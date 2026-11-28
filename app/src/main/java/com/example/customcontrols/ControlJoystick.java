package com.example.customcontrols;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.example.customcontrols.utils.ControlUtils;
import com.example.input.InputBridge;
import com.example.input.LwjglGlfwKeycode;

/**
 * Analog WASD virtual joystick adapted from MojoLauncher's ControlJoystick.
 * Supports multi-touch pointer isolation, dynamic origin or fixed center, and Forward Sprint Lock.
 */
@SuppressLint("ViewConstructor")
public class ControlJoystick extends View implements ControlInterface {

    private final ControlLayout mParentLayout;
    private final ControlJoystickData mProperties;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float originX;
    private float originY;
    private float knobX;
    private float knobY;
    private boolean isTracking = false;
    private boolean sprintLocked = false;

    private boolean keyW = false;
    private boolean keyA = false;
    private boolean keyS = false;
    private boolean keyD = false;
    private boolean keyShift = false;

    private float downRawX;
    private float downRawY;
    private float startX;
    private float startY;
    private boolean hasMoved;
    private final int touchSlop;

    public ControlJoystick(Context context, ControlLayout parentLayout, ControlJoystickData properties) {
        super(context);
        this.mParentLayout = parentLayout;
        this.mProperties = properties != null ? properties : new ControlJoystickData();
        this.mProperties.ensureValid();
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        bgPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        knobPaint.setStyle(Paint.Style.FILL);
        lockPaint.setStyle(Paint.Style.STROKE);
        lockPaint.setColor(0xCCFFB300);
        lockPaint.setStrokeWidth(ControlUtils.dpToPx(context, 2f));

        updateProperties();
    }

    @Override
    public View getControlView() {
        return this;
    }

    @Override
    public ControlJoystickData getProperties() {
        return mProperties;
    }

    @Override
    public void updateProperties() {
        mProperties.ensureValid();
        int wPx = (int) Math.max(ControlUtils.dpToPx(getContext(), 72f), mProperties.getWidthPx(getContext()));
        int hPx = (int) Math.max(ControlUtils.dpToPx(getContext(), 72f), mProperties.getHeightPx(getContext()));

        mParentLayout.resizeManagedView(this, wPx, hPx);
        applyEffectiveOpacity();

        bgPaint.setColor(mProperties.bgColor);
        strokePaint.setColor(mProperties.strokeColor);
        strokePaint.setStrokeWidth(ControlUtils.dpToPx(getContext(), Math.max(1, mProperties.strokeWidth)));
        knobPaint.setColor(mProperties.strokeColor);

        originX = wPx / 2f;
        originY = hPx / 2f;
        knobX = originX;
        knobY = originY;

        regenerateDynamicCoordinates();
        invalidate();
    }

    public void applyEffectiveOpacity() {
        float effective = mProperties.opacity * mParentLayout.getGlobalOpacity();
        setAlpha(ControlUtils.clamp(effective, 0.08f, 1.0f));
    }

    @Override
    public void regenerateDynamicCoordinates() {
        int parentW = mParentLayout.getLayoutWidthPx();
        int parentH = mParentLayout.getLayoutHeightPx();

        float x = mProperties.calculateDynamicX(getContext(), parentW, parentH);
        float y = mProperties.calculateDynamicY(getContext(), parentW, parentH);
        mParentLayout.setManagedViewPosition(this, x, y);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        originX = w / 2f;
        originY = h / 2f;
        if (!isTracking && !sprintLocked) {
            knobX = originX;
            knobY = originY;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float radius = Math.min(w, h) * 0.38f;
        float cx = w / 2f;
        float cy = h / 2f;

        // Outer ring
        canvas.drawCircle(cx, cy, radius, bgPaint);
        canvas.drawCircle(cx, cy, radius, strokePaint);

        // Sprint lock indicator at top edge
        if (mProperties.forwardLock && (isTracking || sprintLocked)) {
            float lockY = Math.max(ControlUtils.dpToPx(getContext(), 10f), cy - radius * 1.05f);
            float lockR = ControlUtils.dpToPx(getContext(), 11f);
            lockPaint.setColor(sprintLocked ? 0xFFFFFFFF : 0xAAE0E0E0);
            canvas.drawCircle(cx, lockY, lockR, lockPaint);
            if (sprintLocked) {
                Paint txtP = new Paint(Paint.ANTI_ALIAS_FLAG);
                txtP.setColor(0xFFFFFFFF);
                txtP.setTextSize(ControlUtils.dpToPx(getContext(), 9f));
                txtP.setTextAlign(Paint.Align.CENTER);
                txtP.setFakeBoldText(true);
                canvas.drawText("SPRINT", cx, lockY + ControlUtils.dpToPx(getContext(), 20f), txtP);
            }
        }

        // Thumbstick knob
        float knobRadius = radius * 0.42f;
        knobPaint.setAlpha(isTracking || sprintLocked ? 230 : 165);
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mParentLayout.isModifiable()) {
            return handleEditTouch(event);
        }
        return handlePlayTouch(event);
    }

    private boolean handleEditTouch(MotionEvent event) {
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
            case MotionEvent.ACTION_CANCEL:
                mParentLayout.clearAlignmentGuides();
                if (!hasMoved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    mParentLayout.selectControl(this, true);
                } else {
                    mParentLayout.notifyLayoutModified();
                }
                return true;
        }
        return true;
    }

    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    private boolean handlePlayTouch(MotionEvent event) {
        float radius = Math.min(getWidth(), getHeight()) * 0.38f;
        if (radius <= 1f) radius = 60f;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = event.getActionIndex();
                int[] loc = new int[2];
                getLocationOnScreen(loc);
                mParentLayout.notifyTouchFeedback(loc[0] + event.getX(idx), loc[1] + event.getY(idx));
                if (activePointerId == MotionEvent.INVALID_POINTER_ID
                        || event.findPointerIndex(activePointerId) < 0) {
                    activePointerId = event.getPointerId(idx);
                    isTracking = true;
                    if (sprintLocked) {
                        sprintLocked = false;
                    }
                    float px = event.getX(idx);
                    float py = event.getY(idx);
                    if (mProperties.absolute) {
                        originX = getWidth() / 2f;
                        originY = getHeight() / 2f;
                    } else {
                        originX = ControlUtils.clamp(px, radius * 0.5f, getWidth() - radius * 0.5f);
                        originY = ControlUtils.clamp(py, radius * 0.5f, getHeight() - radius * 0.5f);
                    }
                    updateStickPosition(px, py, radius);
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (isTracking && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    int idx = event.findPointerIndex(activePointerId);
                    if (idx >= 0) {
                        updateStickPosition(event.getX(idx), event.getY(idx), radius);
                        invalidate();
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int idx = event.getActionIndex();
                int liftedId = event.getPointerId(idx);
                if (liftedId == activePointerId) {
                    float releaseY = event.getY(idx);
                    int replacementIdx = -1;
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (i != idx) {
                            replacementIdx = i;
                            break;
                        }
                    }
                    if (replacementIdx >= 0) {
                        activePointerId = event.getPointerId(replacementIdx);
                        updateStickPosition(event.getX(replacementIdx), event.getY(replacementIdx), radius);
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID;
                        finishJoystickTracking(releaseY, radius);
                    }
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int idx = event.getActionIndex();
                float releaseY = (idx >= 0 && idx < event.getPointerCount()) ? event.getY(idx) : event.getY();
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                finishJoystickTracking(releaseY, radius);
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void finishJoystickTracking(float releaseY, float radius) {
        isTracking = false;
        if (mProperties.forwardLock && (releaseY - originY) < -radius * 1.15f) {
            sprintLocked = true;
            knobX = getWidth() / 2f;
            knobY = Math.max(ControlUtils.dpToPx(getContext(), 10f), getHeight() / 2f - radius * 1.15f);
            applyDirectionalKeys(true, false, false, false, true);
        } else {
            sprintLocked = false;
            knobX = getWidth() / 2f;
            knobY = getHeight() / 2f;
            sendKeyPresses(false);
        }
    }

    private void updateStickPosition(float x, float y, float radius) {
        float dx = x - originX;
        float dy = y - originY;
        float dist = (float) Math.hypot(dx, dy);

        float clampedDist = Math.min(dist, radius);
        float angle = (float) Math.atan2(dy, dx);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        knobX = cx + clampedDist * (float) Math.cos(angle);
        knobY = cy + clampedDist * (float) Math.sin(angle);

        float normX = dx / radius;
        float normY = dy / radius;
        float deadzone = 0.22f;

        boolean nextW = normY < -deadzone;
        boolean nextS = normY > deadzone;
        boolean nextA = normX < -deadzone;
        boolean nextD = normX > deadzone;
        boolean nextShift = mProperties.forwardLock && normY < -1.15f;

        applyDirectionalKeys(nextW, nextA, nextS, nextD, nextShift);
    }

    private void applyDirectionalKeys(boolean w, boolean a, boolean s, boolean d, boolean shift) {
        if (w != keyW) {
            keyW = w;
            InputBridge.sendKey(LwjglGlfwKeycode.GLFW_KEY_W, w);
        }
        if (a != keyA) {
            keyA = a;
            InputBridge.sendKey(LwjglGlfwKeycode.GLFW_KEY_A, a);
        }
        if (s != keyS) {
            keyS = s;
            InputBridge.sendKey(LwjglGlfwKeycode.GLFW_KEY_S, s);
        }
        if (d != keyD) {
            keyD = d;
            InputBridge.sendKey(LwjglGlfwKeycode.GLFW_KEY_D, d);
        }
        if (shift != keyShift) {
            keyShift = shift;
            InputBridge.sendKey(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, shift);
        }
    }

    @Override
    public void sendKeyPresses(boolean isDown) {
        if (!isDown) {
            sprintLocked = false;
            isTracking = false;
            knobX = getWidth() / 2f;
            knobY = getHeight() / 2f;
            applyDirectionalKeys(false, false, false, false, false);
            invalidate();
        }
    }

    @Override
    public void cloneButton() {
        ControlJoystickData cloned = new ControlJoystickData(mProperties);
        cloned.name = cloned.name + " Copy";
        float offsetPx = ControlUtils.dpToPx(getContext(), 24f);
        cloned.setStaticPosition(
                mParentLayout.getManagedViewX(this) + offsetPx,
                mParentLayout.getManagedViewY(this) + offsetPx,
                Math.max(1, mParentLayout.getLayoutWidthPx()),
                Math.max(1, mParentLayout.getLayoutHeightPx())
        );
        mParentLayout.addControlJoystick(cloned);
        mParentLayout.notifyLayoutModified();
    }

    @Override
    public void removeButton() {
        sendKeyPresses(false);
        mParentLayout.removeControlJoystick(this);
        mParentLayout.notifyLayoutModified();
    }

    @Override
    public boolean canSnap(ControlInterface target) {
        return false;
    }
}
