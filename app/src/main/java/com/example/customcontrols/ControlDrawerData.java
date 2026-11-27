package com.example.customcontrols;

import java.util.ArrayList;

/**
 * Data model for a collapsible ControlDrawer containing ControlSubButtons.
 */
public class ControlDrawerData {

    public static final int ORIENTATION_DOWN = 0;
    public static final int ORIENTATION_LEFT = 1;
    public static final int ORIENTATION_UP = 2;
    public static final int ORIENTATION_RIGHT = 3;
    public static final int ORIENTATION_FREE = 4;

    public ControlData properties = new ControlData();
    public ArrayList<ControlData> buttonProperties = new ArrayList<>();
    public int orientation = ORIENTATION_DOWN;
    public boolean open = false;

    public ControlDrawerData() {
        properties.name = "Drawer";
    }

    public ControlDrawerData(ControlData properties, int orientation) {
        this.properties = properties != null ? properties : new ControlData();
        this.orientation = orientation;
    }

    public ControlDrawerData(ControlDrawerData other) {
        if (other == null) return;
        this.properties = other.properties != null ? new ControlData(other.properties) : new ControlData();
        this.orientation = other.orientation;
        this.open = other.open;
        this.buttonProperties = new ArrayList<>();
        if (other.buttonProperties != null) {
            for (ControlData sub : other.buttonProperties) {
                this.buttonProperties.add(new ControlData(sub));
            }
        }
    }

    public void ensureValid() {
        if (properties == null) properties = new ControlData();
        properties.ensureValid();
        if (buttonProperties == null) buttonProperties = new ArrayList<>();
        for (ControlData sub : buttonProperties) {
            if (sub != null) sub.ensureValid();
        }
    }
}
