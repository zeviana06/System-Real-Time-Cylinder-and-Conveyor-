public class Mechanism {

    public void conveyorMove() {
        SplitterConveyor.conveyorMove();
    }

    public void conveyorStop() {
        SplitterConveyor.conveyorStop();
    }

    public boolean switchDock1Pressed() {
        return SplitterConveyor.isSwitchDock1Pressed();
    }

    public boolean switchDock2Pressed() {
        return SplitterConveyor.isSwitchDock2Pressed();
    }

    public boolean switchDockEndPressed() {
        return SplitterConveyor.isSwitchDockEndPressed();
    }

    public void ledSwitch(int on) {
        SplitterConveyor.ledSwitch(on);
    }
}