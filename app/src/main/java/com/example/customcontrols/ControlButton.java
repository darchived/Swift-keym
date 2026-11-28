package com.example.customcontrols;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;

import com.example.customcontrols.utils.ControlUtils;
import com.example.input.InputBridge;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Custom HUD Button supporting text labels and custom PNG background icons.
 */
@SuppressLint("AppCompatCustomView")
public class ControlButton extends Button implements ControlInterface {

    protected ControlData mProperties;
    protected final ControlLayout mParentLayout;
    protected boolean mIsToggled = false;
    protected boolean mIsPressed = false;

    private float downRawX;
    private float downRawY;
    private float startX;
    private float startY;
    private boolean hasMoved;
    private final int touchSlop;
    private String cachedIconPath = "";
    private Bitmap cachedIconBitmap = null;

    public ControlButton(Context context, ControlLayout parentLayout, ControlData properties) {
        super(context);
        this.mParentLayout = parentLayout;
        this.mProperties = properties != null ? properties : new ControlData();
        this.mProperties.ensureValid();
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setGravity(Gravity.CENTER);
        setAllCaps(false);
        setTextColor(Color.WHITE);
        setTypeface(Typeface.DEFAULT_BOLD);
        setTextSize(12f);
        setMinWidth(0);
        setMinHeight(0);
        setMinimumWidth(0);
        setMinimumHeight(0);
        setPadding(6, 4, 6, 4);
        setStateListAnimator(null);

        updateProperties();
    }

    @Override
    public View getControlView() {
        return this;
    }

    @Override
    public ControlData getProperties() {
        return mProperties;
    }

    @Override
    public void updateProperties() {
        mProperties.ensureValid();
        int wPx = (int) Math.max(24f, mProperties.getWidthPx(getContext()));
        int hPx = (int) Math.max(24f, mProperties.getHeightPx(getContext()));

        mParentLayout.resizeManagedView(this, wPx, hPx);
        refreshLabelForLayer(mParentLayout.isLayerShiftActive());

        applyEffectiveOpacity();
        applyBackgroundDrawable();
        regenerateDynamicCoordinates();
    }

    public void onLayerShiftChanged(boolean active) {
        refreshLabelForLayer(active);
        applyBackgroundDrawable();
    }

    private void refreshLabelForLayer(boolean layerActive) {
        String primary = mProperties.name != null ? mProperties.name.trim() : "";
        if (layerActive && !mProperties.isLayerShiftKey) {
            String sec = mProperties.secondaryName != null ? mProperties.secondaryName.trim() : "";
            if (!sec.isEmpty()) {
                setText(sec);
                return;
            }
            if (mProperties.secondaryKeycodes != null && mProperties.secondaryKeycodes[0] != 0) {
                setText(com.example.input.LwjglGlfwKeycode.getKeyName(mProperties.secondaryKeycodes[0]));
                return;
            }
        }
        setText(primary);
    }

    public void applyEffectiveOpacity() {
        float effective = mProperties.opacity * mParentLayout.getGlobalOpacity();
        setAlpha(ControlUtils.clamp(effective, 0.08f, 1.0f));
    }

    protected void applyBackgroundDrawable() {
        int wPx = (int) Math.max(24f, mProperties.getWidthPx(getContext()));
        int hPx = (int) Math.max(24f, mProperties.getHeightPx(getContext()));
        float maxRadius = Math.min(wPx, hPx) / 2f;
        float radiusPx = maxRadius * ControlUtils.clamp(mProperties.cornerRadius, 0f, 100f) / 100f;

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        int fillColor = mProperties.bgColor;
        int strokeColor = mProperties.strokeColor;
        if (mIsPressed || mIsToggled) {
            fillColor = blendHighlight(fillColor);
            strokeColor = 0xFFFFFFFF;
        }
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        int strokePx = (int) ControlUtils.dpToPx(getContext(), Math.max(0, mProperties.strokeWidth));
        if (strokePx > 0) {
            gd.setStroke(strokePx, strokeColor);
        }

        Bitmap customBitmap = loadCustomBitmap();
        if (customBitmap != null) {
            BitmapDrawable bd = new BitmapDrawable(getResources(), customBitmap);
            bd.setGravity(Gravity.FILL);
            LayerDrawable layer = new LayerDrawable(new Drawable[]{gd, bd});
            setBackground(layer);
        } else {
            setBackground(gd);
        }
    }

    private Bitmap loadCustomBitmap() {
        String base64 = mProperties.imageBase64 != null ? mProperties.imageBase64 : "";
        if (!base64.isEmpty() && cachedIconBitmap == null) {
            try {
                byte[] decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                cachedIconBitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                return cachedIconBitmap;
            } catch (Throwable ignored) {}
        }
        String path = mProperties.iconPath != null ? mProperties.iconPath : "";
        if (path.isEmpty()) {
            cachedIconPath = "";
            if (base64.isEmpty()) cachedIconBitmap = null;
            return cachedIconBitmap;
        }
        if (path.equals(cachedIconPath) && cachedIconBitmap != null) {
            return cachedIconBitmap;
        }
        File file = new File(path);
        if (file.exists()) {
            cachedIconBitmap = BitmapFactory.decodeFile(path);
            cachedIconPath = path;
            return cachedIconBitmap;
        }
        return cachedIconBitmap;
    }

    private int blendHighlight(int color) {
        int a = Math.min(255, Color.alpha(color) + 75);
        int r = Math.min(255, Color.red(color) + 60);
        int g = Math.min(255, Color.green(color) + 60);
        int b = Math.min(255, Color.blue(color) + 60);
        return Color.argb(a, r, g, b);
    }

    @Override
    public void regenerateDynamicCoordinates() {
        int parentW = mParentLayout.getLayoutWidthPx();
        int parentH = mParentLayout.getLayoutHeightPx();

        float x = mProperties.calculateDynamicX(getContext(), parentW, parentH);
        float y = mProperties.calculateDynamicY(getContext(), parentW, parentH);
        mParentLayout.setManagedViewPosition(this, x, y);
    }

    private final Handler macroHandler = new Handler(Looper.getMainLooper());

    @Override
    public void sendKeyPresses(boolean isDown) {
        int[] targetArray = mProperties.keycodes;
        if (!mProperties.isLayerShiftKey && mParentLayout.isLayerShiftActive() && hasAnyValidCode(mProperties.secondaryKeycodes)) {
            targetArray = mProperties.secondaryKeycodes;
        }
        dispatchKeycodeArray(targetArray, isDown);
    }

    private boolean hasAnyValidCode(int[] arr) {
        if (arr == null) return false;
        for (int kc : arr) {
            if (kc != 0) return true;
        }
        return false;
    }

    private void dispatchKeycodeArray(int[] arr, boolean isDown) {
        if (arr == null) return;
        List<Integer> activeCodes = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();
        boolean hasDuplicate = false;
        for (int kc : arr) {
            if (kc != 0) {
                activeCodes.add(kc);
                if (!seen.add(kc)) {
                    hasDuplicate = true;
                }
            }
        }
        if (activeCodes.isEmpty()) return;

        boolean runSequential = (mProperties.macroSequential || hasDuplicate) && activeCodes.size() > 1;
        if (runSequential) {
            if (!isDown) return;
            macroHandler.removeCallbacksAndMessages(null);
            for (int i = 0; i < activeCodes.size(); i++) {
                final int code = activeCodes.get(i);
                long pressDelay = i * 50L;
                long releaseDelay = pressDelay + 28L;
                macroHandler.postDelayed(() -> InputBridge.sendKey(code, true), pressDelay);
                macroHandler.postDelayed(() -> InputBridge.sendKey(code, false), releaseDelay);
            }
        } else {
            for (int kc : activeCodes) {
                InputBridge.sendKey(kc, isDown);
            }
        }
    }

    @Override
    public void cloneButton() {
        ControlData cloned = mProperties.cloneData();
        cloned.name = cloned.name.isEmpty() ? "Copy" : cloned.name + " Copy";
        float offsetPx = ControlUtils.dpToPx(getContext(), 20f);
        int parentW = Math.max(1, mParentLayout.getLayoutWidthPx());
        int parentH = Math.max(1, mParentLayout.getLayoutHeightPx());
        cloned.setStaticPosition(
                mParentLayout.getManagedViewX(this) + offsetPx,
                mParentLayout.getManagedViewY(this) + offsetPx,
                parentW,
                parentH
        );
        mParentLayout.addControlButton(cloned);
        mParentLayout.notifyLayoutModified();
    }

    @Override
    public void removeButton() {
        mParentLayout.removeControlButton(this);
        mParentLayout.notifyLayoutModified();
    }

    @Override
    public boolean canSnap(ControlInterface target) {
        return target != null && target != this;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mParentLayout.isModifiable()) {
            return handleEditTouch(event);
        }
        return handlePlayTouch(event);
    }

    protected boolean handleEditTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = mParentLayout.getManagedViewX(this);
                startY = mParentLayout.getManagedViewY(this);
                hasMoved = false;
                mParentLayout.selectControl(this, false);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mParentLayout.isPositionsLocked()) {
                    return true;
                }
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!hasMoved && Math.hypot(dx, dy) > touchSlop) {
                    hasMoved = true;
                }
                if (hasMoved) {
                    int parentW = mParentLayout.getLayoutWidthPx();
                    int parentH = mParentLayout.getLayoutHeightPx();
                    float maxX = Math.max(0f, parentW - getWidth());
                    float maxY = Math.max(0f, parentH - getHeight());
                    float[] snapped = mParentLayout.applySnapAndAlignment(this, startX + dx, startY + dy, maxX, maxY);
                    float newX = snapped[0];
                    float newY = snapped[1];
                    mParentLayout.setManagedViewPosition(this, newX, newY);
                    mProperties.setStaticPosition(newX, newY, parentW, parentH);
                    mParentLayout.syncSelectionOverlays();
                }
                return true;

            case MotionEvent.ACTION_UP:
                mParentLayout.clearAlignmentGuides();
                if (!hasMoved) {
                    mParentLayout.selectControl(this, true);
                } else {
                    mParentLayout.notifyLayoutModified();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                mParentLayout.clearAlignmentGuides();
                if (hasMoved) {
                    mParentLayout.notifyLayoutModified();
                }
                return true;
        }
        return true;
    }

    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private int passThruPointerId = MotionEvent.INVALID_POINTER_ID;
    private float passThruDownX = 0f;
    private float passThruDownY = 0f;
    private float passThruLastX = 0f;
    private float passThruLastY = 0f;
    private boolean passThruDragging = false;
    private float scrollAccumY = 0f;
    private float lastScrollY = 0f;
    private boolean dualLongPressTriggered = false;
    private final Runnable dualLongPressRunnable = () -> {
        if (mIsPressed && mProperties.dualActionEnabled) {
            dualLongPressTriggered = true;
            dispatchKeycodeArray(mProperties.secondaryKeycodes, true);
        }
    };

    private float getScreenEventX(MotionEvent ev, int idx) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        return loc[0] + ev.getX(idx);
    }

    private float getScreenEventY(MotionEvent ev, int idx) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        return loc[1] + ev.getY(idx);
    }

    protected boolean handlePlayTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = event.getActionIndex();
                int pid = event.getPointerId(idx);
                float screenX = getScreenEventX(event, idx);
                float screenY = getScreenEventY(event, idx);
                mParentLayout.notifyTouchFeedback(screenX, screenY);
                if (activePointerId == MotionEvent.INVALID_POINTER_ID
                        || event.findPointerIndex(activePointerId) < 0) {
                    activePointerId = pid;

                    // 1. 8-Direction Radial Quick Wheel
                    if (mProperties.isRadialWheel) {
                        mIsPressed = true;
                        applyBackgroundDrawable();
                        mParentLayout.notifyShowRadialWheel(mProperties, screenX, screenY);
                        return true;
                    }

                    // 2. Virtual Mouse Scroll Wheel Strip
                    if (mProperties.isScrollStrip) {
                        mIsPressed = true;
                        applyBackgroundDrawable();
                        lastScrollY = event.getY(idx);
                        scrollAccumY = 0f;
                        return true;
                    }

                    // 3. Quick 90°/180° Turn Button
                    if (mProperties.quickTurnDegrees > 0) {
                        mIsPressed = true;
                        applyBackgroundDrawable();
                        mParentLayout.notifyQuickTurnRequested(mProperties.quickTurnDegrees);
                        return true;
                    }

                    // 4. Layer Shift Modifier (Fn)
                    if (mProperties.isLayerShiftKey) {
                        if (mProperties.isToggle) {
                            mIsToggled = !mIsToggled;
                            mIsPressed = mIsToggled;
                            applyBackgroundDrawable();
                            mParentLayout.setLayerShiftActive(mIsToggled);
                        } else if (!mIsPressed) {
                            mIsPressed = true;
                            applyBackgroundDrawable();
                            mParentLayout.setLayerShiftActive(true);
                        }
                        return true;
                    }

                    // 5. Free-Look Button (unlocks temporary swipe look while pressed)
                    if (mProperties.isFreeLookButton) {
                        mIsPressed = true;
                        applyBackgroundDrawable();
                        mParentLayout.notifyFreeLookChanged(true);
                        sendKeyPresses(true);
                        float px = event.getX(idx);
                        float py = event.getY(idx);
                        passThruPointerId = pid;
                        passThruDownX = px;
                        passThruDownY = py;
                        passThruLastX = px;
                        passThruLastY = py;
                        passThruDragging = false;
                        return true;
                    }

                    // 6. Custom Macro ID binding
                    if (mProperties.macroId != null && !mProperties.macroId.isEmpty()) {
                        mIsPressed = true;
                        applyBackgroundDrawable();
                        com.example.input.MacroEngine.triggerPress(getContext(), mProperties.macroId);
                        return true;
                    }

                    // 7. Dual-Action Button (Tap vs 300ms Hold)
                    if (mProperties.dualActionEnabled) {
                        mIsPressed = true;
                        dualLongPressTriggered = false;
                        applyBackgroundDrawable();
                        macroHandler.removeCallbacks(dualLongPressRunnable);
                        macroHandler.postDelayed(dualLongPressRunnable, 300L);
                        return true;
                    }

                    // 8. Standard / Toggle / Pass-Thru Button
                    if (mProperties.isToggle) {
                        mIsToggled = !mIsToggled;
                        mIsPressed = mIsToggled;
                        applyBackgroundDrawable();
                        sendKeyPresses(mIsToggled);
                    } else if (!mIsPressed) {
                        mIsPressed = true;
                        applyBackgroundDrawable();
                        sendKeyPresses(true);
                    }
                    if (mProperties.passThruEnabled && mParentLayout.isDetachedWindowMode()) {
                        float px = event.getX(idx);
                        float py = event.getY(idx);
                        passThruPointerId = pid;
                        passThruDownX = px;
                        passThruDownY = py;
                        passThruLastX = px;
                        passThruLastY = py;
                        passThruDragging = false;
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mProperties.isRadialWheel && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    int idx = event.findPointerIndex(activePointerId);
                    if (idx >= 0) {
                        mParentLayout.notifyUpdateRadialWheel(getScreenEventX(event, idx), getScreenEventY(event, idx));
                    }
                    return true;
                }

                if (mProperties.isScrollStrip && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    int idx = event.findPointerIndex(activePointerId);
                    if (idx >= 0) {
                        float currY = event.getY(idx);
                        float dy = currY - lastScrollY;
                        lastScrollY = currY;
                        scrollAccumY += dy;
                        float stepPx = ControlUtils.dpToPx(getContext(), 20f);
                        while (scrollAccumY <= -stepPx) {
                            InputBridge.sendMouseScroll(1.0f, 0f);
                            scrollAccumY += stepPx;
                        }
                        while (scrollAccumY >= stepPx) {
                            InputBridge.sendMouseScroll(-1.0f, 0f);
                            scrollAccumY -= stepPx;
                        }
                    }
                    return true;
                }

                if ((mProperties.passThruEnabled || mProperties.isFreeLookButton)
                        && mParentLayout.isDetachedWindowMode()
                        && passThruPointerId != MotionEvent.INVALID_POINTER_ID) {
                    int idx = event.findPointerIndex(passThruPointerId);
                    if (idx >= 0) {
                        float currX = event.getX(idx);
                        float currY = event.getY(idx);
                        if (!passThruDragging) {
                            if (Math.hypot(currX - passThruDownX, currY - passThruDownY) < 3.0) {
                                return true;
                            }
                            passThruDragging = true;
                            passThruLastX = currX;
                            passThruLastY = currY;
                            return true;
                        }
                        float dx = currX - passThruLastX;
                        float dy = currY - passThruLastY;
                        passThruLastX = currX;
                        passThruLastY = currY;
                        if (dx != 0f || dy != 0f) {
                            float sensX = ControlLayoutManager.INSTANCE.getAimSensX(getContext());
                            float sensY = ControlLayoutManager.INSTANCE.getAimSensY(getContext());
                            mParentLayout.notifyPassThruAimDelta(dx * sensX, dy * sensY);
                        }
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int idx = event.getActionIndex();
                int liftedId = event.getPointerId(idx);
                if (liftedId == passThruPointerId) {
                    passThruPointerId = MotionEvent.INVALID_POINTER_ID;
                    passThruDragging = false;
                }
                if (liftedId == activePointerId) {
                    int replacementIdx = -1;
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (i != idx) {
                            replacementIdx = i;
                            break;
                        }
                    }
                    if (replacementIdx >= 0) {
                        activePointerId = event.getPointerId(replacementIdx);
                        if ((mProperties.passThruEnabled || mProperties.isFreeLookButton) && mParentLayout.isDetachedWindowMode()) {
                            float px = event.getX(replacementIdx);
                            float py = event.getY(replacementIdx);
                            passThruPointerId = activePointerId;
                            passThruDownX = px;
                            passThruDownY = py;
                            passThruLastX = px;
                            passThruLastY = py;
                            passThruDragging = false;
                        }
                    } else {
                        finishPlayRelease();
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishPlayRelease();
                return true;
        }
        return true;
    }

    private void finishPlayRelease() {
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        passThruPointerId = MotionEvent.INVALID_POINTER_ID;
        passThruDragging = false;
        mParentLayout.notifyVirtualMouseTouchActivity();

        if (mProperties.isRadialWheel) {
            mIsPressed = false;
            applyBackgroundDrawable();
            mParentLayout.notifyReleaseRadialWheel();
            return;
        }
        if (mProperties.isScrollStrip || mProperties.quickTurnDegrees > 0) {
            mIsPressed = false;
            applyBackgroundDrawable();
            return;
        }
        if (mProperties.isLayerShiftKey) {
            if (!mProperties.isToggle && mIsPressed) {
                mIsPressed = false;
                applyBackgroundDrawable();
                mParentLayout.setLayerShiftActive(false);
            }
            return;
        }
        if (mProperties.isFreeLookButton) {
            if (mIsPressed) {
                mIsPressed = false;
                applyBackgroundDrawable();
                sendKeyPresses(false);
                mParentLayout.notifyFreeLookChanged(false);
            }
            return;
        }
        if (mProperties.macroId != null && !mProperties.macroId.isEmpty()) {
            if (mIsPressed) {
                mIsPressed = false;
                applyBackgroundDrawable();
                com.example.input.MacroEngine.triggerRelease(getContext(), mProperties.macroId);
            }
            return;
        }
        if (mProperties.dualActionEnabled) {
            macroHandler.removeCallbacks(dualLongPressRunnable);
            if (mIsPressed) {
                mIsPressed = false;
                applyBackgroundDrawable();
                if (dualLongPressTriggered) {
                    dualLongPressTriggered = false;
                    dispatchKeycodeArray(mProperties.secondaryKeycodes, false);
                } else {
                    dispatchKeycodeArray(mProperties.keycodes, true);
                    macroHandler.postDelayed(() -> dispatchKeycodeArray(mProperties.keycodes, false), 35L);
                }
            }
            return;
        }
        if (!mProperties.isToggle && mIsPressed) {
            mIsPressed = false;
            applyBackgroundDrawable();
            sendKeyPresses(false);
        }
    }

    public void releaseIfPressed() {
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        passThruPointerId = MotionEvent.INVALID_POINTER_ID;
        passThruDragging = false;
        macroHandler.removeCallbacks(dualLongPressRunnable);
        if (mProperties.isRadialWheel) {
            mParentLayout.notifyReleaseRadialWheel();
        }
        if (mProperties.isLayerShiftKey && (mIsPressed || mIsToggled)) {
            mIsPressed = false;
            mIsToggled = false;
            applyBackgroundDrawable();
            mParentLayout.setLayerShiftActive(false);
            return;
        }
        if (mProperties.isFreeLookButton && mIsPressed) {
            mParentLayout.notifyFreeLookChanged(false);
        }
        if (mProperties.dualActionEnabled && dualLongPressTriggered) {
            dualLongPressTriggered = false;
            dispatchKeycodeArray(mProperties.secondaryKeycodes, false);
        }
        if (mIsPressed || mIsToggled) {
            mIsPressed = false;
            mIsToggled = false;
            applyBackgroundDrawable();
            if (mProperties.macroId != null && !mProperties.macroId.isEmpty()) {
                com.example.input.MacroEngine.triggerRelease(getContext(), mProperties.macroId);
            } else {
                sendKeyPresses(false);
            }
        }
    }
}
