package com.example.customcontrols;

import android.content.Context;

import com.example.customcontrols.utils.ControlUtils;
import com.example.input.LwjglGlfwKeycode;

import java.util.Arrays;
import java.util.Locale;

/**
 * Data model for an individual Swift KeyM custom control button.
 * Compatible with MojoLauncher / PojavLauncher .json controlmap schemas.
 */
public class ControlData {

    public String name = "New";
    public String iconPath = "";
    public int[] keycodes = new int[]{
            LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
            LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
            LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
            LwjglGlfwKeycode.GLFW_KEY_UNKNOWN
    };
    public String dynamicX = "0.5 * ${screen_width}";
    public String dynamicY = "0.5 * ${screen_height}";
    public float width = 64f;
    public float height = 56f;
    public int bgColor = 0x802C2C2C;
    public int strokeColor = 0xCCFFFFFF;
    public int strokeWidth = 1;
    public float cornerRadius = 18f;
    public float opacity = 0.90f;
    public boolean isToggle = false;
    public boolean passThruEnabled = false;
    public boolean isSwipeable = false;
    public boolean isHideable = true;
    public boolean displayInGame = true;
    public boolean displayInMenu = true;
    public boolean isDynamicBtn = false;

    public ControlData() {
    }

    public ControlData(String name, int[] keycodes, String dynamicX, String dynamicY, float width, float height) {
        this.name = name;
        if (keycodes != null) {
            this.keycodes = new int[4];
            for (int i = 0; i < Math.min(4, keycodes.length); i++) {
                this.keycodes[i] = keycodes[i];
            }
        }
        this.dynamicX = dynamicX;
        this.dynamicY = dynamicY;
        this.width = width;
        this.height = height;
    }

    public ControlData(ControlData other) {
        if (other == null) return;
        this.name = other.name;
        this.iconPath = other.iconPath != null ? other.iconPath : "";
        this.keycodes = other.keycodes != null ? Arrays.copyOf(other.keycodes, 4) : new int[4];
        this.dynamicX = other.dynamicX;
        this.dynamicY = other.dynamicY;
        this.width = other.width;
        this.height = other.height;
        this.bgColor = other.bgColor;
        this.strokeColor = other.strokeColor;
        this.strokeWidth = other.strokeWidth;
        this.cornerRadius = other.cornerRadius;
        this.opacity = other.opacity;
        this.isToggle = other.isToggle;
        this.passThruEnabled = other.passThruEnabled;
        this.isSwipeable = other.isSwipeable;
        this.isHideable = other.isHideable;
        this.displayInGame = other.displayInGame;
        this.displayInMenu = other.displayInMenu;
        this.isDynamicBtn = other.isDynamicBtn;
    }

    public void ensureValid() {
        if (name == null) name = "";
        if (iconPath == null) iconPath = "";
        if (keycodes == null || keycodes.length < 4) {
            int[] fixed = new int[4];
            if (keycodes != null) {
                System.arraycopy(keycodes, 0, fixed, 0, Math.min(4, keycodes.length));
            }
            keycodes = fixed;
        }
        if (dynamicX == null || dynamicX.isEmpty()) dynamicX = "0.5 * ${screen_width}";
        if (dynamicY == null || dynamicY.isEmpty()) dynamicY = "0.5 * ${screen_height}";
        if (width < 16f) width = 56f;
        if (height < 16f) height = 56f;
        if (opacity <= 0f) opacity = 0.90f;
    }

    public float insertDynamicPos(Context context, String expr, float parentWidth, float parentHeight) {
        float wPx = getWidthPx(context);
        float hPx = getHeightPx(context);
        float marginPx = ControlUtils.dpToPx(context, 2f);
        return ControlUtils.evaluateDynamicExpression(expr, parentWidth, parentHeight, wPx, hPx, marginPx);
    }

    public float calculateDynamicX(Context context, float parentWidth, float parentHeight) {
        float raw = insertDynamicPos(context, dynamicX, parentWidth, parentHeight);
        float max = Math.max(0f, parentWidth - getWidthPx(context));
        return ControlUtils.clamp(raw, 0f, max);
    }

    public float calculateDynamicY(Context context, float parentWidth, float parentHeight) {
        float raw = insertDynamicPos(context, dynamicY, parentWidth, parentHeight);
        float max = Math.max(0f, parentHeight - getHeightPx(context));
        return ControlUtils.clamp(raw, 0f, max);
    }

    public void setStaticPosition(float xPx, float yPx, float parentWidth, float parentHeight) {
        if (parentWidth > 0f) {
            float ratioX = ControlUtils.clamp(xPx / parentWidth, 0f, 1f);
            this.dynamicX = String.format(Locale.US, "%.4f * ${screen_width}", ratioX);
        }
        if (parentHeight > 0f) {
            float ratioY = ControlUtils.clamp(yPx / parentHeight, 0f, 1f);
            this.dynamicY = String.format(Locale.US, "%.4f * ${screen_height}", ratioY);
        }
    }

    public float getWidthPx(Context context) {
        return ControlUtils.dpToPx(context, width);
    }

    public float getHeightPx(Context context) {
        return ControlUtils.dpToPx(context, height);
    }

    public ControlData cloneData() {
        return new ControlData(this);
    }
}
