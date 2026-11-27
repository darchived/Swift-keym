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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShizukuInputService extends IInputService.Stub {
    private static final String TAG = "ShizukuInputService";
    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    private Object inputManagerInstance;
    private Method injectInputEventMethod;
    private Method setActionButtonMethod;

    private final ConcurrentHashMap<Integer, Long> keyDownTimes = new ConcurrentHashMap<>();
    private volatile int activeMetaState = 0;
    private volatile int mouseButtonState = 0;
    private volatile long mouseDownTime = 0L;
    private volatile float cursorX = 960f;
    private volatile float cursorY = 540f;

    public ShizukuInputService() {
        initReflection();
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
        long now = SystemClock.uptimeMillis();
        updateModifierState(keyCode, action == KeyEvent.ACTION_DOWN);
        int combinedMeta = activeMetaState | metaState;

        long downTime;
        if (action == KeyEvent.ACTION_DOWN) {
            Long existing = keyDownTimes.get(keyCode);
            downTime = existing != null ? existing : now;
            keyDownTimes.put(keyCode, downTime);
        } else {
            Long existing = keyDownTimes.remove(keyCode);
            downTime = existing != null ? existing : now;
        }

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

            if (prevState == 0) {
                MotionEvent downEvent = obtainMouseEvent(mouseDownTime, now, MotionEvent.ACTION_DOWN, mouseButtonState, 0f, 0f, 0f, 0f);
                injectAndRecycle(downEvent);
            }
            MotionEvent pressEvent = obtainMouseEvent(mouseDownTime, now, MotionEvent.ACTION_BUTTON_PRESS, mouseButtonState, 0f, 0f, 0f, 0f);
            applyActionButton(pressEvent, button);
            injectAndRecycle(pressEvent);
        } else {
            if ((prevState & button) == 0) return;
            mouseButtonState = prevState & ~button;

            MotionEvent releaseEvent = obtainMouseEvent(mouseDownTime, now, MotionEvent.ACTION_BUTTON_RELEASE, mouseButtonState, 0f, 0f, 0f, 0f);
            applyActionButton(releaseEvent, button);
            injectAndRecycle(releaseEvent);

            if (mouseButtonState == 0) {
                MotionEvent upEvent = obtainMouseEvent(mouseDownTime, now, MotionEvent.ACTION_UP, 0, 0f, 0f, 0f, 0f);
                injectAndRecycle(upEvent);
            }
        }
    }

    @Override
    public synchronized void injectMouseMove(float dx, float dy) {
        if (dx == 0f && dy == 0f) return;
        long now = SystemClock.uptimeMillis();

        cursorX = Math.max(1f, Math.min(1919f, cursorX + dx));
        cursorY = Math.max(1f, Math.min(1079f, cursorY + dy));

        int action = (mouseButtonState != 0) ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_HOVER_MOVE;
        long downTime = (mouseButtonState != 0) ? mouseDownTime : now;

        MotionEvent moveEvent = obtainMouseEvent(downTime, now, action, mouseButtonState, dx, dy, 0f, 0f);
        injectAndRecycle(moveEvent);
    }

    @Override
    public synchronized void injectMouseScroll(float vScroll, float hScroll) {
        long now = SystemClock.uptimeMillis();
        MotionEvent scrollEvent = obtainMouseEvent(now, now, MotionEvent.ACTION_SCROLL, mouseButtonState, 0f, 0f, vScroll, hScroll);
        injectAndRecycle(scrollEvent);
    }

    @Override
    public synchronized void resetAllInputs() {
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
        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", command)
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
            return "-1\n" + (t.getMessage() != null ? t.getMessage() : "Shell execution error");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    @Override
    public void destroy() {
        try {
            resetAllInputs();
        } catch (Throwable ignored) {
        }
        System.exit(0);
    }

    private MotionEvent obtainMouseEvent(
            long downTime,
            long eventTime,
            int action,
            int buttonState,
            float relX,
            float relY,
            float vScroll,
            float hScroll
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
                InputDevice.SOURCE_MOUSE,
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
