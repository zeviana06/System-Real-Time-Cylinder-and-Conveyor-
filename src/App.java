import java.util.Scanner;

public class App {

    public static void main(String[] args) throws InterruptedException {
        SplitterConveyor.initializeHardwarePorts();

        Scanner sc = new Scanner(System.in);
        SplitterConveyorThread supervisor = new SplitterConveyorThread();

        // 1. Calibração semi-automática inicial obrigatória (FRH1)
        supervisor.calibrateAllCylinders(sc);

        // 2. Arranca a thread principal de supervisão (e as suas sub-threads)
        supervisor.start();

        // 3. Ciclo de comandos do operador em tempo real (FRH2, FRH3, FRH11, FRH13, FRH14)
        boolean running = true;
        while (running) {
            printMenu();
            if (!sc.hasNextLine()) break;
            String input = sc.nextLine().trim().toUpperCase();
            if (input.isEmpty()) continue;

            switch (input.charAt(0)) {
                case 'S':
                    supervisor.startProduction();
                    break;
                case 'P':
                    supervisor.getBox();
                    break;
                case 'F':
                    supervisor.finishProduction();
                    break;
                case 'E':
                    supervisor.emergencyStop();
                    break;
                case 'R':
                    supervisor.emergencyResume(sc);
                    break;
                case 'M':
                    supervisor.displayOperationalStatus();
                    break;
                case 'T':
                    supervisor.displayStatistics();
                    break;
                case 'C':
                    supervisor.calibrateAllCylinders(sc);
                    break;
                case 'Q':
                    running = false;
                    break;
                default:
                    System.out.println("[AVISO] Comando invalido.");
                    break;
            }
        }

        // 4. Terminação limpa e segura (NFR7)
        supervisor.shutdown();
        supervisor.join();
        sc.close();
        System.out.println("[SISTEMA] Aplicacao encerrada com seguranca.");
    }

    private static void printMenu() {
        System.out.println("\n[S] Iniciar | [P] Pedir Caixa | [F] Terminar | [E] Emergencia | [R] Resume | [M] Estado | [T] Estatisticas | [C] Calibrar | [Q] Sair");
        System.out.print("Comando > ");
    }
}