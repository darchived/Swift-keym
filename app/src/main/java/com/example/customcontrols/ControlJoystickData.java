package com.example.customcontrols;

/**
 * Data model for Swift KeyM virtual analog WASD joystick.
 */
public class ControlJoystickData extends ControlData {

    public boolean forwardLock = true;
    public boolean absolute = false;

    public ControlJoystickData() {
        super();
        this.name = "WASD";
        this.width = 136f;
        this.height = 136f;
        this.cornerRadius = 100f;
        this.bgColor = 0x4D2C2C2C;
        this.strokeColor = 0xCCFFFFFF;
        this.strokeWidth = 2;
        this.dynamicX = "0.06 * ${screen_width}";
        this.dynamicY = "0.48 * ${screen_height}";
    }

    public ControlJoystickData(ControlJoystickData other) {
        super(other);
        if (other != null) {
            this.forwardLock = other.forwardLock;
            this.absolute = other.absolute;
        }
    }
}
