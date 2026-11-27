package com.example.customcontrols;

import android.view.View;

/**
 * Common interface implemented by ControlButton, ControlDrawer, ControlSubButton, and ControlJoystick.
 */
public interface ControlInterface {
    View getControlView();

    ControlData getProperties();

    void updateProperties();

    void regenerateDynamicCoordinates();

    void sendKeyPresses(boolean isDown);

    void cloneButton();

    void removeButton();

    boolean canSnap(ControlInterface target);
}
