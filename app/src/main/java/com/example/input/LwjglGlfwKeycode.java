package com.example.input;

import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained LWJGL / GLFW keycode definitions and Android KeyEvent mapper
 * replacing MojoLauncher's native GLFW dependency.
 */
public final class LwjglGlfwKeycode {

    // MojoLauncher / PojavLauncher special control codes
    public static final int SPECIALBTN_KEYBOARD = -1;
    public static final int SPECIALBTN_TOGGLECTRL = -2;
    public static final int SPECIALBTN_MOUSEPRI = -3;
    public static final int SPECIALBTN_MOUSEMID = -4;
    public static final int SPECIALBTN_MOUSESEC = -5;
    public static final int SPECIALBTN_SCROLLUP = -6;
    public static final int SPECIALBTN_SCROLLDOWN = -7;
    public static final int SPECIALBTN_MENU = -8;

    // GLFW Keycodes
    public static final int GLFW_KEY_UNKNOWN = 0;
    public static final int GLFW_KEY_SPACE = 32;
    public static final int GLFW_KEY_APOSTROPHE = 39;
    public static final int GLFW_KEY_COMMA = 44;
    public static final int GLFW_KEY_MINUS = 45;
    public static final int GLFW_KEY_PERIOD = 46;
    public static final int GLFW_KEY_SLASH = 47;
    public static final int GLFW_KEY_0 = 48;
    public static final int GLFW_KEY_1 = 49;
    public static final int GLFW_KEY_2 = 50;
    public static final int GLFW_KEY_3 = 51;
    public static final int GLFW_KEY_4 = 52;
    public static final int GLFW_KEY_5 = 53;
    public static final int GLFW_KEY_6 = 54;
    public static final int GLFW_KEY_7 = 55;
    public static final int GLFW_KEY_8 = 56;
    public static final int GLFW_KEY_9 = 57;
    public static final int GLFW_KEY_SEMICOLON = 59;
    public static final int GLFW_KEY_EQUAL = 61;
    public static final int GLFW_KEY_A = 65;
    public static final int GLFW_KEY_B = 66;
    public static final int GLFW_KEY_C = 67;
    public static final int GLFW_KEY_D = 68;
    public static final int GLFW_KEY_E = 69;
    public static final int GLFW_KEY_F = 70;
    public static final int GLFW_KEY_G = 71;
    public static final int GLFW_KEY_H = 72;
    public static final int GLFW_KEY_I = 73;
    public static final int GLFW_KEY_J = 74;
    public static final int GLFW_KEY_K = 75;
    public static final int GLFW_KEY_L = 76;
    public static final int GLFW_KEY_M = 77;
    public static final int GLFW_KEY_N = 78;
    public static final int GLFW_KEY_O = 79;
    public static final int GLFW_KEY_P = 80;
    public static final int GLFW_KEY_Q = 81;
    public static final int GLFW_KEY_R = 82;
    public static final int GLFW_KEY_S = 83;
    public static final int GLFW_KEY_T = 84;
    public static final int GLFW_KEY_U = 85;
    public static final int GLFW_KEY_V = 86;
    public static final int GLFW_KEY_W = 87;
    public static final int GLFW_KEY_X = 88;
    public static final int GLFW_KEY_Y = 89;
    public static final int GLFW_KEY_Z = 90;
    public static final int GLFW_KEY_LEFT_BRACKET = 91;
    public static final int GLFW_KEY_BACKSLASH = 92;
    public static final int GLFW_KEY_RIGHT_BRACKET = 93;
    public static final int GLFW_KEY_GRAVE_ACCENT = 96;

    public static final int GLFW_KEY_ESCAPE = 256;
    public static final int GLFW_KEY_ENTER = 257;
    public static final int GLFW_KEY_TAB = 258;
    public static final int GLFW_KEY_BACKSPACE = 259;
    public static final int GLFW_KEY_INSERT = 260;
    public static final int GLFW_KEY_DELETE = 261;
    public static final int GLFW_KEY_RIGHT = 262;
    public static final int GLFW_KEY_LEFT = 263;
    public static final int GLFW_KEY_DOWN = 264;
    public static final int GLFW_KEY_UP = 265;
    public static final int GLFW_KEY_PAGE_UP = 266;
    public static final int GLFW_KEY_PAGE_DOWN = 267;
    public static final int GLFW_KEY_HOME = 268;
    public static final int GLFW_KEY_END = 269;
    public static final int GLFW_KEY_CAPS_LOCK = 280;
    public static final int GLFW_KEY_SCROLL_LOCK = 281;
    public static final int GLFW_KEY_NUM_LOCK = 282;
    public static final int GLFW_KEY_PRINT_SCREEN = 283;
    public static final int GLFW_KEY_PAUSE = 284;
    public static final int GLFW_KEY_F1 = 290;
    public static final int GLFW_KEY_F2 = 291;
    public static final int GLFW_KEY_F3 = 292;
    public static final int GLFW_KEY_F4 = 293;
    public static final int GLFW_KEY_F5 = 294;
    public static final int GLFW_KEY_F6 = 295;
    public static final int GLFW_KEY_F7 = 296;
    public static final int GLFW_KEY_F8 = 297;
    public static final int GLFW_KEY_F9 = 298;
    public static final int GLFW_KEY_F10 = 299;
    public static final int GLFW_KEY_F11 = 300;
    public static final int GLFW_KEY_F12 = 301;
    public static final int GLFW_KEY_KP_0 = 320;
    public static final int GLFW_KEY_KP_1 = 321;
    public static final int GLFW_KEY_KP_2 = 322;
    public static final int GLFW_KEY_KP_3 = 323;
    public static final int GLFW_KEY_KP_4 = 324;
    public static final int GLFW_KEY_KP_5 = 325;
    public static final int GLFW_KEY_KP_6 = 326;
    public static final int GLFW_KEY_KP_7 = 327;
    public static final int GLFW_KEY_KP_8 = 328;
    public static final int GLFW_KEY_KP_9 = 329;
    public static final int GLFW_KEY_KP_ENTER = 335;
    public static final int GLFW_KEY_LEFT_SHIFT = 340;
    public static final int GLFW_KEY_LEFT_CONTROL = 341;
    public static final int GLFW_KEY_LEFT_ALT = 342;
    public static final int GLFW_KEY_LEFT_SUPER = 343;
    public static final int GLFW_KEY_RIGHT_SHIFT = 344;
    public static final int GLFW_KEY_RIGHT_CONTROL = 345;
    public static final int GLFW_KEY_RIGHT_ALT = 346;
    public static final int GLFW_KEY_RIGHT_SUPER = 347;

    public static final class KeyEntry {
        public final int code;
        public final String label;

        public KeyEntry(int code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final SparseIntArray GLFW_TO_ANDROID = new SparseIntArray();
    private static final SparseArray<String> KEY_NAMES = new SparseArray<>();
    private static final List<KeyEntry> KEY_ENTRIES = new ArrayList<>();

    static {
        add(GLFW_KEY_UNKNOWN, 0, "None");
        add(SPECIALBTN_MOUSEPRI, -1, "Mouse Left (MB1)");
        add(SPECIALBTN_MOUSESEC, -1, "Mouse Right (MB2)");
        add(SPECIALBTN_MOUSEMID, -1, "Mouse Middle (MB3)");
        add(SPECIALBTN_SCROLLUP, -1, "Scroll Up");
        add(SPECIALBTN_SCROLLDOWN, -1, "Scroll Down");
        add(SPECIALBTN_TOGGLECTRL, -1, "Toggle HUD");
        add(SPECIALBTN_KEYBOARD, -1, "Virtual Keyboard");

        // Common gaming keys first for convenience
        add(GLFW_KEY_W, KeyEvent.KEYCODE_W, "W");
        add(GLFW_KEY_A, KeyEvent.KEYCODE_A, "A");
        add(GLFW_KEY_S, KeyEvent.KEYCODE_S, "S");
        add(GLFW_KEY_D, KeyEvent.KEYCODE_D, "D");
        add(GLFW_KEY_SPACE, KeyEvent.KEYCODE_SPACE, "Space");
        add(GLFW_KEY_LEFT_SHIFT, KeyEvent.KEYCODE_SHIFT_LEFT, "L-Shift");
        add(GLFW_KEY_LEFT_CONTROL, KeyEvent.KEYCODE_CTRL_LEFT, "L-Ctrl");
        add(GLFW_KEY_LEFT_ALT, KeyEvent.KEYCODE_ALT_LEFT, "L-Alt");
        add(GLFW_KEY_TAB, KeyEvent.KEYCODE_TAB, "Tab");
        add(GLFW_KEY_ESCAPE, KeyEvent.KEYCODE_ESCAPE, "Esc");
        add(GLFW_KEY_E, KeyEvent.KEYCODE_E, "E");
        add(GLFW_KEY_F, KeyEvent.KEYCODE_F, "F");
        add(GLFW_KEY_G, KeyEvent.KEYCODE_G, "G");
        add(GLFW_KEY_Q, KeyEvent.KEYCODE_Q, "Q");
        add(GLFW_KEY_R, KeyEvent.KEYCODE_R, "R");
        add(GLFW_KEY_C, KeyEvent.KEYCODE_C, "C");
        add(GLFW_KEY_V, KeyEvent.KEYCODE_V, "V");
        add(GLFW_KEY_X, KeyEvent.KEYCODE_X, "X");
        add(GLFW_KEY_Z, KeyEvent.KEYCODE_Z, "Z");
        add(GLFW_KEY_M, KeyEvent.KEYCODE_M, "M");
        add(GLFW_KEY_B, KeyEvent.KEYCODE_B, "B");
        add(GLFW_KEY_H, KeyEvent.KEYCODE_H, "H");
        add(GLFW_KEY_I, KeyEvent.KEYCODE_I, "I");
        add(GLFW_KEY_J, KeyEvent.KEYCODE_J, "J");
        add(GLFW_KEY_K, KeyEvent.KEYCODE_K, "K");
        add(GLFW_KEY_L, KeyEvent.KEYCODE_L, "L");
        add(GLFW_KEY_N, KeyEvent.KEYCODE_N, "N");
        add(GLFW_KEY_O, KeyEvent.KEYCODE_O, "O");
        add(GLFW_KEY_P, KeyEvent.KEYCODE_P, "P");
        add(GLFW_KEY_T, KeyEvent.KEYCODE_T, "T");
        add(GLFW_KEY_U, KeyEvent.KEYCODE_U, "U");
        add(GLFW_KEY_Y, KeyEvent.KEYCODE_Y, "Y");

        add(GLFW_KEY_1, KeyEvent.KEYCODE_1, "1");
        add(GLFW_KEY_2, KeyEvent.KEYCODE_2, "2");
        add(GLFW_KEY_3, KeyEvent.KEYCODE_3, "3");
        add(GLFW_KEY_4, KeyEvent.KEYCODE_4, "4");
        add(GLFW_KEY_5, KeyEvent.KEYCODE_5, "5");
        add(GLFW_KEY_6, KeyEvent.KEYCODE_6, "6");
        add(GLFW_KEY_7, KeyEvent.KEYCODE_7, "7");
        add(GLFW_KEY_8, KeyEvent.KEYCODE_8, "8");
        add(GLFW_KEY_9, KeyEvent.KEYCODE_9, "9");
        add(GLFW_KEY_0, KeyEvent.KEYCODE_0, "0");

        add(GLFW_KEY_ENTER, KeyEvent.KEYCODE_ENTER, "Enter");
        add(GLFW_KEY_BACKSPACE, KeyEvent.KEYCODE_DEL, "Backspace");
        add(GLFW_KEY_DELETE, KeyEvent.KEYCODE_FORWARD_DEL, "Delete");
        add(GLFW_KEY_INSERT, KeyEvent.KEYCODE_INSERT, "Insert");
        add(GLFW_KEY_CAPS_LOCK, KeyEvent.KEYCODE_CAPS_LOCK, "Caps Lock");
        add(GLFW_KEY_RIGHT_SHIFT, KeyEvent.KEYCODE_SHIFT_RIGHT, "R-Shift");
        add(GLFW_KEY_RIGHT_CONTROL, KeyEvent.KEYCODE_CTRL_RIGHT, "R-Ctrl");
        add(GLFW_KEY_RIGHT_ALT, KeyEvent.KEYCODE_ALT_RIGHT, "R-Alt");
        add(GLFW_KEY_LEFT_SUPER, KeyEvent.KEYCODE_META_LEFT, "L-Super");
        add(GLFW_KEY_RIGHT_SUPER, KeyEvent.KEYCODE_META_RIGHT, "R-Super");

        add(GLFW_KEY_UP, KeyEvent.KEYCODE_DPAD_UP, "Arrow Up");
        add(GLFW_KEY_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, "Arrow Down");
        add(GLFW_KEY_LEFT, KeyEvent.KEYCODE_DPAD_LEFT, "Arrow Left");
        add(GLFW_KEY_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, "Arrow Right");
        add(GLFW_KEY_HOME, KeyEvent.KEYCODE_MOVE_HOME, "Home");
        add(GLFW_KEY_END, KeyEvent.KEYCODE_MOVE_END, "End");
        add(GLFW_KEY_PAGE_UP, KeyEvent.KEYCODE_PAGE_UP, "Page Up");
        add(GLFW_KEY_PAGE_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, "Page Down");

        add(GLFW_KEY_GRAVE_ACCENT, KeyEvent.KEYCODE_GRAVE, "` (Tilde)");
        add(GLFW_KEY_MINUS, KeyEvent.KEYCODE_MINUS, "-");
        add(GLFW_KEY_EQUAL, KeyEvent.KEYCODE_EQUALS, "=");
        add(GLFW_KEY_LEFT_BRACKET, KeyEvent.KEYCODE_LEFT_BRACKET, "[");
        add(GLFW_KEY_RIGHT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, "]");
        add(GLFW_KEY_BACKSLASH, KeyEvent.KEYCODE_BACKSLASH, "\\");
        add(GLFW_KEY_SEMICOLON, KeyEvent.KEYCODE_SEMICOLON, ";");
        add(GLFW_KEY_APOSTROPHE, KeyEvent.KEYCODE_APOSTROPHE, "'");
        add(GLFW_KEY_COMMA, KeyEvent.KEYCODE_COMMA, ",");
        add(GLFW_KEY_PERIOD, KeyEvent.KEYCODE_PERIOD, ".");
        add(GLFW_KEY_SLASH, KeyEvent.KEYCODE_SLASH, "/");

        add(GLFW_KEY_F1, KeyEvent.KEYCODE_F1, "F1");
        add(GLFW_KEY_F2, KeyEvent.KEYCODE_F2, "F2");
        add(GLFW_KEY_F3, KeyEvent.KEYCODE_F3, "F3");
        add(GLFW_KEY_F4, KeyEvent.KEYCODE_F4, "F4");
        add(GLFW_KEY_F5, KeyEvent.KEYCODE_F5, "F5");
        add(GLFW_KEY_F6, KeyEvent.KEYCODE_F6, "F6");
        add(GLFW_KEY_F7, KeyEvent.KEYCODE_F7, "F7");
        add(GLFW_KEY_F8, KeyEvent.KEYCODE_F8, "F8");
        add(GLFW_KEY_F9, KeyEvent.KEYCODE_F9, "F9");
        add(GLFW_KEY_F10, KeyEvent.KEYCODE_F10, "F10");
        add(GLFW_KEY_F11, KeyEvent.KEYCODE_F11, "F11");
        add(GLFW_KEY_F12, KeyEvent.KEYCODE_F12, "F12");

        add(GLFW_KEY_KP_0, KeyEvent.KEYCODE_NUMPAD_0, "Num 0");
        add(GLFW_KEY_KP_1, KeyEvent.KEYCODE_NUMPAD_1, "Num 1");
        add(GLFW_KEY_KP_2, KeyEvent.KEYCODE_NUMPAD_2, "Num 2");
        add(GLFW_KEY_KP_3, KeyEvent.KEYCODE_NUMPAD_3, "Num 3");
        add(GLFW_KEY_KP_4, KeyEvent.KEYCODE_NUMPAD_4, "Num 4");
        add(GLFW_KEY_KP_5, KeyEvent.KEYCODE_NUMPAD_5, "Num 5");
        add(GLFW_KEY_KP_6, KeyEvent.KEYCODE_NUMPAD_6, "Num 6");
        add(GLFW_KEY_KP_7, KeyEvent.KEYCODE_NUMPAD_7, "Num 7");
        add(GLFW_KEY_KP_8, KeyEvent.KEYCODE_NUMPAD_8, "Num 8");
        add(GLFW_KEY_KP_9, KeyEvent.KEYCODE_NUMPAD_9, "Num 9");
        add(GLFW_KEY_KP_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, "Num Enter");
    }

    private LwjglGlfwKeycode() {
    }

    private static void add(int glfwCode, int androidCode, String label) {
        if (androidCode > 0) {
            GLFW_TO_ANDROID.put(glfwCode, androidCode);
        }
        KEY_NAMES.put(glfwCode, label);
        KEY_ENTRIES.add(new KeyEntry(glfwCode, label));
    }

    public static int toAndroidKeyCode(int glfwCode) {
        return GLFW_TO_ANDROID.get(glfwCode, 0);
    }

    public static String getKeyName(int glfwCode) {
        String name = KEY_NAMES.get(glfwCode);
        if (name != null) return name;
        return "Key " + glfwCode;
    }

    public static List<KeyEntry> getAvailableKeyEntries() {
        return KEY_ENTRIES;
    }
}
