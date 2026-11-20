import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SplitterConveyorThread extends Thread {

    // Hardware
    private final Mechanism mechanism;
    private final CylinderStart clStart;
    private final Cylinder1 cl1;
    private final Cylinder2 cl2;

    // Estado global partilhado (NFR5)
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean productionActive = new AtomicBoolean(false);
    private final AtomicBoolean finishingProduction = new AtomicBoolean(false);
    private static final AtomicBoolean emergencyActive = new AtomicBoolean(false);

    // Sincronização direta do tapete (NFR5)
    private final Object conveyorLock = new Object();
    private int conveyorPauseCount = 0;

    // Disponibilidade das estações (FRH9 e FRH10)
    private final AtomicBoolean dock1OperatorEnabled = new AtomicBoolean(true);
    private final AtomicBoolean dock2OperatorEnabled = new AtomicBoolean(true);
    private final AtomicBoolean maintenanceActive = new AtomicBoolean(false);

    // Filas concorrentes para suporte a múltiplas caixas (FRH3, NFR2, NFR3)
    private final BlockingQueue<Integer> boxRequestQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Integer> dock2PipelineQueue = new LinkedBlockingQueue<>();

    // Semáforos para proteção exclusiva dos atuadores (NFR5)
    private final Semaphore entryMutex = new Semaphore(1);
    private final Semaphore cyl1Mutex = new Semaphore(1);
    private final Semaphore cyl2Mutex = new Semaphore(1);

    // Estatísticas de produção thread-safe (FRH14)
    private final AtomicInteger countType1 = new AtomicInteger(0);
    private final AtomicInteger countType2 = new AtomicInteger(0);
    private final AtomicInteger countType3 = new AtomicInteger(0);
    private final AtomicInteger deliveredDock1 = new AtomicInteger(0);
    private final AtomicInteger deliveredDock2 = new AtomicInteger(0);
    private final AtomicInteger deliveredDockEnd = new AtomicInteger(0);
    private final AtomicInteger redirectedCount = new AtomicInteger(0);

    // Controlo temporal do LED P2.7 (FRH12)
    private volatile long ledNormalUntil = 0;
    private volatile long ledRedirectUntil = 0;
    private volatile boolean boxInEntryOrDock1 = false;

    // Threads auxiliares internas
    private Thread switchMonitorThread;
    private Thread ledControllerThread;
    private Thread dock2SorterThread;

    public SplitterConveyorThread() {
        super("SplitterConveyorThread");
        this.mechanism = new Mechanism();
        this.clStart = new CylinderStart();
        this.cl1 = new Cylinder1();
        this.cl2 = new Cylinder2();
    }

    public static boolean isEmergencyActive() {
        return emergencyActive.get();
    }

    // =========================================================================
    // CICLO PRINCIPAL: INTRODUÇÃO, IDENTIFICAÇÃO E TRIAGEM NA DOCA 1
    // =========================================================================
    @Override
    public void run() {
        startAuxiliaryThreads();

        while (running.get() && !isInterrupted()) {
            try {
                // 1. Aguarda pedido de caixa na fila sem busy-waiting (FRH3, NFR7)
                boxRequestQueue.take();

                while ((emergencyActive.get() || !productionActive.get()) && running.get()) {
                    Thread.sleep(50);
                }
                if (!running.get()) break;

                entryMutex.acquire();
                boxInEntryOrDock1 = true;

                // 2. Empurra a caixa para o tapete e identifica-a em paralelo até P0.0 (FRH5)
                BoxIdentificationThread boxIDT = new BoxIdentificationThread();
                CylinderThread clStartT = new CylinderThread(clStart);

                boxIDT.start();
                clStartT.start();

                // Aguarda que a caixa ative P0.0 (Doca 1) e seja identificada
                boxIDT.join();

                if (emergencyActive.get()) {
                    clStartT.join();
                    entryMutex.release();
                    continue;
                }

                int boxType = boxIDT.getBoxIdentification();
                if (boxType == 1) countType1.incrementAndGet();
                else if (boxType == 2) countType2.incrementAndGet();
                else if (boxType == 3) countType3.incrementAndGet();
                System.out.println("\n[ID] Caixa identificada na Doca 1 (P0.0): Tipo " + boxType);

                // 3. Decisão imediata na Doca 1 (FRH6, FRH8, FRH9)
                if (boxType == 1) {
                    if (!maintenanceActive.get() && dock1OperatorEnabled.get()) {
                        // Pára o tapete diretamente com mechanism.conveyorStop()
                        synchronized (conveyorLock) {
                            conveyorPauseCount++;
                            mechanism.conveyorStop();
                        }
                        System.out.println("[DOCA 1] Tapete parado para alinhamento. A empurrar caixa Tipo 1...");

                        cyl1Mutex.acquire();
                        try {
                            cl1.gotoPosition(1); // Empurra caixa com tapete parado
                            synchronized (conveyorLock) {
                                if (conveyorPauseCount > 0) conveyorPauseCount--;
                                if (conveyorPauseCount == 0 && productionActive.get() && !emergencyActive.get()) {
                                    mechanism.conveyorMove();
                                }
                            }
                            cl1.gotoPosition(0); // Recolhe cilindro
                        } finally {
                            cyl1Mutex.release();
                        }

                        if (!emergencyActive.get()) {
                            deliveredDock1.incrementAndGet();
                            ledNormalUntil = System.currentTimeMillis() + 2000L; // FRH12: 2s
                            System.out.println("[DOCA 1] Caixa Tipo 1 entregue.");
                        }
                    } else {
                        // Doca 1 indisponível -> Redirecionar para Dock End (FRH8)
                        redirectedCount.incrementAndGet();
                        ledRedirectUntil = System.currentTimeMillis() + 4000L; // FRH12: 4s
                        System.out.println("[REDIRECIONAMENTO] Doca 1 indisponivel! Caixa Tipo 1 segue para Dock End.");
                        dock2PipelineQueue.offer(-1);
                    }
                } else {
                    // Tipo 2 ou Tipo 3 seguem para a zona da Doca 2
                    dock2PipelineQueue.offer(boxType);
                }

                // Garante que CylinderStart já recuou antes de aceitar a próxima caixa
                clStartT.join();
                boxInEntryOrDock1 = false;
                entryMutex.release();

            } catch (InterruptedException e) {
                interrupt();
                break;
            }
        }
    }

    // =========================================================================
    // THREADS AUXILIARES (Botões/Manutenção, LED P2.7 e Doca 2/Dock End)
    // =========================================================================
    private void startAuxiliaryThreads() {
        // 1. Monitorização dos botões físicos P1.4, P1.3 e P1.2 (FRH9, FRH10)
        switchMonitorThread = new Thread(() -> {
            boolean prevP14 = false, prevP13 = false, prevP12 = false;
            long maintenanceEndTime = 0;
            boolean savedDock1 = true, savedDock2 = true;

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    boolean currP14 = mechanism.switchDock1Pressed();
                    boolean currP13 = mechanism.switchDock2Pressed();
                    boolean currP12 = mechanism.switchDockEndPressed();

                    if (currP14 && !prevP14) {
                        boolean next = !dock1OperatorEnabled.get();
                        dock1OperatorEnabled.set(next);
                        System.out.println("\n[ESTACAO] Doca 1 definida como: " + (next ? "DISPONIVEL" : "INDISPONIVEL"));
                    }

                    if (currP13 && !prevP13) {
                        boolean next = !dock2OperatorEnabled.get();
                        dock2OperatorEnabled.set(next);
                        System.out.println("\n[ESTACAO] Doca 2 definida como: " + (next ? "DISPONIVEL" : "INDISPONIVEL"));
                    }

                    if (currP12 && !prevP12) {
                        if (!maintenanceActive.get()) {
                            savedDock1 = dock1OperatorEnabled.get();
                            savedDock2 = dock2OperatorEnabled.get();
                            maintenanceActive.set(true);
                            maintenanceEndTime = System.currentTimeMillis() + 10_000L;
                            System.out.println("\n[MANUTENCAO] Ativada por 10s (Docas 1 e 2 indisponiveis).");
                        } else {
                            System.out.println("\n[MANUTENCAO] Ja em curso (pedido ignorado).");
                        }
                    }

                    if (maintenanceActive.get() && System.currentTimeMillis() >= maintenanceEndTime) {
                        maintenanceActive.set(false);
                        dock1OperatorEnabled.set(savedDock1);
                        dock2OperatorEnabled.set(savedDock2);
                        System.out.println("\n[MANUTENCAO] Terminada. Estados anteriores restaurados.");
                    }

                    prevP14 = currP14;
                    prevP13 = currP13;
                    prevP12 = currP12;
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "SwitchMonitorThread");

        // 2. Controlador exclusivo do LED P2.7 com prioridades (FRH12)
        ledControllerThread = new Thread(() -> {
            boolean ledOn = false;
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    long now = System.currentTimeMillis();
                    if (emergencyActive.get()) {
                        ledOn = !ledOn;
                        mechanism.ledSwitch(ledOn ? 1 : 0);
                        Thread.sleep(200);
                    } else if (now < ledRedirectUntil) {
                        ledOn = !ledOn;
                        mechanism.ledSwitch(ledOn ? 1 : 0);
                        Thread.sleep(300);
                    } else if (now < ledNormalUntil) {
                        ledOn = !ledOn;
                        mechanism.ledSwitch(ledOn ? 1 : 0);
                        Thread.sleep(500);
                    } else {
                        if (ledOn) {
                            ledOn = false;
                            mechanism.ledSwitch(0);
                        }
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) {
                    mechanism.ledSwitch(0);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "LedControllerThread");

        // 3. Triagem na Doca 2 (com paragem direta via conveyorStop) e entregas no Dock End (FRH7, FRH8)
        dock2SorterThread = new Thread(() -> {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    int boxType = dock2PipelineQueue.take();

                    // Aguarda que a caixa ative o sensor P1.7 (Doca 2)
                    while (!SplitterConveyor.isBrickAtDock2() && running.get()) {
                        Thread.sleep(5);
                    }
                    if (!running.get()) break;

                    if (boxType == 2) {
                        if (!maintenanceActive.get() && dock2OperatorEnabled.get()) {
                            // Pára o tapete diretamente com mechanism.conveyorStop()
                            synchronized (conveyorLock) {
                                conveyorPauseCount++;
                                mechanism.conveyorStop();
                            }
                            System.out.println("\n[DOCA 2] Tapete parado para alinhamento. A empurrar caixa Tipo 2...");

                            cyl2Mutex.acquire();
                            try {
                                cl2.gotoPosition(1);
                                synchronized (conveyorLock) {
                                    if (conveyorPauseCount > 0) conveyorPauseCount--;
                                    if (conveyorPauseCount == 0 && productionActive.get() && !emergencyActive.get()) {
                                        mechanism.conveyorMove();
                                    }
                                }
                                cl2.gotoPosition(0);
                            } finally {
                                cyl2Mutex.release();
                            }

                            if (!emergencyActive.get()) {
                                deliveredDock2.incrementAndGet();
                                ledNormalUntil = System.currentTimeMillis() + 2000L; // FRH12: 2s
                                System.out.println("[DOCA 2] Caixa Tipo 2 entregue.");
                            }
                        } else {
                            // Doca 2 indisponível -> Redirecionar para Dock End (FRH8)
                            redirectedCount.incrementAndGet();
                            ledRedirectUntil = System.currentTimeMillis() + 4000L; // FRH12: 4s
                            System.out.println("\n[REDIRECIONAMENTO] Doca 2 indisponivel! Caixa Tipo 2 segue para Dock End.");
                            waitAndCountDockEnd(true);
                        }
                    } else {
                        // Tipo 3 (normal) ou Tipo 1 redirecionada (-1)
                        waitAndCountDockEnd(boxType == -1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Dock2SorterThread");

        switchMonitorThread.start();
        ledControllerThread.start();
        dock2SorterThread.start();
    }

    private void waitAndCountDockEnd(boolean isRedirected) throws InterruptedException {
        while (SplitterConveyor.isBrickAtDock2() && running.get()) {
            Thread.sleep(10);
        }
        long remaining = 1200;
        while (remaining > 0 && running.get()) {
            if (!emergencyActive.get() && productionActive.get() && conveyorPauseCount == 0) {
                Thread.sleep(50);
                remaining -= 50;
            } else {
                Thread.sleep(50);
            }
        }
        if (running.get() && !emergencyActive.get()) {
            deliveredDockEnd.incrementAndGet();
            if (!isRedirected) {
                ledNormalUntil = System.currentTimeMillis() + 2000L;
            }
            System.out.println("\n[DOCA END] Caixa entregue no Dock End.");
        }
    }

    // =========================================================================
    // MÉTODOS PÚBLICOS INVOCADOS PELO OPERADOR (App.java)
    // =========================================================================

    public void calibrateAllCylinders(Scanner sc) {
        if (productionActive.get()) {
            System.out.println("[AVISO] Pare a producao ('F' ou 'E') antes de calibrar.");
            return;
        }
        System.out.println("\n=== FRH1: CALIBRACAO SEMI-AUTOMATICA ===");
        calibrateSingle("CylinderStart", clStart, sc);
        calibrateSingle("Cylinder1", cl1, sc);
        calibrateSingle("Cylinder2", cl2, sc);
        System.out.println("=== CALIBRACAO CONCLUIDA ===\n");
    }

    private void calibrateSingle(String name, Cylinder cyl, Scanner sc) {
        if (cyl.getPosition() == 0) {
            System.out.println(name + " ja esta na posicao 0.");
            return;
        }
        System.out.print(name + " fora da posicao 0. Direcao [F = Avancar / B = Recuar]: ");
        while (true) {
            String dir = sc.nextLine().trim().toUpperCase();
            if (dir.equals("F")) {
                cyl.gotoPosition(1);
                cyl.gotoPosition(0);
                break;
            } else if (dir.equals("B")) {
                cyl.gotoPosition(0);
                break;
            } else {
                System.out.print("Opcao invalida [F/B]: ");
            }
        }
        System.out.println(name + " calibrado na posicao 0.");
    }

    public void startProduction() {
        if (emergencyActive.get()) {
            System.out.println("[AVISO] Emergencia ativa! Prima 'R' primeiro.");
            return;
        }
        finishingProduction.set(false);
        productionActive.set(true);
        synchronized (conveyorLock) {
            if (conveyorPauseCount == 0) {
                mechanism.conveyorMove();
            }
        }
        System.out.println("[PRODUCAO] Iniciada.");
    }

    public void getBox() {
        if (emergencyActive.get()) {
            System.out.println("[AVISO] Emergencia ativa! Pedido rejeitado.");
            return;
        }
        if (!productionActive.get() || finishingProduction.get()) {
            System.out.println("[AVISO] Producao parada ou a terminar. Prima 'S' primeiro.");
            return;
        }
        boxRequestQueue.offer(1);
        System.out.println("[FILA] Pedido registado (getBox). Pendentes na fila: " + boxRequestQueue.size());
    }

    public void finishProduction() {
        if (!productionActive.get() || finishingProduction.get()) {
            System.out.println("[INFO] Producao nao esta ativa ou ja esta a terminar.");
            return;
        }
        finishingProduction.set(true);
        System.out.println("[PRODUCAO] A terminar controladamente (a aguardar caixas em transito)...");
        new Thread(() -> {
            try {
                while ((!boxRequestQueue.isEmpty() || !dock2PipelineQueue.isEmpty() || boxInEntryOrDock1)
                        && !emergencyActive.get() && running.get()) {
                    Thread.sleep(100);
                }
                if (!emergencyActive.get()) {
                    productionActive.set(false);
                    finishingProduction.set(false);
                    mechanism.conveyorStop();
                    System.out.println("\n[PRODUCAO] Terminada com sucesso. Tapete parado.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "ControlledFinishThread").start();
    }

    public void emergencyStop() {
        emergencyActive.set(true);
        productionActive.set(false);
        synchronized (conveyorLock) {
            conveyorPauseCount = 0;
            mechanism.conveyorStop();
        }
        clStart.stop();
        cl1.stop();
        cl2.stop();
        System.out.println("\n[!!! EMERGENCIA !!!] Atuadores parados! Prima 'R' para diagnosticar e retomar.");
    }

    public void emergencyResume(Scanner sc) {
        if (!emergencyActive.get()) {
            System.out.println("[INFO] Nenhuma emergencia ativa.");
            return;
        }
        System.out.println("\n=== FRH11: DIAGNOSTICO E RECUPERACAO ===");
        int pS = clStart.getPosition(), p1 = cl1.getPosition(), p2 = cl2.getPosition();
        System.out.println("Posicoes -> Start=" + pS + " | Cyl1=" + p1 + " | Cyl2=" + p2);

        if (pS != 0 || p1 != 0 || p2 != 0) {
            System.out.println("[ALERTA] Cilindros fora de repouso. A recalibrar...");
            emergencyActive.set(false);
            calibrateAllCylinders(sc);
        }

        if (boxInEntryOrDock1 || SplitterConveyor.isBrickAtDock1() || SplitterConveyor.isBrickAtDock2()) {
            System.out.println("[RECUPERACAO] Operacao interrompida descartada com seguranca.");
            dock2PipelineQueue.clear();
            boxInEntryOrDock1 = false;
        }

        synchronized (conveyorLock) {
            conveyorPauseCount = 0;
        }
        emergencyActive.set(false);
        finishingProduction.set(false);
        System.out.println("[SISTEMA] Recuperado! Prima 'S' para reiniciar a producao.");
    }

    public void displayOperationalStatus() {
        boolean d1 = !maintenanceActive.get() && dock1OperatorEnabled.get();
        boolean d2 = !maintenanceActive.get() && dock2OperatorEnabled.get();
        System.out.println("\n--- ESTADO OPERACIONAL (FRH13) ---");
        System.out.println("Producao Ativa       : " + (productionActive.get() ? "SIM" : "NAO"));
        System.out.println("Emergencia / Alarme  : " + (emergencyActive.get() ? "ATIVA" : "NORMAL"));
        System.out.println("Modo Manutencao      : " + (maintenanceActive.get() ? "ATIVO (10s)" : "INATIVO"));
        System.out.println("Cilindros            : Start=" + clStart.getPosition() + " | Cyl1=" + cl1.getPosition() + " | Cyl2=" + cl2.getPosition());
        System.out.println("Disponibilidade Docas: Dock 1=" + (d1 ? "ON" : "OFF") + " | Dock 2=" + (d2 ? "ON" : "OFF"));
        System.out.println("Pedidos Pendentes    : " + boxRequestQueue.size() + " | Caixas rumo Doca 2: " + dock2PipelineQueue.size());
        System.out.println("----------------------------------");
    }

    public void displayStatistics() {
        System.out.println("\n--- ESTATISTICAS DE PRODUCAO (FRH14) ---");
        System.out.println("Identificadas -> Tipo 1: " + countType1.get() + " | Tipo 2: " + countType2.get() + " | Tipo 3: " + countType3.get());
        System.out.println("Entregues     -> Dock 1: " + deliveredDock1.get() + " | Dock 2: " + deliveredDock2.get() + " | Dock End: " + deliveredDockEnd.get());
        System.out.println("Redirecionadas: " + redirectedCount.get());
        System.out.println("----------------------------------------");
    }

    public void shutdown() throws InterruptedException {
        running.set(false);
        productionActive.set(false);
        mechanism.conveyorStop();
        clStart.stop();
        cl1.stop();
        cl2.stop();
        mechanism.ledSwitch(0);

        if (switchMonitorThread != null) { switchMonitorThread.interrupt(); switchMonitorThread.join(500); }
        if (ledControllerThread != null) { ledControllerThread.interrupt(); ledControllerThread.join(500); }
        if (dock2SorterThread != null) { dock2SorterThread.interrupt(); dock2SorterThread.join(500); }
        this.interrupt();
    }
}