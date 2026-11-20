public class CylinderThread extends Thread {
    private Cylinder cylinder;

    public CylinderThread(Cylinder cylinder) {
        this.cylinder = cylinder;
    }

    public void initializeCylinderThread() throws InterruptedException {
        // goto(1) - estende o cilindro
        this.cylinder.gotoPosition(1);
        Thread.sleep(500);
        // goto(0) - recolhe o cilindro de volta à posição inicial
        this.cylinder.gotoPosition(0);
    }

    @Override
    public void run() {
        try {
            this.initializeCylinderThread();
        } catch (InterruptedException e) {
            this.cylinder.stop();
            Thread.currentThread().interrupt();
        }
    }
}