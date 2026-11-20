#include <stdio.h>
#include <conio.h>
#include <interface.h>
#include <SplitterConveyor.h>

void initializeHardwarePorts() {
    createDigitalInput(0);
    createDigitalInput(1);
    createDigitalOutput(2);
    writeDigitalU8(2, 0x00);
}

void setBitValue(uInt8* variable, int n_bit, int new_value_bit) {
    uInt8 mask_on = (uInt8)(1 << n_bit);
    uInt8 mask_off = ~mask_on;
    if (new_value_bit) *variable |= mask_on;
    else *variable &= mask_off;
}

int getBitValue(uInt8 value, uInt8 n_bit) {
    return (value & (1 << n_bit));
}

// =========================================================
// CYLINDER START (P2.1 = Forward, P2.0 = Backward)
// Sensores P0.6 (pos 0) e P0.5 (pos 1) -> ACTIVE-HIGH
// =========================================================
void cylinderStart_moveForward() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 1, 1);
    setBitValue(&p, 0, 0);
    writeDigitalU8(2, p);
}

void cylinderStart_moveBackward() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 1, 0);
    setBitValue(&p, 0, 1);
    writeDigitalU8(2, p);
}

void cylinderStart_stop() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 1, 0);
    setBitValue(&p, 0, 0);
    writeDigitalU8(2, p);
}

int cylinderStart_getPosition() {
    uInt8 v = readDigitalU8(0);
    if (getBitValue(v, 6)) return 0;
    else if (getBitValue(v, 5)) return 1;
    return -1;
}

// =========================================================
// CYLINDER 1 (P2.4 = Forward, P2.3 = Backward)
// Sensores P0.4 (pos 0) e P0.3 (pos 1) -> ACTIVE-LOW
// =========================================================
void cylinder1_moveForward() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 4, 1);
    setBitValue(&p, 3, 0);
    writeDigitalU8(2, p);
}

void cylinder1_moveBackward() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 4, 0);
    setBitValue(&p, 3, 1);
    writeDigitalU8(2, p);
}

void cylinder1_stop() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 4, 0);
    setBitValue(&p, 3, 0);
    writeDigitalU8(2, p);
}

int cylinder1_getPosition() {
    uInt8 v = readDigitalU8(0);
    if (!getBitValue(v, 4)) return 0;
    else if (!getBitValue(v, 3)) return 1;
    return -1;
}

// =========================================================
// CYLINDER 2 (P2.6 = Forward, P2.5 = Backward)
// Sensores P0.2 (pos 0) e P0.1 (pos 1) -> ACTIVE-LOW
// =========================================================
void cylinder2_moveForward() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 6, 1);
    setBitValue(&p, 5, 0);
    writeDigitalU8(2, p);
}

void cylinder2_moveBackward() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 6, 0);
    setBitValue(&p, 5, 1);
    writeDigitalU8(2, p);
}

void cylinder2_stop() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 6, 0);
    setBitValue(&p, 5, 0);
    writeDigitalU8(2, p);
}

int cylinder2_getPosition() {
    uInt8 v = readDigitalU8(0);
    if (!getBitValue(v, 2)) return 0;
    else if (!getBitValue(v, 1)) return 1;
    return -1;
}

// =========================================================
// CONVEYOR (P2.2)
// =========================================================
void conveyorMove() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 2, 1);
    writeDigitalU8(2, p);
}

void conveyorStop() {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 2, 0);
    writeDigitalU8(2, p);
}

// =========================================================
// SENSORES DE IDENTIFICAÇÃO E PRESENÇA NAS DOCAS
// =========================================================
int getIdentificationSensors() {
    uInt8 v = readDigitalU8(1);
    if (getBitValue(v, 5)) return 1;
    if (getBitValue(v, 6)) return 2;
    return 0;
}

int isBrickAtDock1() {
    uInt8 v = readDigitalU8(0);
    return getBitValue(v, 0) ? 1 : 0; // P0.0 active-high
}

int isBrickAtDock2() {
    uInt8 v = readDigitalU8(1);
    return getBitValue(v, 7) ? 1 : 0; // P1.7 active-high
}

// =========================================================
// BOTÕES / SWITCHES (P1.4, P1.3, P1.2)
// =========================================================
int isSwitchDock1Pressed() {
    uInt8 v = readDigitalU8(1);
    return getBitValue(v, 4) ? 1 : 0;
}

int isSwitchDock2Pressed() {
    uInt8 v = readDigitalU8(1);
    return getBitValue(v, 3) ? 1 : 0;
}

int isSwitchDockEndPressed() {
    uInt8 v = readDigitalU8(1);
    return getBitValue(v, 2) ? 1 : 0;
}

// =========================================================
// LED (P2.7)
// =========================================================
void ledSwitch(int on) {
    uInt8 p = readDigitalU8(2);
    setBitValue(&p, 7, on ? 1 : 0);
    writeDigitalU8(2, p);
}