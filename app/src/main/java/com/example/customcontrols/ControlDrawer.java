package com.example.customcontrols;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import com.example.customcontrols.utils.ControlUtils;

import java.util.ArrayList;

/**
 * Collapsible ControlDrawer adapted from MojoLauncher's ControlDrawer.
 */
@SuppressLint("ViewConstructor")
public class ControlDrawer extends ControlButton {

    private final ControlDrawerData mDrawerData;
    private final ArrayList<ControlSubButton> mSubButtons = new ArrayList<>();

    public ControlDrawer(Context context, ControlLayout parentLayout, ControlDrawerData drawerData) {
        super(context, parentLayout, drawerData != null ? drawerData.properties : new ControlData());
        this.mDrawerData = drawerData != null ? drawerData : new ControlDrawerData();
        this.mDrawerData.ensureValid();
        this.mProperties = this.mDrawerData.properties;

        for (ControlData subData : this.mDrawerData.buttonProperties) {
            ControlSubButton subBtn = new ControlSubButton(context, parentLayout, this, subData);
            mSubButtons.add(subBtn);
        }
        updateProperties();
    }

    public ControlDrawerData getDrawerData() {
        return mDrawerData;
    }

    public ArrayList<ControlSubButton> getSubButtons() {
        return mSubButtons;
    }

    public void attachSubButtonsToParent() {
        for (ControlSubButton sub : mSubButtons) {
            if (sub.getParent() == null) {
                mParentLayout.addView(sub);
            }
        }
        syncButtons();
    }

    public void detachSubButtonsFromParent() {
        for (ControlSubButton sub : mSubButtons) {
            sub.releaseIfPressed();
            mParentLayout.removeView(sub);
        }
    }

    public void addSubButton(ControlData data) {
        if (data == null) data = new ControlData();
        data.ensureValid();
        mDrawerData.buttonProperties.add(data);
        ControlSubButton sub = new ControlSubButton(getContext(), mParentLayout, this, data);
        mSubButtons.add(sub);
        mParentLayout.addView(sub);
        syncButtons();
    }

    public void removeSubButton(ControlSubButton sub) {
        sub.releaseIfPressed();
        mSubButtons.remove(sub);
        mDrawerData.buttonProperties.remove(sub.getProperties());
        mParentLayout.removeView(sub);
        syncButtons();
    }

    @Override
    public void updateProperties() {
        super.updateProperties();
        syncButtons();
    }

    @Override
    public void setX(float x) {
        super.setX(x);
        syncButtons();
    }

    @Override
    public void setY(float y) {
        super.setY(y);
        syncButtons();
    }

    public void syncButtons() {
        if (mSubButtons == null) return;
        boolean shouldShow = mDrawerData.open || mParentLayout.isModifiable();
        float gapPx = ControlUtils.dpToPx(getContext(), 4f);
        float baseX = getX();
        float baseY = getY();
        float selfW = mProperties.getWidthPx(getContext());
        float selfH = mProperties.getHeightPx(getContext());

        float offsetX = 0f;
        float offsetY = 0f;

        for (int i = 0; i < mSubButtons.size(); i++) {
            ControlSubButton sub = mSubButtons.get(i);
            sub.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
            if (!shouldShow) continue;

            float subW = sub.getProperties().getWidthPx(getContext());
            float subH = sub.getProperties().getHeightPx(getContext());

            switch (mDrawerData.orientation) {
                case ControlDrawerData.ORIENTATION_DOWN:
                    offsetY += (i == 0 ? selfH : mSubButtons.get(i - 1).getProperties().getHeightPx(getContext())) + gapPx;
                    sub.setX(baseX);
                    sub.setY(baseY + offsetY);
                    break;
                case ControlDrawerData.ORIENTATION_UP:
                    offsetY -= subH + gapPx;
                    sub.setX(baseX);
                    sub.setY(baseY + offsetY);
                    break;
                case ControlDrawerData.ORIENTATION_RIGHT:
                    offsetX += (i == 0 ? selfW : mSubButtons.get(i - 1).getProperties().getWidthPx(getContext())) + gapPx;
                    sub.setX(baseX + offsetX);
                    sub.setY(baseY);
                    break;
                case ControlDrawerData.ORIENTATION_LEFT:
                    offsetX -= subW + gapPx;
                    sub.setX(baseX + offsetX);
                    sub.setY(baseY);
                    break;
                case ControlDrawerData.ORIENTATION_FREE:
                default:
                    sub.regenerateDynamicCoordinates();
                    break;
            }
        }
    }

    @Override
    protected boolean handlePlayTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDrawerData.open = !mDrawerData.open;
            mIsToggled = mDrawerData.open;
            applyBackgroundDrawable();
            syncButtons();
        }
        return true;
    }

    @Override
    public void cloneButton() {
        ControlDrawerData cloned = new ControlDrawerData(mDrawerData);
        cloned.properties.name = cloned.properties.name + " Copy";
        float offsetPx = ControlUtils.dpToPx(getContext(), 24f);
        cloned.properties.setStaticPosition(getX() + offsetPx, getY() + offsetPx,
                Math.max(1, mParentLayout.getWidth()), Math.max(1, mParentLayout.getHeight()));
        mParentLayout.addControlDrawer(cloned);
        mParentLayout.notifyLayoutModified();
    }

    @Override
    public void removeButton() {
        detachSubButtonsFromParent();
        mParentLayout.removeControlDrawer(this);
        mParentLayout.notifyLayoutModified();
    }
}
