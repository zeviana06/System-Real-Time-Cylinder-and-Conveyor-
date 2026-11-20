#pragma once

#include <interface.h>

void initializeHardwarePorts();

void setBitValue(uInt8* variable, int n_bit, int new_value_bit);
int getBitValue(uInt8 value, uInt8 n_bit);

// CylinderStart (P2.1 = Forward, P2.0 = Backward | P0.6 = Back/0, P0.5 = Fwd/1 [active-high]) ....
void cylinderStart_moveForward();
void cylinderStart_moveBackward();
void cylinderStart_stop();
int cylinderStart_getPosition();

// Cylinder 1 (P2.4 = Forward, P2.3 = Backward | P0.4 = Back/0, P0.3 = Fwd/1 [active-low])
void cylinder1_moveForward();
void cylinder1_moveBackward();
void cylinder1_stop();
int cylinder1_getPosition();

// Cylinder 2 (P2.6 = Forward, P2.5 = Backward | P0.2 = Back/0, P0.1 = Fwd/1 [active-low])
void cylinder2_moveForward();
void cylinder2_moveBackward();
void cylinder2_stop();
int cylinder2_getPosition();

// Conveyor (P2.2)
void conveyorMove();
void conveyorStop();

// Identification & Dock Sensors
int getIdentificationSensors();
int isBrickAtDock1();
int isBrickAtDock2();

// Switches (P1.4 = Dock1, P1.3 = Dock2, P1.2 = DockEnd)
int isSwitchDock1Pressed();
int isSwitchDock2Pressed();
int isSwitchDockEndPressed();

// LED (P2.7)
void ledSwitch(int on);