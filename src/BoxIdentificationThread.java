public class BoxIdentificationThread extends Thread {
    private int brickType = -1;

    public int getBoxIdentification() {
        return brickType;
    }

    public void startBoxIdentification() {
        boolean dot1 = false;
        boolean dot2 = false;

        while (!SplitterConveyor.isBrickAtDock1() && !Thread.currentThread().isInterrupted()) {
            int sensor = SplitterConveyor.getIdentificationSensors();
            if (sensor == 1) {
                dot1 = true;
            } else if (sensor == 2) {
                dot2 = true;
            }try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
    }
        }

        if (dot1 && dot2) {
            brickType = 3;
        } else if (dot1 || dot2) {
            brickType = 2;
        } else {
            brickType = 1;
        }
    }

    @Override
    public void run() {
        this.startBoxIdentification();
    }
}