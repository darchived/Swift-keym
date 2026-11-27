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

        String label = mProperties.name != null ? mProperties.name.trim() : "";
        setText(label);

        applyEffectiveOpacity();
        applyBackgroundDrawable();
        regenerateDynamicCoordinates();
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
            bd.setGravity(Gravity.CENTER);
            LayerDrawable layer = new LayerDrawable(new Drawable[]{gd, bd});
            int inset = (int) ControlUtils.dpToPx(getContext(), 6f);
            layer.setLayerInset(1, inset, inset, inset, inset);
            setBackground(layer);
        } else {
            setBackground(gd);
        }
    }

    private Bitmap loadCustomBitmap() {
        String path = mProperties.iconPath != null ? mProperties.iconPath : "";
        if (path.isEmpty()) {
            cachedIconPath = "";
            cachedIconBitmap = null;
            return null;
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
        return null;
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
        if (mProperties.keycodes == null) return;
        List<Integer> activeCodes = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();
        boolean hasDuplicate = false;
        for (int kc : mProperties.keycodes) {
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
                    float newX = ControlUtils.clamp(startX + dx, 0f, maxX);
                    float newY = ControlUtils.clamp(startY + dy, 0f, maxY);
                    mParentLayout.setManagedViewPosition(this, newX, newY);
                    mProperties.setStaticPosition(newX, newY, parentW, parentH);
                    mParentLayout.syncSelectionOverlays();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!hasMoved) {
                    mParentLayout.selectControl(this, true);
                } else {
                    mParentLayout.notifyLayoutModified();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (hasMoved) {
                    mParentLayout.notifyLayoutModified();
                }
                return true;
        }
        return true;
    }

    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private int passThruPointerId = MotionEvent.INVALID_POINTER_ID;
    private float passThruLastX = 0f;
    private float passThruLastY = 0f;

    protected boolean handlePlayTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = event.getActionIndex();
                int pid = event.getPointerId(idx);
                int[] loc = new int[2];
                getLocationOnScreen(loc);
                mParentLayout.notifyTouchFeedback(loc[0] + event.getX(idx), loc[1] + event.getY(idx));
                if (activePointerId == MotionEvent.INVALID_POINTER_ID
                        || event.findPointerIndex(activePointerId) < 0) {
                    activePointerId = pid;
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
                        passThruPointerId = pid;
                        passThruLastX = event.getX(idx);
                        passThruLastY = event.getY(idx);
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mProperties.passThruEnabled && mParentLayout.isDetachedWindowMode()
                        && passThruPointerId != MotionEvent.INVALID_POINTER_ID) {
                    int idx = event.findPointerIndex(passThruPointerId);
                    if (idx >= 0) {
                        float currX = event.getX(idx);
                        float currY = event.getY(idx);
                        float dx = currX - passThruLastX;
                        float dy = currY - passThruLastY;
                        passThruLastX = currX;
                        passThruLastY = currY;
                        if (dx != 0f || dy != 0f) {
                            float sens = mParentLayout.getAimSensitivity();
                            InputBridge.sendMouseMove(dx * sens, dy * sens);
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
                        if (mProperties.passThruEnabled && mParentLayout.isDetachedWindowMode()) {
                            passThruPointerId = activePointerId;
                            passThruLastX = event.getX(replacementIdx);
                            passThruLastY = event.getY(replacementIdx);
                        }
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID;
                        if (!mProperties.isToggle && mIsPressed) {
                            mIsPressed = false;
                            applyBackgroundDrawable();
                            sendKeyPresses(false);
                        }
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                passThruPointerId = MotionEvent.INVALID_POINTER_ID;
                if (!mProperties.isToggle && mIsPressed) {
                    mIsPressed = false;
                    applyBackgroundDrawable();
                    sendKeyPresses(false);
                }
                return true;
        }
        return true;
    }

    public void releaseIfPressed() {
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        passThruPointerId = MotionEvent.INVALID_POINTER_ID;
        if (mIsPressed || mIsToggled) {
            mIsPressed = false;
            mIsToggled = false;
            applyBackgroundDrawable();
            sendKeyPresses(false);
        }
    }
}
