package com.example.input;

import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ShizukuInputService extends IInputService.Stub {
    private static final String TAG = "ShizukuInputService";
    private static final long MOUSE_KEEP_ALIVE_MS = 1500L;

    // Separate kernel virtual USB mouse & keyboard (/dev/uinput) file descriptors
    private volatile int uinputMouseFd = -1;
    private volatile int uinputKbdFd = -1;
    private float uinputRelAccX = 0f;
    private float uinputRelAccY = 0f;
    private volatile boolean virtualMouseActive = false;
    private volatile long lastMouseActivityMs = 0L;

    private final Object mouseLock = new Object();
    private final ConcurrentHashMap<Integer, Long> keyDownTimes = new ConcurrentHashMap<>();
    private final ExecutorService shellExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService keepAliveScheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile int activeMetaState = 0;
    private volatile int mouseButtonState = 0;

    public ShizukuInputService() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
        } catch (Throwable ignored) {
        }
        setHardKeyboardImeSetting(true);
        initUinput();
        keepAliveScheduler.scheduleAtFixedRate(() -> {
            if (!virtualMouseActive) return;
            if (uinputMouseFd < 0) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastMouseActivityMs >= 1400L) {
                sendMouseKeepAlive();
            }
        }, MOUSE_KEEP_ALIVE_MS, MOUSE_KEEP_ALIVE_MS, TimeUnit.MILLISECONDS);
    }

    private void setHardKeyboardImeSetting(boolean allowSoftImeWithHardKeyboard) {
        try {
            String val = allowSoftImeWithHardKeyboard ? "1" : "0";
            runProcess(Arrays.asList("settings", "put", "secure", "show_ime_with_hard_keyboard", val));
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

    @Override
    public synchronized void injectKey(int keyCode, int action, int metaState) {
        if (keyCode <= 0) return;
        boolean down = action == KeyEvent.ACTION_DOWN;
        long now = SystemClock.uptimeMillis();
        updateModifierState(keyCode, down);

        if (down) {
            Long existing = keyDownTimes.get(keyCode);
            keyDownTimes.put(keyCode, existing != null ? existing : now);
        } else {
            keyDownTimes.remove(keyCode);
        }

        // Primary backend: Linux /dev/uinput dedicated Virtual Keyboard device
        if (uinputKbdFd >= 0 && UinputBridge.toLinuxKeyCode(keyCode) > 0) {
            if (UinputBridge.injectKey(uinputKbdFd, keyCode, down)) {
                return;
            }
        }

        // Reflection-free fallback when /dev/uinput is unavailable
        if (down) {
            final int code = keyCode;
            shellExecutor.execute(() -> runProcess(Arrays.asList("input", "keyevent", String.valueOf(code))));
        }
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
    public void injectMouseButton(int button, boolean down) {
        synchronized (mouseLock) {
            int prevState = mouseButtonState;
            if (down) {
                if ((prevState & button) != 0) return;
                mouseButtonState = prevState | button;
            } else {
                if ((prevState & button) == 0) return;
                mouseButtonState = prevState & ~button;
            }

            if (uinputMouseFd >= 0) {
                UinputBridge.injectMouseButton(uinputMouseFd, button, down);
                lastMouseActivityMs = SystemClock.uptimeMillis();
            }
        }
    }

    @Override
    public void injectMouseMove(float dx, float dy) {
        if (dx == 0f && dy == 0f) return;
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
        } catch (Throwable ignored) {
        }

        synchronized (mouseLock) {
            if (uinputMouseFd >= 0) {
                uinputRelAccX += dx;
                uinputRelAccY += dy;
                int ix = (int) uinputRelAccX;
                int iy = (int) uinputRelAccY;
                uinputRelAccX -= ix;
                uinputRelAccY -= iy;
                if (ix == 0 && iy == 0) return;
                UinputBridge.injectMouseMove(uinputMouseFd, ix, iy);
                lastMouseActivityMs = SystemClock.uptimeMillis();
            }
        }
    }

    @Override
    public void injectMouseScroll(float vScroll, float hScroll) {
        synchronized (mouseLock) {
            if (uinputMouseFd >= 0) {
                int iv = Math.round(vScroll);
                int ih = Math.round(hScroll);
                if (iv != 0 || ih != 0) {
                    UinputBridge.injectMouseScroll(uinputMouseFd, iv, ih);
                    lastMouseActivityMs = SystemClock.uptimeMillis();
                }
            }
        }
    }

    @Override
    public void setVirtualMouseActive(boolean active) {
        virtualMouseActive = active;
        if (active) {
            sendMouseKeepAlive();
        }
    }

    @Override
    public void sendMouseKeepAlive() {
        synchronized (mouseLock) {
            if (uinputMouseFd >= 0) {
                UinputBridge.injectMouseKeepAlive(uinputMouseFd);
                lastMouseActivityMs = SystemClock.uptimeMillis();
            }
        }
    }

    @Override
    public synchronized void resetAllInputs() {
        synchronized (mouseLock) {
            uinputRelAccX = 0f;
            uinputRelAccY = 0f;
        }

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

    @Override
    public synchronized void destroyUinput() {
        virtualMouseActive = false;
        try {
            resetAllInputs();
        } catch (Throwable ignored) {
        }
        synchronized (mouseLock) {
            try {
                UinputBridge.destroyUinput();
            } catch (Throwable ignored) {
            }
            uinputMouseFd = -1;
            uinputKbdFd = -1;
        }
        setHardKeyboardImeSetting(true);
        try {
            runProcess(Arrays.asList("input", "keyevent", "0"));
        } catch (Throwable ignored) {
        }
    }

    private boolean runAppOpsAllow(String packageName, String op) {
        String res = runProcess(Arrays.asList("cmd", "appops", "set", packageName, op, "allow"));
        if (res.startsWith("0\n")) {
            return true;
        }
        String fallback = runProcess(Arrays.asList("appops", "set", packageName, op, "allow"));
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
        destroyUinput();
        keepAliveScheduler.shutdownNow();
        shellExecutor.shutdownNow();
        System.exit(0);
    }
}
