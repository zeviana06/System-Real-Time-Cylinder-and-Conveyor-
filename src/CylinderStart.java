public class CylinderStart implements Cylinder {

    @Override
    public void moveForward() {
        SplitterConveyor.cylinderStartMoveForward();
    }

    @Override
    public void moveBackward() {
        SplitterConveyor.cylinderStartMoveBackward();
    }

    @Override
    public void stop() {
        SplitterConveyor.cylinderStartStop();
    }

    @Override
    public int getPosition() {
        return SplitterConveyor.cylinderStartGetPosition();
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
        return SplitterConveyor.getIdentificationSensors() != 0;
    }
}