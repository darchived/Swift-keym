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
    public boolean macroSequential = false;
    public String imageBase64 = "";

    // v1.0.0-Released Advanced Control Features
    public int[] secondaryKeycodes = new int[]{
            LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
            LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
            LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
            LwjglGlfwKeycode.GLFW_KEY_UNKNOWN
    };
    public String secondaryName = "";
    public boolean isLayerShiftKey = false;
    public boolean dualActionEnabled = false;
    public boolean isRadialWheel = false;
    public int[] radialKeycodes = new int[]{
            LwjglGlfwKeycode.GLFW_KEY_1,
            LwjglGlfwKeycode.GLFW_KEY_2,
            LwjglGlfwKeycode.GLFW_KEY_3,
            LwjglGlfwKeycode.GLFW_KEY_4,
            LwjglGlfwKeycode.GLFW_KEY_5,
            LwjglGlfwKeycode.GLFW_KEY_6,
            LwjglGlfwKeycode.GLFW_KEY_G,
            LwjglGlfwKeycode.GLFW_KEY_Q
    };
    public String[] radialLabels = new String[]{
            "1", "2", "3", "4", "5", "6", "G", "Q"
    };
    public boolean isScrollStrip = false;
    public boolean isFreeLookButton = false;
    public int quickTurnDegrees = 0; // 0 = off, 90, 180
    public String macroId = "";

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
        this.macroSequential = other.macroSequential;
        this.imageBase64 = other.imageBase64 != null ? other.imageBase64 : "";
        this.secondaryKeycodes = other.secondaryKeycodes != null ? Arrays.copyOf(other.secondaryKeycodes, 4) : new int[4];
        this.secondaryName = other.secondaryName != null ? other.secondaryName : "";
        this.isLayerShiftKey = other.isLayerShiftKey;
        this.dualActionEnabled = other.dualActionEnabled;
        this.isRadialWheel = other.isRadialWheel;
        this.radialKeycodes = other.radialKeycodes != null ? Arrays.copyOf(other.radialKeycodes, 8) : new int[8];
        this.radialLabels = other.radialLabels != null ? Arrays.copyOf(other.radialLabels, 8) : new String[]{"1", "2", "3", "4", "5", "6", "G", "Q"};
        this.isScrollStrip = other.isScrollStrip;
        this.isFreeLookButton = other.isFreeLookButton;
        this.quickTurnDegrees = other.quickTurnDegrees;
        this.macroId = other.macroId != null ? other.macroId : "";
    }

    public void ensureValid() {
        if (name == null) name = "";
        if (iconPath == null) iconPath = "";
        if (imageBase64 == null) imageBase64 = "";
        if (secondaryName == null) secondaryName = "";
        if (macroId == null) macroId = "";
        if (keycodes == null || keycodes.length < 4) {
            int[] fixed = new int[4];
            if (keycodes != null) {
                System.arraycopy(keycodes, 0, fixed, 0, Math.min(4, keycodes.length));
            }
            keycodes = fixed;
        }
        if (secondaryKeycodes == null || secondaryKeycodes.length < 4) {
            int[] fixedSec = new int[4];
            if (secondaryKeycodes != null) {
                System.arraycopy(secondaryKeycodes, 0, fixedSec, 0, Math.min(4, secondaryKeycodes.length));
            }
            secondaryKeycodes = fixedSec;
        }
        if (radialKeycodes == null || radialKeycodes.length < 8) {
            int[] fixedRad = new int[]{
                    LwjglGlfwKeycode.GLFW_KEY_1,
                    LwjglGlfwKeycode.GLFW_KEY_2,
                    LwjglGlfwKeycode.GLFW_KEY_3,
                    LwjglGlfwKeycode.GLFW_KEY_4,
                    LwjglGlfwKeycode.GLFW_KEY_5,
                    LwjglGlfwKeycode.GLFW_KEY_6,
                    LwjglGlfwKeycode.GLFW_KEY_G,
                    LwjglGlfwKeycode.GLFW_KEY_Q
            };
            if (radialKeycodes != null) {
                System.arraycopy(radialKeycodes, 0, fixedRad, 0, Math.min(8, radialKeycodes.length));
            }
            radialKeycodes = fixedRad;
        }
        if (radialLabels == null || radialLabels.length < 8) {
            String[] fixedLbl = new String[]{"1", "2", "3", "4", "5", "6", "G", "Q"};
            if (radialLabels != null) {
                System.arraycopy(radialLabels, 0, fixedLbl, 0, Math.min(8, radialLabels.length));
            }
            radialLabels = fixedLbl;
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
