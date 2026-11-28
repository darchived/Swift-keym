package com.example.input;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ShizukuInputService extends IInputService.Stub {
    private static final String TAG = "ShizukuInputService";
    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    private Object inputManagerInstance;
    private Method injectInputEventMethod;
    private Method setActionButtonMethod;

    // Separate kernel virtual USB mouse & keyboard (/dev/uinput) file descriptors
    private volatile int uinputMouseFd = -1;
    private volatile int uinputKbdFd = -1;
    private volatile float uinputRelAccX = 0f;
    private volatile float uinputRelAccY = 0f;

    private final ConcurrentHashMap<Integer, Long> keyDownTimes = new ConcurrentHashMap<>();
    private volatile int activeMetaState = 0;
    private volatile int mouseButtonState = 0;
    private volatile long mouseDownTime = 0L;
    private volatile float cursorX = 960f;
    private volatile float cursorY = 540f;

    public ShizukuInputService() {
        initReflection();
        setHardKeyboardImeSetting(true);
        initUinput();
    }

    private void setHardKeyboardImeSetting(boolean allowSoftImeWithHardKeyboard) {
        try {
            String val = allowSoftImeWithHardKeyboard ? "1" : "0";
            Runtime.getRuntime().exec(new String[]{"settings", "put", "secure", "show_ime_with_hard_keyboard", val});
            Log.i(TAG, "Successfully set show_ime_with_hard_keyboard = " + val);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to update show_ime_with_hard_keyboard setting", t);
        }
    }

    private void initUinput() {
        // Clean up any leftover uinput devices first
        try {
            UinputBridge.destroyUinput();
        } catch (Throwable ignored) {}

        int mouseFd = UinputBridge.openMouseDevice();
        if (mouseFd >= 0) {
            uinputMouseFd = mouseFd;
            Log.i(TAG, "Registered kernel /dev/uinput virtual Mouse (fd=" + mouseFd + ")");
        } else {
            uinputMouseFd = -1;
            Log.w(TAG, "Virtual Mouse /dev/uinput unavailable (code=" + mouseFd + ")");
        }

        int kbdFd = UinputBridge.openKeyboardDevice();
        if (kbdFd >= 0) {
            uinputKbdFd = kbdFd;
            Log.i(TAG, "Registered kernel /dev/uinput virtual Keyboard (fd=" + kbdFd + ")");
        } else {
            uinputKbdFd = -1;
            Log.w(TAG, "Virtual Keyboard /dev/uinput unavailable (code=" + kbdFd + ")");
        }
    }

    private void initReflection() {
        // Android 14+ moved injectInputEvent into android.hardware.input.InputManagerGlobal
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Class<?> globalClass = Class.forName("android.hardware.input.InputManagerGlobal");
                Method getInstance = globalClass.getDeclaredMethod("getInstance");
                getInstance.setAccessible(true);
                inputManagerInstance = getInstance.invoke(null);
                injectInputEventMethod = globalClass.getMethod("injectInputEvent", InputEvent.class, int.class);
                injectInputEventMethod.setAccessible(true);
            } catch (Throwable t) {
                Log.w(TAG, "InputManagerGlobal reflection fallback triggered", t);
                inputManagerInstance = null;
                injectInputEventMethod = null;
            }
        }

        if (inputManagerInstance == null || injectInputEventMethod == null) {
            try {
                Class<?> imClass = Class.forName("android.hardware.input.InputManager");
                Method getInstance = imClass.getDeclaredMethod("getInstance");
                getInstance.setAccessible(true);
                inputManagerInstance = getInstance.invoke(null);
                injectInputEventMethod = imClass.getMethod("injectInputEvent", InputEvent.class, int.class);
                injectInputEventMethod.setAccessible(true);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to initialize InputManager reflection", t);
            }
        }

        try {
            setActionButtonMethod = MotionEvent.class.getDeclaredMethod("setActionButton", int.class);
            setActionButtonMethod.setAccessible(true);
        } catch (Throwable ignored) {
            setActionButtonMethod = null;
        }
    }

    private boolean injectEvent(InputEvent event) {
        if (inputManagerInstance == null || injectInputEventMethod == null || event == null) {
            return false;
        }
        try {
            Object res = injectInputEventMethod.invoke(inputManagerInstance, event, INJECT_INPUT_EVENT_MODE_ASYNC);
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            Log.e(TAG, "injectInputEvent failed", t);
            return false;
        }
    }

    @Override
    public synchronized void injectKey(int keyCode, int action, int metaState) {
        if (keyCode <= 0) return;
        boolean down = action == KeyEvent.ACTION_DOWN;
        long now = SystemClock.uptimeMillis();
        updateModifierState(keyCode, down);
        int combinedMeta = activeMetaState | metaState;

        long downTime;
        if (down) {
            Long existing = keyDownTimes.get(keyCode);
            downTime = existing != null ? existing : now;
            keyDownTimes.put(keyCode, downTime);
        } else {
            Long existing = keyDownTimes.remove(keyCode);
            downTime = existing != null ? existing : now;
        }

        // Primary backend: Linux /dev/uinput dedicated Virtual Keyboard device
        if (uinputKbdFd >= 0 && UinputBridge.toLinuxKeyCode(keyCode) > 0) {
            if (UinputBridge.injectKey(uinputKbdFd, keyCode, down)) {
                return;
            }
        }

        // Fallback: InputManager.injectInputEvent
        KeyEvent keyEvent = new KeyEvent(
                downTime,
                now,
                action,
                keyCode,
                0,
                combinedMeta,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
        );
        injectEvent(keyEvent);
    }

    private void updateModifierState(int keyCode, boolean down) {
        int mask = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                mask = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                mask = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_CTRL_LEFT:
                mask = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                mask = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_ALT_LEFT:
                mask = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_ALT_RIGHT:
                mask = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_RIGHT_ON;
                break;
        }
        if (mask != 0) {
            if (down) {
                activeMetaState |= mask;
            } else {
                activeMetaState &= ~mask;
            }
        }
    }

    @Override
    public synchronized void injectMouseButton(int button, boolean down) {
        long now = SystemClock.uptimeMillis();
        int prevState = mouseButtonState;
        if (down) {
            if ((prevState & button) != 0) return;
            if (prevState == 0) {
                mouseDownTime = now;
            }
            mouseButtonState = prevState | button;
        } else {
            if ((prevState & button) == 0) return;
            mouseButtonState = prevState & ~button;
        }

        // Primary backend: /dev/uinput Virtual Mouse device
        if (uinputMouseFd >= 0 && UinputBridge.injectMouseButton(uinputMouseFd, button, down)) {
            return;
        }

        // Fallback backend: InputManager.injectInputEvent with SOURCE_MOUSE + TOOL_TYPE_MOUSE
        if (down) {
            if (prevState == 0) {
                MotionEvent downEvent = obtainMouseEvent(
                        mouseDownTime, now, MotionEvent.ACTION_DOWN, mouseButtonState,
                        0f, 0f, 0f, 0f, InputDevice.SOURCE_MOUSE
                );
                injectAndRecycle(downEvent);
            }
            MotionEvent pressEvent = obtainMouseEvent(
                    mouseDownTime, now, MotionEvent.ACTION_BUTTON_PRESS, mouseButtonState,
                    0f, 0f, 0f, 0f, InputDevice.SOURCE_MOUSE
            );
            applyActionButton(pressEvent, button);
            injectAndRecycle(pressEvent);
        } else {
            MotionEvent releaseEvent = obtainMouseEvent(
                    mouseDownTime, now, MotionEvent.ACTION_BUTTON_RELEASE, mouseButtonState,
                    0f, 0f, 0f, 0f, InputDevice.SOURCE_MOUSE
            );
            applyActionButton(releaseEvent, button);
            injectAndRecycle(releaseEvent);

            if (mouseButtonState == 0) {
                MotionEvent upEvent = obtainMouseEvent(
                        mouseDownTime, now, MotionEvent.ACTION_UP, 0,
                        0f, 0f, 0f, 0f, InputDevice.SOURCE_MOUSE
                );
                injectAndRecycle(upEvent);
            }
        }
    }

    @Override
    public synchronized void injectMouseMove(float dx, float dy) {
        if (dx == 0f && dy == 0f) return;

        // Primary backend: /dev/uinput Virtual Mouse device
        if (uinputMouseFd >= 0) {
            uinputRelAccX += dx;
            uinputRelAccY += dy;
            int ix = (int) uinputRelAccX;
            int iy = (int) uinputRelAccY;
            uinputRelAccX -= ix;
            uinputRelAccY -= iy;
            if (ix == 0 && iy == 0) return;
            if (UinputBridge.injectMouseMove(uinputMouseFd, ix, iy)) {
                return;
            }
        }

        // Fallback backend: InputManager.injectInputEvent with InputDevice.SOURCE_MOUSE_RELATIVE
        long now = SystemClock.uptimeMillis();
        cursorX = Math.max(1f, Math.min(1919f, cursorX + dx));
        cursorY = Math.max(1f, Math.min(1079f, cursorY + dy));

        long downTime = (mouseButtonState != 0) ? mouseDownTime : now;
        MotionEvent moveEvent = obtainRelativeMouseEvent(downTime, now, mouseButtonState, dx, dy);
        injectAndRecycle(moveEvent);
    }

    @Override
    public synchronized void injectMouseScroll(float vScroll, float hScroll) {
        if (uinputMouseFd >= 0) {
            int iv = Math.round(vScroll);
            int ih = Math.round(hScroll);
            if ((iv != 0 || ih != 0) && UinputBridge.injectMouseScroll(uinputMouseFd, iv, ih)) {
                return;
            }
        }

        long now = SystemClock.uptimeMillis();
        MotionEvent scrollEvent = obtainMouseEvent(
                now, now, MotionEvent.ACTION_SCROLL, mouseButtonState,
                0f, 0f, vScroll, hScroll, InputDevice.SOURCE_MOUSE
        );
        injectAndRecycle(scrollEvent);
    }

    @Override
    public synchronized void resetAllInputs() {
        uinputRelAccX = 0f;
        uinputRelAccY = 0f;

        List<Integer> heldKeys = new ArrayList<>(keyDownTimes.keySet());
        for (Integer keyCode : heldKeys) {
            injectKey(keyCode, KeyEvent.ACTION_UP, 0);
        }
        activeMetaState = 0;

        int state = mouseButtonState;
        if ((state & MotionEvent.BUTTON_PRIMARY) != 0) {
            injectMouseButton(MotionEvent.BUTTON_PRIMARY, false);
        }
        if ((state & MotionEvent.BUTTON_SECONDARY) != 0) {
            injectMouseButton(MotionEvent.BUTTON_SECONDARY, false);
        }
        if ((state & MotionEvent.BUTTON_TERTIARY) != 0) {
            injectMouseButton(MotionEvent.BUTTON_TERTIARY, false);
        }
        mouseButtonState = 0;
    }

    @Override
    public String execShell(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "1\n";
        }
        List<String> args = tokenizeCommand(command);
        if (args.isEmpty()) {
            return "1\n";
        }
        return runProcess(args);
    }

    @Override
    public boolean allowOverlayAppOps(String packageName) {
        if (packageName == null || !packageName.matches("[a-zA-Z0-9._]+")) {
            return false;
        }
        boolean restrictedCleared = runAppOpsAllow(packageName, "ACCESS_RESTRICTED_SETTINGS");
        boolean overlayAllowed = runAppOpsAllow(packageName, "SYSTEM_ALERT_WINDOW");
        return restrictedCleared || overlayAllowed;
    }

    private boolean runAppOpsAllow(String packageName, String op) {
        String res = runProcess(java.util.Arrays.asList("cmd", "appops", "set", packageName, op, "allow"));
        if (res.startsWith("0\n")) {
            return true;
        }
        String fallback = runProcess(java.util.Arrays.asList("appops", "set", packageName, op, "allow"));
        return fallback.startsWith("0\n");
    }

    private String runProcess(List<String> args) {
        Process process = null;
        try {
            process = new ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            int exitCode = process.waitFor();
            return exitCode + "\n" + sb;
        } catch (Throwable t) {
            return "-1\n" + (t.getMessage() != null ? t.getMessage() : "Execution error");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static List<String> tokenizeCommand(String cmd) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    @Override
    public void destroy() {
        try {
            resetAllInputs();
        } catch (Throwable ignored) {
        }
        try {
            UinputBridge.destroyUinput();
        } catch (Throwable ignored) {
        }
        uinputMouseFd = -1;
        uinputKbdFd = -1;

        // Guarantee soft keyboard IME is re-enabled immediately
        setHardKeyboardImeSetting(true);

        System.exit(0);
    }

    private MotionEvent obtainRelativeMouseEvent(
            long downTime,
            long eventTime,
            int buttonState,
            float relX,
            float relY
    ) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
        props[0] = new MotionEvent.PointerProperties();
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].x = relX;
        coords[0].y = relY;
        coords[0].pressure = (buttonState != 0) ? 1.0f : 0.0f;
        coords[0].size = 1.0f;
        coords[0].setAxisValue(MotionEvent.AXIS_RELATIVE_X, relX);
        coords[0].setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relY);

        return MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_MOVE,
                1,
                props,
                coords,
                activeMetaState,
                buttonState,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_MOUSE_RELATIVE,
                0
        );
    }

    private MotionEvent obtainMouseEvent(
            long downTime,
            long eventTime,
            int action,
            int buttonState,
            float relX,
            float relY,
            float vScroll,
            float hScroll,
            int source
    ) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
        props[0] = new MotionEvent.PointerProperties();
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].x = cursorX;
        coords[0].y = cursorY;
        coords[0].pressure = (buttonState != 0) ? 1.0f : 0.0f;
        coords[0].size = 1.0f;
        coords[0].setAxisValue(MotionEvent.AXIS_RELATIVE_X, relX);
        coords[0].setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relY);
        if (vScroll != 0f) {
            coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);
        }
        if (hScroll != 0f) {
            coords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll);
        }

        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,
                props,
                coords,
                activeMetaState,
                buttonState,
                1.0f,
                1.0f,
                0,
                0,
                source,
                0
        );
    }

    private void applyActionButton(MotionEvent event, int button) {
        if (setActionButtonMethod != null && event != null) {
            try {
                setActionButtonMethod.invoke(event, button);
            } catch (Throwable ignored) {
            }
        }
    }

    private void injectAndRecycle(MotionEvent event) {
        if (event != null) {
            try {
                injectEvent(event);
            } finally {
                event.recycle();
            }
        }
    }
}
