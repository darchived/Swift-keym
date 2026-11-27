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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;

import com.example.customcontrols.utils.ControlUtils;
import com.example.input.InputBridge;

import java.io.File;

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

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(wPx, hPx);
        } else {
            lp.width = wPx;
            lp.height = hPx;
        }
        setLayoutParams(lp);

        String label = mProperties.name != null ? mProperties.name.trim() : "";
        setText(label);

        setAlpha(ControlUtils.clamp(mProperties.opacity, 0.1f, 1.0f));
        applyBackgroundDrawable();
        regenerateDynamicCoordinates();
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
        int parentW = mParentLayout.getWidth();
        int parentH = mParentLayout.getHeight();
        if (parentW <= 0) parentW = ControlUtils.getScreenWidth(getContext());
        if (parentH <= 0) parentH = ControlUtils.getScreenHeight(getContext());

        float x = mProperties.calculateDynamicX(getContext(), parentW, parentH);
        float y = mProperties.calculateDynamicY(getContext(), parentW, parentH);
        setX(x);
        setY(y);
    }

    @Override
    public void sendKeyPresses(boolean isDown) {
        if (mProperties.keycodes == null) return;
        for (int kc : mProperties.keycodes) {
            if (kc != 0) {
                InputBridge.sendKey(kc, isDown);
            }
        }
    }

    @Override
    public void cloneButton() {
        ControlData cloned = mProperties.cloneData();
        cloned.name = cloned.name.isEmpty() ? "Copy" : cloned.name + " Copy";
        float offsetPx = ControlUtils.dpToPx(getContext(), 20f);
        int parentW = Math.max(1, mParentLayout.getWidth());
        int parentH = Math.max(1, mParentLayout.getHeight());
        cloned.setStaticPosition(getX() + offsetPx, getY() + offsetPx, parentW, parentH);
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
                startX = getX();
                startY = getY();
                hasMoved = false;
                mParentLayout.selectControl(this, false);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!hasMoved && Math.hypot(dx, dy) > touchSlop) {
                    hasMoved = true;
                }
                if (hasMoved) {
                    float maxX = Math.max(0f, mParentLayout.getWidth() - getWidth());
                    float maxY = Math.max(0f, mParentLayout.getHeight() - getHeight());
                    float newX = ControlUtils.clamp(startX + dx, 0f, maxX);
                    float newY = ControlUtils.clamp(startY + dy, 0f, maxY);
                    setX(newX);
                    setY(newY);
                    mProperties.setStaticPosition(newX, newY, mParentLayout.getWidth(), mParentLayout.getHeight());
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
                return true;
        }
        return true;
    }

    protected boolean handlePlayTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mProperties.isToggle) {
                    mIsToggled = !mIsToggled;
                    mIsPressed = mIsToggled;
                    applyBackgroundDrawable();
                    sendKeyPresses(mIsToggled);
                } else {
                    if (!mIsPressed) {
                        mIsPressed = true;
                        applyBackgroundDrawable();
                        sendKeyPresses(true);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
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
        if (mIsPressed || mIsToggled) {
            mIsPressed = false;
            mIsToggled = false;
            applyBackgroundDrawable();
            sendKeyPresses(false);
        }
    }
}
