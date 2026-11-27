package com.example.customcontrols.handleview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.example.customcontrols.ControlData;
import com.example.customcontrols.ControlInterface;
import com.example.customcontrols.ControlLayout;
import com.example.customcontrols.utils.ControlUtils;

/**
 * Draggable resize grip rendered at the bottom-right corner of the selected control in Edit Mode.
 */
@SuppressLint("ViewConstructor")
public class ControlHandleView extends View {

    private final ControlLayout parentLayout;
    private ControlInterface targetControl;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int handleSizePx;

    private float downRawX;
    private float downRawY;
    private float startWidthDp;
    private float startHeightDp;

    public ControlHandleView(Context context, ControlLayout parentLayout) {
        super(context);
        this.parentLayout = parentLayout;
        this.handleSizePx = (int) ControlUtils.dpToPx(context, 28f);

        fillPaint.setColor(0xFFFFFFFF);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(0xFF1E1E1E);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(ControlUtils.dpToPx(context, 2.5f));

        setLayoutParams(new FrameLayout.LayoutParams(handleSizePx, handleSizePx));
        setVisibility(GONE);
        setElevation(ControlUtils.dpToPx(context, 12f));
    }

    public void attachTo(ControlInterface control) {
        this.targetControl = control;
        if (control == null || !parentLayout.isModifiable()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        bringToFront();
        syncPosition();
    }

    public void syncPosition() {
        if (targetControl == null) return;
        View targetView = targetControl.getControlView();
        float x = targetView.getX() + targetView.getWidth() - (handleSizePx / 2f);
        float y = targetView.getY() + targetView.getHeight() - (handleSizePx / 2f);
        setX(ControlUtils.clamp(x, 0f, Math.max(0f, parentLayout.getWidth() - handleSizePx)));
        setY(ControlUtils.clamp(y, 0f, Math.max(0f, parentLayout.getHeight() - handleSizePx)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = (Math.min(getWidth(), getHeight()) / 2f) - ControlUtils.dpToPx(getContext(), 2f);
        canvas.drawCircle(cx, cy, radius, fillPaint);
        canvas.drawCircle(cx, cy, radius, strokePaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (targetControl == null || !parentLayout.isModifiable()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                ControlData data = targetControl.getProperties();
                startWidthDp = data.width;
                startHeightDp = data.height;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dxPx = event.getRawX() - downRawX;
                float dyPx = event.getRawY() - downRawY;
                float dxDp = ControlUtils.pxToDp(getContext(), dxPx);
                float dyDp = ControlUtils.pxToDp(getContext(), dyPx);

                ControlData props = targetControl.getProperties();
                props.width = ControlUtils.clamp(startWidthDp + dxDp, 28f, 360f);
                props.height = ControlUtils.clamp(startHeightDp + dyDp, 28f, 360f);
                targetControl.updateProperties();
                post(this::syncPosition);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                parentLayout.notifyLayoutModified();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
