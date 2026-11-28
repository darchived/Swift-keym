package com.example.input;

import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * JNI wrapper for Linux /dev/uinput virtual mouse & keyboard injection.
 * Separates SwiftKeyM-VirtualMouse and SwiftKeyM-VirtualKeyboard into distinct uinput devices
 * to bypass Android EventHub mouse-key classification bugs.
 */
public final class UinputBridge {
    private static final String TAG = "UinputBridge";

    public static final int BTN_LEFT = 0x110;
    public static final int BTN_RIGHT = 0x111;
    public static final int BTN_MIDDLE = 0x112;

    private static boolean sNativeLoaded = false;

    static {
        try {
            System.loadLibrary("uinput_helper");
            sNativeLoaded = true;
        } catch (Throwable t) {
            sNativeLoaded = false;
            Log.w(TAG, "libuinput_helper.so not available; InputManager fallback active", t);
        }
    }

    private static final SparseIntArray ANDROID_TO_LINUX_KEY = new SparseIntArray();

    static {
        // Letters (linux/input-event-codes.h)
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_Q, 16);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_W, 17);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_E, 18);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_R, 19);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_T, 20);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_Y, 21);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_U, 22);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_I, 23);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_O, 24);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_P, 25);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_A, 30);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_S, 31);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_D, 32);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F, 33);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_G, 34);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_H, 35);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_J, 36);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_K, 37);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_L, 38);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_Z, 44);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_X, 45);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_C, 46);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_V, 47);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_B, 48);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_N, 49);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_M, 50);

        // Top number row
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_1, 2);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_2, 3);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_3, 4);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_4, 5);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_5, 6);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_6, 7);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_7, 8);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_8, 9);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_9, 10);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_0, 11);

        // Core controls & modifiers
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_ESCAPE, 1);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_MINUS, 12);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_EQUALS, 13);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_DEL, 14); // Backspace
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_TAB, 15);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_LEFT_BRACKET, 26);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_RIGHT_BRACKET, 27);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_ENTER, 28);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_CTRL_LEFT, 29);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_SEMICOLON, 39);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_APOSTROPHE, 40);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_GRAVE, 41);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_SHIFT_LEFT, 42);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_BACKSLASH, 43);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_COMMA, 51);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_PERIOD, 52);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_SLASH, 53);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_SHIFT_RIGHT, 54);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_ALT_LEFT, 56);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_SPACE, 57);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_CAPS_LOCK, 58);

        // Function keys
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F1, 59);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F2, 60);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F3, 61);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F4, 62);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F5, 63);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F6, 64);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F7, 65);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F8, 66);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F9, 67);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F10, 68);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F11, 87);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_F12, 88);

        // Navigation & Numpad
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_CTRL_RIGHT, 97);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_ALT_RIGHT, 100);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_MOVE_HOME, 102);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_DPAD_UP, 103);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_PAGE_UP, 104);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_DPAD_LEFT, 105);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_DPAD_RIGHT, 106);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_MOVE_END, 107);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_DPAD_DOWN, 108);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_PAGE_DOWN, 109);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_INSERT, 110);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_FORWARD_DEL, 111);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_META_LEFT, 125);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_META_RIGHT, 126);

        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_7, 71);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_8, 72);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_9, 73);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_4, 75);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_5, 76);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_6, 77);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_1, 79);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_2, 80);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_3, 81);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_0, 82);
        ANDROID_TO_LINUX_KEY.put(KeyEvent.KEYCODE_NUMPAD_ENTER, 96);
    }

    private UinputBridge() {}

    public static boolean isNativeLoaded() {
        return sNativeLoaded;
    }

    public static int toLinuxMouseButton(int androidButton) {
        if (androidButton == MotionEvent.BUTTON_PRIMARY) return BTN_LEFT;
        if (androidButton == MotionEvent.BUTTON_SECONDARY) return BTN_RIGHT;
        if (androidButton == MotionEvent.BUTTON_TERTIARY) return BTN_MIDDLE;
        return 0;
    }

    public static int toLinuxKeyCode(int androidKeyCode) {
        return ANDROID_TO_LINUX_KEY.get(androidKeyCode, 0);
    }

    public static void destroyUinput() {
        if (!sNativeLoaded) return;
        try {
            nativeDestroyUinput();
        } catch (Throwable ignored) {
        }
    }

    public static int openMouseDevice() {
        if (!sNativeLoaded) return -1;
        try {
            return nativeOpenMouseUinput();
        } catch (Throwable t) {
            Log.w(TAG, "nativeOpenMouseUinput threw", t);
            return -1;
        }
    }

    public static int openKeyboardDevice() {
        if (!sNativeLoaded) return -1;
        try {
            return nativeOpenKeyboardUinput();
        } catch (Throwable t) {
            Log.w(TAG, "nativeOpenKeyboardUinput threw", t);
            return -1;
        }
    }

    public static boolean injectMouseButton(int fd, int androidButton, boolean down) {
        if (!sNativeLoaded || fd < 0) return false;
        int linuxBtn = toLinuxMouseButton(androidButton);
        if (linuxBtn == 0) return false;
        try {
            return nativeInjectMouseButton(fd, linuxBtn, down);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean injectMouseMove(int fd, int dx, int dy) {
        if (!sNativeLoaded || fd < 0) return false;
        if (dx == 0 && dy == 0) return true;
        try {
            return nativeInjectMouseMove(fd, dx, dy);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean injectMouseKeepAlive(int fd) {
        if (!sNativeLoaded || fd < 0) return false;
        try {
            return nativeInjectMouseKeepAlive(fd);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean injectMouseScroll(int fd, int vScroll, int hScroll) {
        if (!sNativeLoaded || fd < 0) return false;
        if (vScroll == 0 && hScroll == 0) return true;
        try {
            return nativeInjectMouseScroll(fd, vScroll, hScroll);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean injectKey(int fd, int androidKeyCode, boolean down) {
        if (!sNativeLoaded || fd < 0) return false;
        int linuxCode = toLinuxKeyCode(androidKeyCode);
        if (linuxCode <= 0) return false;
        try {
            return nativeInjectKey(fd, linuxCode, down);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void closeDevice(int fd) {
        if (!sNativeLoaded || fd < 0) return;
        try {
            nativeCloseUinput(fd);
        } catch (Throwable ignored) {
        }
    }

    private static native void nativeDestroyUinput();
    private static native int nativeOpenMouseUinput();
    private static native int nativeOpenKeyboardUinput();
    private static native boolean nativeInjectMouseButton(int fd, int linuxButton, boolean down);
    private static native boolean nativeInjectMouseMove(int fd, int dx, int dy);
    private static native boolean nativeInjectMouseKeepAlive(int fd);
    private static native boolean nativeInjectMouseScroll(int fd, int vScroll, int hScroll);
    private static native boolean nativeInjectKey(int fd, int linuxKeyCode, boolean down);
    private static native void nativeCloseUinput(int fd);
}
