public class SplitterConveyor {

    static {
        System.load("c:\\str\\labwork2\\SplitterConveyor\\x64\\Debug\\SplitterConveyor.dll");
    }

    static synchronized native public void initializeHardwarePorts();

    // CylinderStart
    static synchronized native public void cylinderStartMoveForward();
    static synchronized native public void cylinderStartMoveBackward();
    static synchronized native public void cylinderStartStop();
    static synchronized native public int cylinderStartGetPosition();

    // Cylinder 1
    static synchronized native public void cylinder1MoveForward();
    static synchronized native public void cylinder1MoveBackward();
    static synchronized native public void cylinder1Stop();
    static synchronized native public int cylinder1GetPosition();

    // Cylinder 2
    static synchronized native public void cylinder2MoveForward();
    static synchronized native public void cylinder2MoveBackward();
    static synchronized native public void cylinder2Stop();
    static synchronized native public int cylinder2GetPosition();

    // Conveyor
    static synchronized native public void conveyorMove();
    static synchronized native public void conveyorStop();

    // Sensors
    static synchronized native public int getIdentificationSensors();
    static synchronized native public boolean isBrickAtDock1();
    static synchronized native public boolean isBrickAtDock2();

    // Switches
    static synchronized native public boolean isSwitchDock1Pressed();
    static synchronized native public boolean isSwitchDock2Pressed();
    static synchronized native public boolean isSwitchDockEndPressed();

    // LED
    static synchronized native public void ledSwitch(int on);
}