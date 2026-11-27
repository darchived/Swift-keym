package com.example.customcontrols;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.R;
import com.example.customcontrols.utils.ControlUtils;

/**
 * Exit confirmation dialog matching MojoLauncher's "Leaving?" prompt when clean,
 * and displaying an Unsaved Layout Warning (CANCEL / SAVE & EXIT / CONTINUE) when dirty.
 */
public final class ExitConfirmDialog {

    public interface Callback {
        void onSaveAndExit();
        void onContinueExit();
    }

    private ExitConfirmDialog() {}

    public static void show(
            Context context,
            boolean hasUnsavedChanges,
            boolean isOverlayWindow,
            Callback callback
    ) {
        Dialog dialog = new Dialog(context, R.style.Theme_SwiftKeyM_OverlayDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null && isOverlayWindow) {
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        int pad = (int) ControlUtils.dpToPx(context, 18f);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog_panel);
        root.setPadding(pad, pad, pad, pad);

        TextView titleTv = new TextView(context);
        titleTv.setText(hasUnsavedChanges ? R.string.exit_unsaved_title : R.string.exit_leaving_title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(17f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(titleTv);

        TextView msgTv = new TextView(context);
        msgTv.setText(hasUnsavedChanges ? R.string.exit_unsaved_message : R.string.exit_leaving_message);
        msgTv.setTextColor(0xFFD0D0D0);
        msgTv.setTextSize(13.5f);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        msgLp.topMargin = (int) ControlUtils.dpToPx(context, 10f);
        msgLp.bottomMargin = (int) ControlUtils.dpToPx(context, 18f);
        msgTv.setLayoutParams(msgLp);
        root.addView(msgTv);

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        if (hasUnsavedChanges) {
            Button cancelBtn = createActionBtn(context, context.getString(R.string.exit_cancel), Color.TRANSPARENT, 0xFFA8A8A8, false);
            cancelBtn.setOnClickListener(v -> dialog.dismiss());

            Button continueBtn = createActionBtn(context, context.getString(R.string.exit_continue), 0xFF3A3A3A, 0xFFFFFFFF, true);
            LinearLayout.LayoutParams contLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (int) ControlUtils.dpToPx(context, 38f)
            );
            contLp.setMarginStart((int) ControlUtils.dpToPx(context, 8f));
            continueBtn.setLayoutParams(contLp);
            continueBtn.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) callback.onContinueExit();
            });

            Button saveExitBtn = createActionBtn(context, context.getString(R.string.exit_save_and_exit), 0xFFFFFFFF, 0xFF1E1E1E, true);
            LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (int) ControlUtils.dpToPx(context, 38f)
            );
            saveLp.setMarginStart((int) ControlUtils.dpToPx(context, 8f));
            saveExitBtn.setLayoutParams(saveLp);
            saveExitBtn.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) callback.onSaveAndExit();
            });

            btnRow.addView(cancelBtn);
            btnRow.addView(continueBtn);
            btnRow.addView(saveExitBtn);
        } else {
            Button noBtn = createActionBtn(context, context.getString(R.string.exit_no), Color.TRANSPARENT, 0xFFA8A8A8, false);
            noBtn.setOnClickListener(v -> dialog.dismiss());

            Button yesBtn = createActionBtn(context, context.getString(R.string.exit_yes), 0xFFFFFFFF, 0xFF1E1E1E, true);
            LinearLayout.LayoutParams yesLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (int) ControlUtils.dpToPx(context, 38f)
            );
            yesLp.setMarginStart((int) ControlUtils.dpToPx(context, 10f));
            yesBtn.setLayoutParams(yesLp);
            yesBtn.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) callback.onContinueExit();
            });

            btnRow.addView(noBtn);
            btnRow.addView(yesBtn);
        }

        root.addView(btnRow);
        dialog.setContentView(root);

        if (window != null) {
            int width = hasUnsavedChanges
                    ? (int) ControlUtils.dpToPx(context, 420f)
                    : (int) ControlUtils.dpToPx(context, 340f);
            int maxAllowed = (int) (ControlUtils.getScreenWidth(context) * 0.92f);
            window.setLayout(Math.min(width, maxAllowed), WindowManager.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }

    private static Button createActionBtn(
            Context context,
            String label,
            int bgColor,
            int textColor,
            boolean bold
    ) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(12.5f);
        btn.setTextColor(textColor);
        btn.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setStateListAnimator(null);
        int hp = (int) ControlUtils.dpToPx(context, 14f);
        btn.setPadding(hp, 4, hp, 4);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(ControlUtils.dpToPx(context, 8f));
        btn.setBackground(gd);

        btn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) ControlUtils.dpToPx(context, 38f)
        ));
        return btn;
    }
}
