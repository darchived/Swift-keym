package com.example.customcontrols;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.example.customcontrols.handleview.ControlHandleView;
import com.example.customcontrols.utils.ControlUtils;
import com.example.input.InputBridge;

import java.util.ArrayList;

/**
 * Swift KeyM ControlLayout adapted from MojoLauncher's CustomControls container.
 * Features Edit Mode floating DELETE/CLONE + resize handle and Play Mode multi-touch Swipe-to-Aim.
 */
public class ControlLayout extends FrameLayout {

    public interface OnLayoutModifiedListener {
        void onLayoutModified(CustomControls controls);
    }

    public interface OnHistoryChangedListener {
        void onHistoryChanged(boolean canUndo, boolean canRedo, boolean hasUnsavedChanges);
    }

    public interface ImagePickCallback {
        void onImageSaved(String savedPath);
    }

    public interface OnImagePickRequestListener {
        void onRequestImagePick(ImagePickCallback callback);
    }

    private static final int MAX_HISTORY = 50;

    private CustomControls mLayout = new CustomControls();
    private boolean mModifiable = false;
    private boolean mIsOverlayWindow = true;
    private float mAimSensitivity = 1.2f;

    private final ArrayList<ControlButton> mButtons = new ArrayList<>();
    private final ArrayList<ControlDrawer> mDrawers = new ArrayList<>();
    private final ArrayList<ControlJoystick> mJoysticks = new ArrayList<>();

    private ControlHandleView mResizeHandle;
    private LinearLayout mQuickActionBar;
    private ControlInterface mSelectedControl;
    private EditControlSideDialog mActiveEditDialog;
    private OnLayoutModifiedListener mModifiedListener;
    private OnHistoryChangedListener mHistoryListener;
    private OnImagePickRequestListener mImagePickListener;

    // Undo / Redo & Unsaved Tracking
    private final ArrayList<String> mUndoStack = new ArrayList<>();
    private final ArrayList<String> mRedoStack = new ArrayList<>();
    private String mCurrentSnapshotJson = "";
    private String mLastSavedJson = "";

    // Multi-touch Play Mode state
    private int mAimPointerId = MotionEvent.INVALID_POINTER_ID;
    private float mLastAimX = 0f;
    private float mLastAimY = 0f;
    private final SparseArray<View> mActivePointerTargets = new SparseArray<>();

    private final Paint mEditGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ControlLayout(Context context) {
        this(context, null);
    }

    public ControlLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        mEditGridPaint.setColor(0x1EFFFFFF);
        mEditGridPaint.setStrokeWidth(1f);

        mResizeHandle = new ControlHandleView(context, this);
        addView(mResizeHandle);

        buildQuickActionBar(context);
    }

    private void buildQuickActionBar(Context context) {
        mQuickActionBar = new LinearLayout(context);
        mQuickActionBar.setOrientation(LinearLayout.HORIZONTAL);
        mQuickActionBar.setVisibility(GONE);
        mQuickActionBar.setElevation(ControlUtils.dpToPx(context, 14f));

        int h = (int) ControlUtils.dpToPx(context, 32f);
        int gap = (int) ControlUtils.dpToPx(context, 6f);

        Button deleteBtn = createQuickActionButton(context, "DELETE");
        deleteBtn.setOnClickListener(v -> {
            if (mSelectedControl != null) {
                if (mActiveEditDialog != null) mActiveEditDialog.dismiss();
                mSelectedControl.removeButton();
            }
        });

        Button cloneBtn = createQuickActionButton(context, "CLONE");
        cloneBtn.setOnClickListener(v -> {
            if (mSelectedControl != null) {
                if (mActiveEditDialog != null) mActiveEditDialog.dismiss();
                mSelectedControl.cloneButton();
            }
        });

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, h);
        lp1.setMarginEnd(gap);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, h);

        mQuickActionBar.addView(deleteBtn, lp1);
        mQuickActionBar.addView(cloneBtn, lp2);
        addView(mQuickActionBar, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        ));
    }

    private Button createQuickActionButton(Context context, String label) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setTextSize(11f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setStateListAnimator(null);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        int padH = (int) ControlUtils.dpToPx(context, 12f);
        btn.setPadding(padH, 2, padH, 2);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xDD4A4A4A);
        gd.setStroke(1, 0x80FFFFFF);
        gd.setCornerRadius(ControlUtils.dpToPx(context, 4f));
        btn.setBackground(gd);
        return btn;
    }

    public void setOverlayWindowMode(boolean isOverlay) {
        this.mIsOverlayWindow = isOverlay;
    }

    public void setImagePickListener(OnImagePickRequestListener listener) {
        this.mImagePickListener = listener;
    }

    public OnImagePickRequestListener getImagePickListener() {
        return mImagePickListener;
    }

    public void setAimSensitivity(float sensitivity) {
        this.mAimSensitivity = ControlUtils.clamp(sensitivity, 0.2f, 5.0f);
    }

    public float getAimSensitivity() {
        return mAimSensitivity;
    }

    public void setOnLayoutModifiedListener(OnLayoutModifiedListener listener) {
        this.mModifiedListener = listener;
    }

    public void setOnHistoryChangedListener(OnHistoryChangedListener listener) {
        this.mHistoryListener = listener;
        dispatchHistoryChanged();
    }

    public void notifyLayoutModified() {
        if (mLayout == null) return;
        String nextJson = mLayout.toJson();
        if (!nextJson.equals(mCurrentSnapshotJson)) {
            if (!mCurrentSnapshotJson.isEmpty()) {
                mUndoStack.add(mCurrentSnapshotJson);
                if (mUndoStack.size() > MAX_HISTORY) {
                    mUndoStack.remove(0);
                }
            }
            mRedoStack.clear();
            mCurrentSnapshotJson = nextJson;
            dispatchHistoryChanged();
        }
        if (mModifiedListener != null) {
            mModifiedListener.onLayoutModified(mLayout);
        }
    }

    public void markSaved() {
        if (mLayout != null) {
            mLastSavedJson = mLayout.toJson();
            mCurrentSnapshotJson = mLastSavedJson;
            dispatchHistoryChanged();
        }
    }

    public boolean hasUnsavedChanges() {
        if (mLayout == null) return false;
        return !mLayout.toJson().equals(mLastSavedJson);
    }

    public boolean canUndo() {
        return !mUndoStack.isEmpty();
    }

    public boolean canRedo() {
        return !mRedoStack.isEmpty();
    }

    public boolean undo() {
        if (mUndoStack.isEmpty()) return false;
        if (mActiveEditDialog != null) {
            mActiveEditDialog.dismiss();
            mActiveEditDialog = null;
        }
        String current = mLayout != null ? mLayout.toJson() : mCurrentSnapshotJson;
        mRedoStack.add(current);
        String previous = mUndoStack.remove(mUndoStack.size() - 1);
        restoreSnapshot(previous);
        return true;
    }

    public boolean redo() {
        if (mRedoStack.isEmpty()) return false;
        if (mActiveEditDialog != null) {
            mActiveEditDialog.dismiss();
            mActiveEditDialog = null;
        }
        String current = mLayout != null ? mLayout.toJson() : mCurrentSnapshotJson;
        mUndoStack.add(current);
        String next = mRedoStack.remove(mRedoStack.size() - 1);
        restoreSnapshot(next);
        return true;
    }

    private void dispatchHistoryChanged() {
        if (mHistoryListener != null) {
            mHistoryListener.onHistoryChanged(canUndo(), canRedo(), hasUnsavedChanges());
        }
    }

    public boolean isModifiable() {
        return mModifiable;
    }

    public void setModifiable(boolean modifiable) {
        if (this.mModifiable == modifiable) return;
        this.mModifiable = modifiable;

        if (modifiable) {
            releaseAllActiveTouches();
            InputBridge.resetAll();
        } else {
            selectControl(null, false);
            if (mActiveEditDialog != null) {
                mActiveEditDialog.dismiss();
                mActiveEditDialog = null;
            }
        }

        for (ControlDrawer drawer : mDrawers) {
            drawer.syncButtons();
        }
        invalidate();
    }

    public CustomControls getLayout() {
        return mLayout;
    }

    public void loadLayout(CustomControls controls) {
        mUndoStack.clear();
        mRedoStack.clear();
        applyControlsInternal(controls);
        mCurrentSnapshotJson = mLayout.toJson();
        mLastSavedJson = mCurrentSnapshotJson;
        dispatchHistoryChanged();
    }

    private void restoreSnapshot(String json) {
        CustomControls parsed = CustomControls.fromJson(json);
        applyControlsInternal(parsed);
        mCurrentSnapshotJson = mLayout.toJson();
        dispatchHistoryChanged();
        if (mModifiedListener != null) {
            mModifiedListener.onLayoutModified(mLayout);
        }
    }

    private void applyControlsInternal(CustomControls controls) {
        clearControls();
        mLayout = controls != null ? controls : new CustomControls();
        mLayout.ensureValid();

        for (ControlData data : mLayout.mControlDataList) {
            ControlButton btn = new ControlButton(getContext(), this, data);
            mButtons.add(btn);
            addView(btn);
        }
        for (ControlDrawerData drawerData : mLayout.mDrawerDataList) {
            ControlDrawer drawer = new ControlDrawer(getContext(), this, drawerData);
            mDrawers.add(drawer);
            addView(drawer);
            drawer.attachSubButtonsToParent();
        }
        for (ControlJoystickData joyData : mLayout.mJoystickDataList) {
            ControlJoystick joy = new ControlJoystick(getContext(), this, joyData);
            mJoysticks.add(joy);
            addView(joy);
        }

        bringOverlaysToFront();
        invalidate();
    }

    private void bringOverlaysToFront() {
        if (mResizeHandle != null) mResizeHandle.bringToFront();
        if (mQuickActionBar != null) mQuickActionBar.bringToFront();
    }

    private void clearControls() {
        releaseAllActiveTouches();
        for (ControlButton btn : mButtons) {
            removeView(btn);
        }
        mButtons.clear();

        for (ControlDrawer drawer : mDrawers) {
            drawer.detachSubButtonsFromParent();
            removeView(drawer);
        }
        mDrawers.clear();

        for (ControlJoystick joy : mJoysticks) {
            removeView(joy);
        }
        mJoysticks.clear();

        selectControl(null, false);
    }

    public void addControlButton(ControlData data) {
        if (data == null) data = new ControlData();
        data.ensureValid();
        mLayout.mControlDataList.add(data);
        ControlButton btn = new ControlButton(getContext(), this, data);
        mButtons.add(btn);
        addView(btn);
        bringOverlaysToFront();
        if (mModifiable) {
            selectControl(btn, false);
        }
    }

    public void removeControlButton(ControlButton btn) {
        btn.releaseIfPressed();
        mButtons.remove(btn);
        mLayout.mControlDataList.remove(btn.getProperties());
        removeView(btn);
        if (mSelectedControl == btn) {
            selectControl(null, false);
        }
    }

    public void addControlDrawer(ControlDrawerData drawerData) {
        if (drawerData == null) drawerData = new ControlDrawerData();
        drawerData.ensureValid();
        mLayout.mDrawerDataList.add(drawerData);
        ControlDrawer drawer = new ControlDrawer(getContext(), this, drawerData);
        mDrawers.add(drawer);
        addView(drawer);
        drawer.attachSubButtonsToParent();
        bringOverlaysToFront();
        if (mModifiable) {
            selectControl(drawer, false);
        }
    }

    public void removeControlDrawer(ControlDrawer drawer) {
        mDrawers.remove(drawer);
        mLayout.mDrawerDataList.remove(drawer.getDrawerData());
        removeView(drawer);
        if (mSelectedControl == drawer) {
            selectControl(null, false);
        }
    }

    public void addControlJoystick(ControlJoystickData joyData) {
        if (joyData == null) joyData = new ControlJoystickData();
        joyData.ensureValid();
        mLayout.mJoystickDataList.add(joyData);
        ControlJoystick joy = new ControlJoystick(getContext(), this, joyData);
        mJoysticks.add(joy);
        addView(joy);
        bringOverlaysToFront();
        if (mModifiable) {
            selectControl(joy, false);
        }
    }

    public void removeControlJoystick(ControlJoystick joy) {
        joy.sendKeyPresses(false);
        mJoysticks.remove(joy);
        mLayout.mJoystickDataList.remove(joy.getProperties());
        removeView(joy);
        if (mSelectedControl == joy) {
            selectControl(null, false);
        }
    }

    public void selectControl(ControlInterface control, boolean openEditor) {
        mSelectedControl = control;
        if (mResizeHandle != null) {
            mResizeHandle.attachTo(control);
        }
        if (mQuickActionBar != null) {
            if (control != null && mModifiable) {
                mQuickActionBar.setVisibility(VISIBLE);
                syncSelectionOverlays();
            } else {
                mQuickActionBar.setVisibility(GONE);
            }
        }
        bringOverlaysToFront();

        if (openEditor && control != null && mModifiable) {
            if (mActiveEditDialog != null) {
                mActiveEditDialog.dismiss();
            }
            mActiveEditDialog = new EditControlSideDialog(getContext(), this, control, mIsOverlayWindow);
            mActiveEditDialog.show();
        }
    }

    public void syncSelectionOverlays() {
        if (!mModifiable) return;
        if (mResizeHandle != null) {
            mResizeHandle.syncPosition();
        }
        if (mQuickActionBar != null && mSelectedControl != null) {
            View v = mSelectedControl.getControlView();
            mQuickActionBar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            int barW = mQuickActionBar.getMeasuredWidth();
            int barH = mQuickActionBar.getMeasuredHeight();
            float gap = ControlUtils.dpToPx(getContext(), 6f);
            float targetX = v.getX() + (v.getWidth() - barW) / 2f;
            float targetY = v.getY() - barH - gap;
            if (targetY < gap) {
                targetY = v.getY() + v.getHeight() + gap;
            }
            mQuickActionBar.setX(ControlUtils.clamp(targetX, gap, Math.max(gap, getWidth() - barW - gap)));
            mQuickActionBar.setY(ControlUtils.clamp(targetY, gap, Math.max(gap, getHeight() - barH - gap)));
        }
    }

    public void releaseAllActiveTouches() {
        mAimPointerId = MotionEvent.INVALID_POINTER_ID;
        mActivePointerTargets.clear();
        for (ControlButton btn : mButtons) {
            btn.releaseIfPressed();
        }
        for (ControlDrawer drawer : mDrawers) {
            for (ControlSubButton sub : drawer.getSubButtons()) {
                sub.releaseIfPressed();
            }
        }
        for (ControlJoystick joy : mJoysticks) {
            joy.sendKeyPresses(false);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        for (ControlButton btn : mButtons) {
            btn.regenerateDynamicCoordinates();
        }
        for (ControlDrawer drawer : mDrawers) {
            drawer.regenerateDynamicCoordinates();
            drawer.syncButtons();
        }
        for (ControlJoystick joy : mJoysticks) {
            joy.regenerateDynamicCoordinates();
        }
        syncSelectionOverlays();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mModifiable) {
            canvas.drawColor(0x281E1E1E);
            float step = ControlUtils.dpToPx(getContext(), 32f);
            for (float x = step; x < getWidth(); x += step) {
                canvas.drawLine(x, 0, x, getHeight(), mEditGridPaint);
            }
            for (float y = step; y < getHeight(); y += step) {
                canvas.drawLine(0, y, getWidth(), y, mEditGridPaint);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mModifiable) {
            return super.dispatchTouchEvent(ev);
        }

        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = ev.getActionIndex();
                int pid = ev.getPointerId(idx);
                float x = ev.getX(idx);
                float y = ev.getY(idx);

                View target = findTopInteractiveChildAt(x, y);
                if (target != null) {
                    mActivePointerTargets.put(pid, target);
                    dispatchSinglePointerToChild(target, ev, MotionEvent.ACTION_DOWN, idx);

                    if (target instanceof ControlButton && ((ControlButton) target).getProperties().passThruEnabled
                            && mAimPointerId == MotionEvent.INVALID_POINTER_ID) {
                        mAimPointerId = pid;
                        mLastAimX = x;
                        mLastAimY = y;
                    }
                } else {
                    if (mAimPointerId == MotionEvent.INVALID_POINTER_ID) {
                        mAimPointerId = pid;
                        mLastAimX = x;
                        mLastAimY = y;
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                int count = ev.getPointerCount();
                for (int i = 0; i < count; i++) {
                    int pid = ev.getPointerId(i);
                    View target = mActivePointerTargets.get(pid);
                    if (target != null) {
                        dispatchSinglePointerToChild(target, ev, MotionEvent.ACTION_MOVE, i);
                    }
                    if (pid == mAimPointerId) {
                        float currX = ev.getX(i);
                        float currY = ev.getY(i);
                        float dx = currX - mLastAimX;
                        float dy = currY - mLastAimY;
                        mLastAimX = currX;
                        mLastAimY = currY;
                        if (dx != 0f || dy != 0f) {
                            InputBridge.sendMouseMove(dx * mAimSensitivity, dy * mAimSensitivity);
                        }
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = ev.getActionIndex();
                int pid = ev.getPointerId(idx);
                View target = mActivePointerTargets.get(pid);
                if (target != null) {
                    dispatchSinglePointerToChild(target, ev, MotionEvent.ACTION_UP, idx);
                    mActivePointerTargets.remove(pid);
                }
                if (pid == mAimPointerId) {
                    mAimPointerId = MotionEvent.INVALID_POINTER_ID;
                }
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                for (int i = 0; i < mActivePointerTargets.size(); i++) {
                    View target = mActivePointerTargets.valueAt(i);
                    if (target != null) {
                        MotionEvent cancelEv = MotionEvent.obtain(ev.getDownTime(), ev.getEventTime(),
                                MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
                        target.onTouchEvent(cancelEv);
                        cancelEv.recycle();
                    }
                }
                mActivePointerTargets.clear();
                mAimPointerId = MotionEvent.INVALID_POINTER_ID;
                return true;
            }
        }
        return true;
    }

    private View findTopInteractiveChildAt(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child == mResizeHandle || child == mQuickActionBar || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (x >= child.getX() && x <= child.getX() + child.getWidth()
                    && y >= child.getY() && y <= child.getY() + child.getHeight()) {
                return child;
            }
        }
        return null;
    }

    private void dispatchSinglePointerToChild(View child, MotionEvent source, int singleAction, int pointerIndex) {
        float localX = source.getX(pointerIndex) - child.getX();
        float localY = source.getY(pointerIndex) - child.getY();
        MotionEvent synthetic = MotionEvent.obtain(
                source.getDownTime(),
                source.getEventTime(),
                singleAction,
                localX,
                localY,
                0
        );
        try {
            child.onTouchEvent(synthetic);
        } finally {
            synthetic.recycle();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mModifiable) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                selectControl(null, false);
            }
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = event.getActionIndex();
                if (mAimPointerId == MotionEvent.INVALID_POINTER_ID) {
                    mAimPointerId = event.getPointerId(idx);
                    mLastAimX = event.getX(idx);
                    mLastAimY = event.getY(idx);
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mAimPointerId != MotionEvent.INVALID_POINTER_ID) {
                    int idx = event.findPointerIndex(mAimPointerId);
                    if (idx >= 0) {
                        float currX = event.getX(idx);
                        float currY = event.getY(idx);
                        float dx = currX - mLastAimX;
                        float dy = currY - mLastAimY;
                        mLastAimX = currX;
                        mLastAimY = currY;
                        if (dx != 0f || dy != 0f) {
                            InputBridge.sendMouseMove(dx * mAimSensitivity, dy * mAimSensitivity);
                        }
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = event.getActionIndex();
                if (event.getPointerId(idx) == mAimPointerId) {
                    mAimPointerId = MotionEvent.INVALID_POINTER_ID;
                }
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                mAimPointerId = MotionEvent.INVALID_POINTER_ID;
                return true;
        }
        return true;
    }
}
