package com.example.customcontrols;

import android.annotation.SuppressLint;
import android.content.Context;

/**
 * Sub-button hosted inside a ControlDrawer.
 */
@SuppressLint("ViewConstructor")
public class ControlSubButton extends ControlButton {

    private final ControlDrawer mParentDrawer;

    public ControlSubButton(Context context, ControlLayout parentLayout, ControlDrawer parentDrawer, ControlData properties) {
        super(context, parentLayout, properties);
        this.mParentDrawer = parentDrawer;
    }

    public ControlDrawer getParentDrawer() {
        return mParentDrawer;
    }

    @Override
    public void regenerateDynamicCoordinates() {
        if (mParentDrawer != null && mParentDrawer.getDrawerData().orientation != ControlDrawerData.ORIENTATION_FREE) {
            return;
        }
        super.regenerateDynamicCoordinates();
    }

    @Override
    public void cloneButton() {
        if (mParentDrawer != null) {
            ControlData cloned = mProperties.cloneData();
            cloned.name = cloned.name + " Copy";
            mParentDrawer.addSubButton(cloned);
            mParentLayout.notifyLayoutModified();
        }
    }

    @Override
    public void removeButton() {
        if (mParentDrawer != null) {
            mParentDrawer.removeSubButton(this);
            mParentLayout.notifyLayoutModified();
        } else {
            super.removeButton();
        }
    }
}
