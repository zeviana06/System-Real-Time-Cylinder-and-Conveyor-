// SplitterConveyor.cpp : This file contains the 'main' function. Program execution begins and ends there.
//
#include <iostream>
#include <stdio.h>
#include <conio.h>

extern "C" {
    // observe your project contents. We are mixing C files with cpp ones.
    // Therefore, inside cpp files, we need to tell which functions are written in C.
    // That is why we use extern "C" directive
#include <interface.h>
#include <SplitterConveyor.h>
}

int main()
{
    printf("Welcome to the Splitter Conveyor Application\n");
    initializeHardwarePorts();

    int t = -1;
    printf("\n\nPress: q,a,z for Cylinder 1 or 0 to exit");
    while (t != '0') {
        t = _getch();
        switch (t) {
        case 'q':
            cylinder1_moveForward();
            break;
        case 'a':
            cylinder1_moveBackward();
            break;
        case 'z':
            cylinder1_stop();
            break;
        default:
            break;
        }
    }
    writeDigitalU8(2, 0x00);
    closeChannels();
    return 0;
}
