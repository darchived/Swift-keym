package com.example.customcontrols.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.example.R;

/**
 * Overlay-safe popup host that attaches directly to WindowManager (TYPE_APPLICATION_OVERLAY +
 * FLAG_SPLIT_TOUCH) when running inside a Service context, avoiding Dialog/AlertDialog/PopupWindow
 * BadTokenException crashes on Android 12-15 and OEM ROMs (HyperOS/MIUI/ColorOS).
 * When hosted inside an Activity (isOverlayWindow == false), falls back to a standard Dialog.
 */
public final class OverlayWindowHelper {

    private final Context context;
    private final boolean isOverlayWindow;
    private final WindowManager windowManager;

    private Dialog activityDialog;
    private FrameLayout overlayContainer;
    private WindowManager.LayoutParams overlayParams;
    private boolean showing = false;
    private Runnable onDismissListener;

    public OverlayWindowHelper(@NonNull Context context, boolean isOverlayWindow) {
        this.context = context;
        this.isOverlayWindow = isOverlayWindow;
        this.windowManager = isOverlayWindow
                ? (WindowManager) context.getSystemService(Context.WINDOW_SERVICE)
                : null;
        if (!isOverlayWindow) {
            this.activityDialog = new Dialog(context, R.style.Theme_SwiftKeyM_OverlayDialog);
            this.activityDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            this.activityDialog.setOnDismissListener(d -> {
                showing = false;
                if (onDismissListener != null) {
                    onDismissListener.run();
                }
            });
        }
    }

    public void setOnDismissListener(Runnable listener) {
        this.onDismissListener = listener;
    }

    public void setContentView(
            @NonNull View contentView,
            int widthPx,
            int heightPx,
            int gravity,
            boolean focusableForInputs
    ) {
        if (!isOverlayWindow) {
            activityDialog.setContentView(contentView);
            Window w = activityDialog.getWindow();
            if (w != null) {
                w.setGravity(gravity);
                w.setLayout(widthPx, heightPx);
            }
            return;
        }

        FrameLayout wrapper = new FrameLayout(context) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        wrapper.setFocusableInTouchMode(true);
        wrapper.addView(contentView, new FrameLayout.LayoutParams(
                widthPx == WindowManager.LayoutParams.WRAP_CONTENT
                        ? FrameLayout.LayoutParams.WRAP_CONTENT
                        : FrameLayout.LayoutParams.MATCH_PARENT,
                heightPx == WindowManager.LayoutParams.WRAP_CONTENT
                        ? FrameLayout.LayoutParams.WRAP_CONTENT
                        : FrameLayout.LayoutParams.MATCH_PARENT
        ));

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!focusableForInputs) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                widthPx,
                heightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = gravity;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        this.overlayContainer = wrapper;
        this.overlayParams = lp;
    }

    public void show() {
        if (showing) return;
        if (!isOverlayWindow) {
            try {
                activityDialog.show();
                showing = true;
            } catch (Throwable ignored) {
            }
            return;
        }

        if (windowManager != null && overlayContainer != null && overlayParams != null) {
            try {
                windowManager.addView(overlayContainer, overlayParams);
                showing = true;
            } catch (Throwable ignored) {
                showing = false;
            }
        }
    }

    public void dismiss() {
        if (!isOverlayWindow) {
            try {
                if (activityDialog != null && activityDialog.isShowing()) {
                    activityDialog.dismiss();
                }
            } catch (Throwable ignored) {
            }
            showing = false;
            return;
        }

        boolean wasShowing = showing;
        showing = false;
        if (windowManager != null && overlayContainer != null) {
            try {
                if (overlayContainer.isAttachedToWindow()) {
                    windowManager.removeViewImmediate(overlayContainer);
                }
            } catch (Throwable ignored) {
            }
        }
        if (wasShowing && onDismissListener != null) {
            onDismissListener.run();
        }
    }

    public boolean isShowing() {
        if (!isOverlayWindow) {
            return activityDialog != null && activityDialog.isShowing();
        }
        return showing && overlayContainer != null && overlayContainer.isAttachedToWindow();
    }
}
