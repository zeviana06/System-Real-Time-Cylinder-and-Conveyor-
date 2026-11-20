public class Cylinder2 implements Cylinder {

    @Override
    public void moveForward() {
        SplitterConveyor.cylinder2MoveForward();
    }

    @Override
    public void moveBackward() {
        SplitterConveyor.cylinder2MoveBackward();
    }

    @Override
    public void stop() {
        SplitterConveyor.cylinder2Stop();
    }

    @Override
    public int getPosition() {
        return SplitterConveyor.cylinder2GetPosition();
    }

    @Override
    public void gotoPosition(int position) {
        if (position == 1) {
            moveForward();
        } else if (position == 0) {
            moveBackward();
        } else {
            return;
        }

        while (getPosition() != position) {
            if (SplitterConveyorThread.isEmergencyActive()) {
                stop();
                return;
        }try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            stop();
            Thread.currentThread().interrupt();
            return;
        }
    }
    stop();
    }

    @Override
    public boolean boxDetected() {
        return SplitterConveyor.isBrickAtDock2();
    }
}