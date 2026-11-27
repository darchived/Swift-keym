package com.example.customcontrols;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.R;
import com.example.customcontrols.utils.ControlUtils;
import com.example.input.LwjglGlfwKeycode;
import com.example.overlay.CrosshairOverlayView;

import java.util.List;
import java.util.Locale;

/**
 * Right-side MojoLauncher-style Cogwheel Context Menu Drawer shared by
 * CustomControlsActivity and OverlayService.
 */
public class CogwheelMenuDialog {

    public interface HostActions {
        void onToggleEditMode();
        void onToggleHideHud();
        void onAdjustAimSensitivity(float delta);
        void onExportControlMap();
        void onExitHost();
        boolean isOverlayHost();
        boolean isHudHidden();
        default void onLayoutSaved() {}
        default void onGlobalOpacityChanged(float opacity) {}
        default void onTouchRippleToggled(boolean enabled) {}
        default boolean isAimEnabled() { return false; }
        default void onSetAimEnabled(boolean enabled) {}
        default void onAimSensitivitySet(float sensitivity) {}
        default void onGyroSettingsChanged() {}
        default void onCrosshairSettingsChanged() {}
        default void onHapticToggled(boolean enabled) {}
        default void onPositionsLockToggled(boolean locked) {}
        default void onAutoPauseAimToggled(boolean enabled) {}
        default void onStopServiceConfirmed() { onExitHost(); }
    }

    private final Context context;
    private final ControlLayout controlLayout;
    private final boolean isOverlayWindow;
    private final HostActions hostActions;
    private final Dialog dialog;

    public CogwheelMenuDialog(
            Context context,
            ControlLayout controlLayout,
            boolean isOverlayWindow,
            HostActions hostActions
    ) {
        this.context = context;
        this.controlLayout = controlLayout;
        this.isOverlayWindow = isOverlayWindow;
        this.hostActions = hostActions;

        this.dialog = new Dialog(context, R.style.Theme_SwiftKeyM_OverlayDialog);
        this.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = dialog.getWindow();
        if (w != null) {
            if (isOverlayWindow) {
                w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            }
            w.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        }
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog_panel);
        int pad = (int) ControlUtils.dpToPx(context, 10f);
        root.setPadding(pad, pad, pad, pad);

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        int sp = (int) ControlUtils.dpToPx(context, 6f);

        // Edit / Play Mode primary quick toggle
        String modeLabel = controlLayout.isModifiable() ? "Done Editing (Play Mode)" : "Edit Controls Live";
        addMenuItem(list, modeLabel, v -> {
            if (hostActions != null) hostActions.onToggleEditMode();
            dialog.dismiss();
        });

        // Undo / Redo quick row in Edit Mode
        if (controlLayout.isModifiable()) {
            LinearLayout historyRow = new LinearLayout(context);
            historyRow.setOrientation(LinearLayout.HORIZONTAL);
            historyRow.setGravity(Gravity.CENTER_VERTICAL);
            int hp = (int) ControlUtils.dpToPx(context, 4f);
            historyRow.setPadding(hp, (int) ControlUtils.dpToPx(context, 6f), hp, (int) ControlUtils.dpToPx(context, 6f));

            Button undoBtn = createHistoryBtn(context.getString(R.string.editor_undo), controlLayout.canUndo());
            Button redoBtn = createHistoryBtn(context.getString(R.string.editor_redo), controlLayout.canRedo());

            LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 34f), 1f);
            uLp.setMarginEnd((int) ControlUtils.dpToPx(context, 4f));
            undoBtn.setLayoutParams(uLp);
            redoBtn.setLayoutParams(new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 34f), 1f));

            undoBtn.setOnClickListener(v -> {
                controlLayout.undo();
                updateHistoryBtn(undoBtn, controlLayout.canUndo());
                updateHistoryBtn(redoBtn, controlLayout.canRedo());
            });
            redoBtn.setOnClickListener(v -> {
                controlLayout.redo();
                updateHistoryBtn(undoBtn, controlLayout.canUndo());
                updateHistoryBtn(redoBtn, controlLayout.canRedo());
            });

            historyRow.addView(undoBtn);
            historyRow.addView(redoBtn);
            list.addView(historyRow);
            addDivider(list);
        }

        // Control creation & Presets Manager
        addMenuItem(list, context.getString(R.string.cog_add_button), v -> {
            controlLayout.setModifiable(true);
            ControlData data = new ControlData(
                    "New",
                    new int[]{0, 0, 0, 0},
                    "0.50 * ${screen_width}",
                    "0.50 * ${screen_height}",
                    64f,
                    56f
            );
            controlLayout.addControlButton(data);
            controlLayout.notifyLayoutModified();
            dialog.dismiss();
        });

        addMenuItem(list, context.getString(R.string.cog_add_drawer), v -> {
            controlLayout.setModifiable(true);
            ControlDrawerData drawer = new ControlDrawerData();
            drawer.properties = new ControlData(
                    "Drawer",
                    new int[]{0, 0, 0, 0},
                    "0.50 * ${screen_width}",
                    "0.30 * ${screen_height}",
                    68f,
                    44f
            );
            drawer.buttonProperties.add(new ControlData("1", new int[]{LwjglGlfwKeycode.GLFW_KEY_1, 0, 0, 0}, "0", "0", 52f, 40f));
            drawer.buttonProperties.add(new ControlData("2", new int[]{LwjglGlfwKeycode.GLFW_KEY_2, 0, 0, 0}, "0", "0", 52f, 40f));
            controlLayout.addControlDrawer(drawer);
            controlLayout.notifyLayoutModified();
            dialog.dismiss();
        });

        addMenuItem(list, context.getString(R.string.cog_add_joystick), v -> {
            controlLayout.setModifiable(true);
            ControlJoystickData joy = new ControlJoystickData();
            joy.dynamicX = "0.14 * ${screen_width}";
            joy.dynamicY = "0.50 * ${screen_height}";
            controlLayout.addControlJoystick(joy);
            controlLayout.notifyLayoutModified();
            dialog.dismiss();
        });

        addMenuItem(list, context.getString(R.string.cog_load), v -> {
            dialog.dismiss();
            showLoadControlMapDialog();
        });

        addMenuItem(list, "+ Create New Preset", v -> {
            dialog.dismiss();
            showSaveControlMapDialog(true);
        });

        addMenuItem(list, context.getString(R.string.cog_save), v -> {
            dialog.dismiss();
            showSaveControlMapDialog(false);
        });

        addMenuItem(list, context.getString(R.string.cog_select_default), v -> {
            dialog.dismiss();
            showSelectDefaultDialog();
        });

        addMenuItem(list, context.getString(R.string.cog_export), v -> {
            dialog.dismiss();
            if (hostActions != null) hostActions.onExportControlMap();
        });

        // AIM SWIPE ZONE SECTION
        addSectionHeader(list, "AIM LOOK & SWIPE");
        if (hostActions != null && hostActions.isOverlayHost()) {
            addSwitchRow(list, "3D Aim Swipe Zone", hostActions.isAimEnabled(), sp, checked -> {
                hostActions.onSetAimEnabled(checked);
            });
        }

        // Live Aim Sensitivity Slider (0.2x .. 5.0x)
        LinearLayout aimSensBox = new LinearLayout(context);
        aimSensBox.setOrientation(LinearLayout.VERTICAL);
        aimSensBox.setPadding(sp, sp, sp, sp);

        TextView sensLabel = new TextView(context);
        sensLabel.setText(String.format(Locale.US, "Aim Sensitivity: %.1fx", controlLayout.getAimSensitivity()));
        sensLabel.setTextColor(0xFFFFFFFF);
        sensLabel.setTextSize(12.5f);

        SeekBar aimSeek = new SeekBar(context);
        aimSeek.setMax(48); // 0.2 .. 5.0 (step 0.1)
        aimSeek.setProgress(ControlUtils.clamp(Math.round((controlLayout.getAimSensitivity() - 0.2f) * 10f), 0, 48));
        aimSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float val = 0.2f + (progress / 10f);
                sensLabel.setText(String.format(Locale.US, "Aim Sensitivity: %.1fx", val));
                controlLayout.setAimSensitivity(val);
                ControlLayoutManager.INSTANCE.setAimSensitivity(context, val);
                if (hostActions != null) hostActions.onAimSensitivitySet(val);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        aimSensBox.addView(sensLabel);
        aimSensBox.addView(aimSeek);
        list.addView(aimSensBox);

        if (hostActions != null && hostActions.isOverlayHost()) {
            addSwitchRow(
                    list,
                    "Auto-Pause Aim on ESC/Tab/M",
                    ControlLayoutManager.INSTANCE.isAutoPauseAimOnMenu(context),
                    sp,
                    checked -> {
                        ControlLayoutManager.INSTANCE.setAutoPauseAimOnMenu(context, checked);
                        hostActions.onAutoPauseAimToggled(checked);
                    }
            );
        }
        addDivider(list);

        // GYROSCOPE SECTION
        addSectionHeader(list, "GYROSCOPE ENGINE");
        addSwitchRow(
                list,
                "Gyroscope Aiming",
                ControlLayoutManager.INSTANCE.isGyroEnabled(context),
                sp,
                checked -> {
                    ControlLayoutManager.INSTANCE.setGyroEnabled(context, checked);
                    if (hostActions != null) hostActions.onGyroSettingsChanged();
                }
        );

        // Activation Mode Selector (Always On / ADS Only / Firing Only)
        TextView modeHeader = new TextView(context);
        modeHeader.setText("Gyro Activation Mode");
        modeHeader.setTextColor(0xFFA8A8A8);
        modeHeader.setTextSize(11.5f);
        modeHeader.setPadding(sp, sp, sp, 4);
        list.addView(modeHeader);

        addChipSelectorRow(
                list,
                new String[]{"Always On", "ADS Only", "Firing"},
                ControlLayoutManager.INSTANCE.getGyroMode(context),
                sp,
                selectedIdx -> {
                    ControlLayoutManager.INSTANCE.setGyroMode(context, selectedIdx);
                    if (hostActions != null) hostActions.onGyroSettingsChanged();
                }
        );

        // Gyro X & Y Sensitivity Sliders (0.2x .. 8.0x)
        addFloatSliderRow(
                list,
                "Gyro X Sens",
                0.2f,
                8.0f,
                0.2f,
                ControlLayoutManager.INSTANCE.getGyroSensX(context),
                sp,
                "%.1fx",
                val -> {
                    ControlLayoutManager.INSTANCE.setGyroSensX(context, val);
                    if (hostActions != null) hostActions.onGyroSettingsChanged();
                }
        );
        addFloatSliderRow(
                list,
                "Gyro Y Sens",
                0.2f,
                8.0f,
                0.2f,
                ControlLayoutManager.INSTANCE.getGyroSensY(context),
                sp,
                "%.1fx",
                val -> {
                    ControlLayoutManager.INSTANCE.setGyroSensY(context, val);
                    if (hostActions != null) hostActions.onGyroSettingsChanged();
                }
        );

        addSwitchRow(
                list,
                "Invert Gyro X-Axis",
                ControlLayoutManager.INSTANCE.isGyroInvertX(context),
                sp,
                checked -> {
                    ControlLayoutManager.INSTANCE.setGyroInvertX(context, checked);
                    if (hostActions != null) hostActions.onGyroSettingsChanged();
                }
        );
        addSwitchRow(
                list,
                "Invert Gyro Y-Axis",
                ControlLayoutManager.INSTANCE.isGyroInvertY(context),
                sp,
                checked -> {
                    ControlLayoutManager.INSTANCE.setGyroInvertY(context, checked);
                    if (hostActions != null) hostActions.onGyroSettingsChanged();
                }
        );

        addFloatSliderRow(
                list,
                "Anti-Shake Smoothing",
                0f,
                1.0f,
                0.05f,
                ControlLayoutManager.INSTANCE.getGyroSmoothing(context),
                sp,
                "%.0f%%",
                val -> {
                    ControlLayoutManager.INSTANCE.setGyroSmoothing(context, val);
                    if (hostActions != null) hostActions.onGyroSettingsChanged();
                },
                true
        );
        addDivider(list);

        // CROSSHAIR & VISUAL HUD SECTION
        addSectionHeader(list, "CROSSHAIR & HUD");
        if (hostActions != null && hostActions.isOverlayHost()) {
            addSwitchRow(
                    list,
                    "Center Crosshair Overlay",
                    ControlLayoutManager.INSTANCE.isCrosshairEnabled(context),
                    sp,
                    checked -> {
                        ControlLayoutManager.INSTANCE.setCrosshairEnabled(context, checked);
                        hostActions.onCrosshairSettingsChanged();
                    }
            );
            addChipSelectorRow(
                    list,
                    new String[]{"Cross+", "Dot", "Cross", "Ring"},
                    ControlLayoutManager.INSTANCE.getCrosshairStyle(context),
                    sp,
                    selectedIdx -> {
                        ControlLayoutManager.INSTANCE.setCrosshairStyle(context, selectedIdx);
                        if (!ControlLayoutManager.INSTANCE.isCrosshairEnabled(context)) {
                            ControlLayoutManager.INSTANCE.setCrosshairEnabled(context, true);
                        }
                        hostActions.onCrosshairSettingsChanged();
                    }
            );
        }

        // Global Opacity live slider (15% - 100%)
        LinearLayout opacityBox = new LinearLayout(context);
        opacityBox.setOrientation(LinearLayout.VERTICAL);
        opacityBox.setPadding(sp, sp, sp, sp);

        int currentOpacityPct = Math.round(controlLayout.getGlobalOpacity() * 100f);
        TextView opacityLabel = new TextView(context);
        opacityLabel.setText(String.format(Locale.US, "Global Opacity: %d%%", currentOpacityPct));
        opacityLabel.setTextColor(0xFFFFFFFF);
        opacityLabel.setTextSize(12.5f);

        SeekBar opacitySeek = new SeekBar(context);
        opacitySeek.setMax(85); // 15..100
        opacitySeek.setProgress(Math.max(0, Math.min(85, currentOpacityPct - 15)));
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int pct = 15 + progress;
                float val = pct / 100f;
                opacityLabel.setText(String.format(Locale.US, "Global Opacity: %d%%", pct));
                controlLayout.setGlobalOpacity(val);
                ControlLayoutManager.INSTANCE.setGlobalOpacity(context, val);
                if (hostActions != null) hostActions.onGlobalOpacityChanged(val);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        opacityBox.addView(opacityLabel);
        opacityBox.addView(opacitySeek);
        list.addView(opacityBox);

        addSwitchRow(list, context.getString(R.string.cog_touch_ripple), controlLayout.isTouchRippleEnabled(), sp, checked -> {
            controlLayout.setTouchRippleEnabled(checked);
            ControlLayoutManager.INSTANCE.setTouchRippleEnabled(context, checked);
            if (hostActions != null) hostActions.onTouchRippleToggled(checked);
        });

        addSwitchRow(list, "Tactile Haptics (12ms)", controlLayout.isHapticEnabled(), sp, checked -> {
            controlLayout.setHapticEnabled(checked);
            ControlLayoutManager.INSTANCE.setHapticEnabled(context, checked);
            if (hostActions != null) hostActions.onHapticToggled(checked);
        });

        addSwitchRow(list, "Lock Button Positions", controlLayout.isPositionsLocked(), sp, checked -> {
            controlLayout.setPositionsLocked(checked);
            ControlLayoutManager.INSTANCE.setPositionsLocked(context, checked);
            if (hostActions != null) hostActions.onPositionsLockToggled(checked);
        });

        addSwitchRow(
                list,
                "Remember Last Preset",
                ControlLayoutManager.INSTANCE.isAutoLoadLastPreset(context),
                sp,
                checked -> ControlLayoutManager.INSTANCE.setAutoLoadLastPreset(context, checked)
        );
        addDivider(list);

        // Hide / Show HUD (for Overlay mode so touches pass straight through to GeForce NOW)
        if (hostActions != null && hostActions.isOverlayHost()) {
            String hideLabel = hostActions.isHudHidden()
                    ? context.getString(R.string.cog_show_hud)
                    : context.getString(R.string.cog_hide_hud);
            addMenuItem(list, hideLabel, v -> {
                hostActions.onToggleHideHud();
                dialog.dismiss();
            });

            addMenuItem(list, "Turn Off Service", v -> {
                dialog.dismiss();
                showTurnOffServiceConfirmDialog();
            });
        } else {
            addMenuItem(list, context.getString(R.string.cog_exit), v -> {
                dialog.dismiss();
                if (hostActions != null) hostActions.onExitHost();
            });
        }

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            int widthPx = (int) ControlUtils.dpToPx(context, 280f);
            int maxHeight = (int) (ControlUtils.getScreenHeight(context) * 0.92f);
            dialog.getWindow().setLayout(widthPx, Math.min(WindowManager.LayoutParams.WRAP_CONTENT, maxHeight));
        }
    }

    private interface FloatConsumer {
        void accept(float val);
    }

    private interface BoolConsumer {
        void accept(boolean val);
    }

    private interface IntConsumer {
        void accept(int val);
    }

    private void addSectionHeader(LinearLayout parent, String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(0xFFA8A8A8);
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        int hp = (int) ControlUtils.dpToPx(context, 8f);
        int top = (int) ControlUtils.dpToPx(context, 8f);
        tv.setPadding(hp, top, hp, 2);
        parent.addView(tv);
    }

    private void addSwitchRow(LinearLayout parent, String label, boolean initial, int sp, BoolConsumer onChange) {
        Switch sw = new Switch(context);
        sw.setText(label);
        sw.setTextColor(0xFFFFFFFF);
        sw.setTextSize(12.5f);
        sw.setChecked(initial);
        sw.setPadding(sp, sp, sp, sp);
        sw.setOnCheckedChangeListener((btn, isChecked) -> onChange.accept(isChecked));
        parent.addView(sw);
    }

    private void addFloatSliderRow(
            LinearLayout parent,
            String label,
            float min,
            float max,
            float step,
            float initial,
            int sp,
            String format,
            FloatConsumer onChange
    ) {
        addFloatSliderRow(parent, label, min, max, step, initial, sp, format, onChange, false);
    }

    private void addFloatSliderRow(
            LinearLayout parent,
            String label,
            float min,
            float max,
            float step,
            float initial,
            int sp,
            String format,
            FloatConsumer onChange,
            boolean displayAsPercent
    ) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(sp, sp / 2, sp, sp / 2);

        TextView tv = new TextView(context);
        float initialDisp = displayAsPercent ? initial * 100f : initial;
        tv.setText(label + ": " + String.format(Locale.US, format, initialDisp));
        tv.setTextColor(0xFFE0E0E0);
        tv.setTextSize(12f);

        int stepsCount = Math.max(1, Math.round((max - min) / step));
        SeekBar seek = new SeekBar(context);
        seek.setMax(stepsCount);
        seek.setProgress(ControlUtils.clamp(Math.round((initial - min) / step), 0, stepsCount));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float val = ControlUtils.clamp(min + (progress * step), min, max);
                float disp = displayAsPercent ? val * 100f : val;
                tv.setText(label + ": " + String.format(Locale.US, format, disp));
                onChange.accept(val);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        box.addView(tv);
        box.addView(seek);
        parent.addView(box);
    }

    private void addChipSelectorRow(
            LinearLayout parent,
            String[] labels,
            int selectedIndex,
            int sp,
            IntConsumer onSelect
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(sp, 2, sp, sp);

        Button[] buttons = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button chip = new Button(context);
            chip.setText(labels[i]);
            chip.setAllCaps(false);
            chip.setTextSize(10.5f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setMinWidth(0);
            chip.setMinHeight(0);
            chip.setStateListAnimator(null);
            chip.setPadding(4, 2, 4, 2);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0,
                    (int) ControlUtils.dpToPx(context, 30f),
                    1f
            );
            if (i < labels.length - 1) {
                lp.setMarginEnd((int) ControlUtils.dpToPx(context, 4f));
            }
            chip.setLayoutParams(lp);
            styleChip(chip, i == selectedIndex);
            chip.setOnClickListener(v -> {
                for (int j = 0; j < buttons.length; j++) {
                    styleChip(buttons[j], j == idx);
                }
                onSelect.accept(idx);
            });
            buttons[i] = chip;
            row.addView(chip);
        }
        parent.addView(row);
    }

    private void styleChip(Button chip, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(ControlUtils.dpToPx(context, 6f));
        if (active) {
            gd.setColor(0xFFFFFFFF);
            gd.setStroke(1, 0xFFFFFFFF);
            chip.setTextColor(0xFF1E1E1E);
        } else {
            gd.setColor(0xFF2C2C2C);
            gd.setStroke(1, 0x44FFFFFF);
            chip.setTextColor(0xFFD0D0D0);
        }
        chip.setBackground(gd);
    }

    private void addMenuItem(LinearLayout parent, String label, View.OnClickListener listener) {
        TextView item = new TextView(context);
        item.setText(label);
        item.setTextColor(0xFFFFFFFF);
        item.setTextSize(13.5f);
        item.setGravity(Gravity.CENTER_VERTICAL);
        int hp = (int) ControlUtils.dpToPx(context, 10f);
        int vp = (int) ControlUtils.dpToPx(context, 10f);
        item.setPadding(hp, vp, hp, vp);
        item.setOnClickListener(listener);
        parent.addView(item, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addDivider(parent);
    }

    private void addDivider(LinearLayout parent) {
        View div = new View(context);
        div.setBackgroundColor(0x2EFFFFFF);
        parent.addView(div, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        ));
    }

    private Button createHistoryBtn(String label, boolean enabled) {
        Button b = new Button(context);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundResource(R.drawable.bg_input_field);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(8, 2, 8, 2);
        updateHistoryBtn(b, enabled);
        return b;
    }

    private void updateHistoryBtn(Button b, boolean enabled) {
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1.0f : 0.38f);
        b.setTextColor(enabled ? 0xFFFFFFFF : 0xFFA8A8A8);
    }

    private void showTurnOffServiceConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Stop Swift KeyM");
        builder.setMessage("Are you sure you want to stop Swift KeyM?");
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Turn Off", (d, w) -> {
            if (controlLayout.hasUnsavedChanges()) {
                ControlLayoutManager.INSTANCE.saveActiveLayout(context, controlLayout.getLayout());
                controlLayout.markSaved();
            }
            if (hostActions != null) {
                hostActions.onStopServiceConfirmed();
            }
        });
        AlertDialog dlg = builder.create();
        if (isOverlayWindow && dlg.getWindow() != null) {
            dlg.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dlg.show();
    }

    private void showLoadControlMapDialog() {
        List<LayoutProfileInfo> profiles = ControlLayoutManager.INSTANCE.listLayouts(context);
        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            LayoutProfileInfo info = profiles.get(i);
            labels[i] = info.getDisplayName() + " (" + info.getGameName() + ")";
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Load Game Preset (" + profiles.size() + ")");
        builder.setItems(labels, (d, which) -> {
            String chosen = profiles.get(which).getFileName();
            try {
                CustomControls loaded = ControlLayoutManager.INSTANCE.loadLayoutByName(context, chosen);
                ControlLayoutManager.INSTANCE.setActiveLayoutName(context, chosen);
                controlLayout.loadLayout(loaded);
                Toast.makeText(context, "Loaded " + profiles.get(which).getDisplayName(), Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(context, "Failed to load " + chosen, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("CANCEL", null);
        AlertDialog dlg = builder.create();
        if (isOverlayWindow && dlg.getWindow() != null) {
            dlg.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dlg.show();
    }

    private void showSaveControlMapDialog(boolean asNew) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) ControlUtils.dpToPx(context, 16f);
        container.setPadding(pad, pad / 2, pad, 0);

        EditText nameInput = new EditText(context);
        if (asNew) {
            nameInput.setText("");
            nameInput.setHint("New preset name (e.g. codm_3finger)");
        } else {
            nameInput.setText(ControlLayoutManager.INSTANCE.getActiveLayoutName(context).replace(".json", ""));
            nameInput.setHint("Preset name (e.g. codm_hud)");
        }
        nameInput.setTextColor(Color.WHITE);
        nameInput.setHintTextColor(0xFF999999);
        nameInput.setSingleLine(true);

        EditText gameInput = new EditText(context);
        gameInput.setText(asNew ? "" : (controlLayout.getLayout() != null ? controlLayout.getLayout().gameName : ""));
        gameInput.setHint("Game label (e.g. Call of Duty Mobile)");
        gameInput.setTextColor(Color.WHITE);
        gameInput.setHintTextColor(0xFF999999);
        gameInput.setSingleLine(true);

        container.addView(nameInput);
        container.addView(gameInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(asNew ? "Create New Preset" : "Save Game Preset");
        builder.setView(container);
        builder.setPositiveButton("SAVE", (d, w) -> {
            String raw = nameInput.getText().toString().trim();
            if (raw.isEmpty()) raw = "preset_" + System.currentTimeMillis();
            String game = gameInput.getText().toString().trim();
            if (!game.isEmpty() && controlLayout.getLayout() != null) {
                controlLayout.getLayout().gameName = game;
            }
            String savedName = ControlLayoutManager.INSTANCE.saveLayoutAs(context, raw, controlLayout.getLayout());
            controlLayout.markSaved();
            if (hostActions != null) hostActions.onLayoutSaved();
            Toast.makeText(context, "Saved preset " + savedName, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("CANCEL", null);
        AlertDialog dlg = builder.create();
        if (isOverlayWindow && dlg.getWindow() != null) {
            dlg.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dlg.show();
    }

    private void showSelectDefaultDialog() {
        String[] presets = new String[]{
                "Warzone / CODM (FPS)",
                "Minecraft Java (Hotbar + WASD)",
                "Action / RPG (Q/E/R + Stick)",
                "Tactical Arena Shooter (Valorant / CS)",
                "MOBA / ARPG (Q/W/E/R + D/F)",
                "Blank Canvas"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Built-in Game Presets");
        builder.setItems(presets, (d, which) -> {
            CustomControls preset;
            String fileName;
            switch (which) {
                case 1:
                    preset = ControlLayoutManager.INSTANCE.createMinecraftJavaPreset();
                    fileName = ControlLayoutManager.DEFAULT_MINECRAFT_FILE;
                    break;
                case 2:
                    preset = ControlLayoutManager.INSTANCE.createDefaultActionPreset();
                    fileName = ControlLayoutManager.DEFAULT_ACTION_FILE;
                    break;
                case 3:
                    preset = ControlLayoutManager.INSTANCE.createArenaShooterPreset();
                    fileName = ControlLayoutManager.DEFAULT_ARENA_FILE;
                    break;
                case 4:
                    preset = ControlLayoutManager.INSTANCE.createMobaArpgPreset();
                    fileName = ControlLayoutManager.DEFAULT_MOBA_FILE;
                    break;
                case 5:
                    preset = ControlLayoutManager.INSTANCE.createBlankCanvasPreset();
                    fileName = ControlLayoutManager.DEFAULT_BLANK_FILE;
                    break;
                case 0:
                default:
                    preset = ControlLayoutManager.INSTANCE.createDefaultFpsPreset();
                    fileName = ControlLayoutManager.DEFAULT_FPS_FILE;
                    break;
            }
            ControlLayoutManager.INSTANCE.saveLayoutAs(context, fileName, preset);
            controlLayout.loadLayout(preset);
            Toast.makeText(context, "Loaded " + presets[which], Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("CANCEL", null);
        AlertDialog dlg = builder.create();
        if (isOverlayWindow && dlg.getWindow() != null) {
            dlg.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dlg.show();
    }

    public void show() {
        dialog.show();
    }
}
