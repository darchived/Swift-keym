package com.example.customcontrols;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.R;
import com.example.customcontrols.utils.ControlUtils;
import com.example.input.LwjglGlfwKeycode;

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

        // Undo / Redo quick row in Edit Mode
        if (controlLayout.isModifiable()) {
            LinearLayout historyRow = new LinearLayout(context);
            historyRow.setOrientation(LinearLayout.HORIZONTAL);
            historyRow.setGravity(Gravity.CENTER_VERTICAL);
            int hp = (int) ControlUtils.dpToPx(context, 4f);
            historyRow.setPadding(hp, 0, hp, (int) ControlUtils.dpToPx(context, 6f));

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

        // Exact Cogwheel Menu items
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

        addMenuItem(list, context.getString(R.string.cog_save), v -> {
            dialog.dismiss();
            showSaveControlMapDialog();
        });

        addMenuItem(list, context.getString(R.string.cog_select_default), v -> {
            dialog.dismiss();
            showSelectDefaultDialog();
        });

        addMenuItem(list, context.getString(R.string.cog_export), v -> {
            dialog.dismiss();
            if (hostActions != null) hostActions.onExportControlMap();
        });

        addDivider(list);

        // Live Aim Sensitivity (- / +)
        LinearLayout sensRow = new LinearLayout(context);
        sensRow.setOrientation(LinearLayout.HORIZONTAL);
        sensRow.setGravity(Gravity.CENTER_VERTICAL);
        int sp = (int) ControlUtils.dpToPx(context, 6f);
        sensRow.setPadding(sp, sp, sp, sp);

        TextView sensLabel = new TextView(context);
        sensLabel.setText(String.format(Locale.US, "Aim %.1fx", controlLayout.getAimSensitivity()));
        sensLabel.setTextColor(0xFFFFFFFF);
        sensLabel.setTextSize(13f);
        sensLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button minusBtn = createSmallSquareBtn("-");
        minusBtn.setOnClickListener(v -> {
            if (hostActions != null) hostActions.onAdjustAimSensitivity(-0.2f);
            sensLabel.setText(String.format(Locale.US, "Aim %.1fx", controlLayout.getAimSensitivity()));
        });

        Button plusBtn = createSmallSquareBtn("+");
        LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams(
                (int) ControlUtils.dpToPx(context, 36f),
                (int) ControlUtils.dpToPx(context, 34f)
        );
        plusLp.setMarginStart((int) ControlUtils.dpToPx(context, 4f));
        plusBtn.setLayoutParams(plusLp);
        plusBtn.setOnClickListener(v -> {
            if (hostActions != null) hostActions.onAdjustAimSensitivity(0.2f);
            sensLabel.setText(String.format(Locale.US, "Aim %.1fx", controlLayout.getAimSensitivity()));
        });

        sensRow.addView(sensLabel);
        sensRow.addView(minusBtn);
        sensRow.addView(plusBtn);
        list.addView(sensRow);

        // Edit / Play Mode toggle
        String modeLabel = controlLayout.isModifiable() ? "Switch to Play Mode" : "Switch to Edit Mode";
        addMenuItem(list, modeLabel, v -> {
            if (hostActions != null) hostActions.onToggleEditMode();
            dialog.dismiss();
        });

        // Hide / Show HUD (for Overlay mode so touches pass straight through to GeForce NOW)
        if (hostActions != null && hostActions.isOverlayHost()) {
            String hideLabel = hostActions.isHudHidden()
                    ? context.getString(R.string.cog_show_hud)
                    : context.getString(R.string.cog_hide_hud);
            addMenuItem(list, hideLabel, v -> {
                hostActions.onToggleHideHud();
                dialog.dismiss();
            });
        }

        addMenuItem(list, context.getString(R.string.cog_exit), v -> {
            dialog.dismiss();
            if (hostActions != null) hostActions.onExitHost();
        });

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            int widthPx = (int) ControlUtils.dpToPx(context, 250f);
            int maxHeight = (int) (ControlUtils.getScreenHeight(context) * 0.92f);
            dialog.getWindow().setLayout(widthPx, Math.min(WindowManager.LayoutParams.WRAP_CONTENT, maxHeight));
        }
    }

    private void addMenuItem(LinearLayout parent, String label, View.OnClickListener listener) {
        TextView item = new TextView(context);
        item.setText(label);
        item.setTextColor(0xFFFFFFFF);
        item.setTextSize(14f);
        item.setGravity(Gravity.CENTER_VERTICAL);
        int hp = (int) ControlUtils.dpToPx(context, 10f);
        int vp = (int) ControlUtils.dpToPx(context, 11f);
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

    private Button createSmallSquareBtn(String label) {
        Button b = new Button(context);
        b.setText(label);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundResource(R.drawable.bg_input_field);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                (int) ControlUtils.dpToPx(context, 36f),
                (int) ControlUtils.dpToPx(context, 34f)
        ));
        return b;
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

    private void showLoadControlMapDialog() {
        List<LayoutProfileInfo> profiles = ControlLayoutManager.INSTANCE.listLayouts(context);
        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            labels[i] = profiles.get(i).getFileName();
        }
        String folderPath = ControlLayoutManager.INSTANCE.getControlMapDir(context).getAbsolutePath();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(folderPath);
        builder.setItems(labels, (d, which) -> {
            String chosen = labels[which];
            try {
                CustomControls loaded = ControlLayoutManager.INSTANCE.loadLayoutByName(context, chosen);
                ControlLayoutManager.INSTANCE.setActiveLayoutName(context, chosen);
                controlLayout.loadLayout(loaded);
                Toast.makeText(context, "Loaded " + chosen, Toast.LENGTH_SHORT).show();
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

    private void showSaveControlMapDialog() {
        EditText input = new EditText(context);
        input.setText(ControlLayoutManager.INSTANCE.getActiveLayoutName(context).replace(".json", ""));
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Save to controlmap/");
        builder.setView(input);
        builder.setPositiveButton("SAVE", (d, w) -> {
            String raw = input.getText().toString().trim();
            if (raw.isEmpty()) raw = "custom_layout";
            String savedName = ControlLayoutManager.INSTANCE.saveLayoutAs(context, raw, controlLayout.getLayout());
            controlLayout.markSaved();
            if (hostActions != null) hostActions.onLayoutSaved();
            Toast.makeText(context, "Saved " + savedName, Toast.LENGTH_SHORT).show();
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
                ControlLayoutManager.DEFAULT_FPS_FILE + " (Warzone / FPS)",
                ControlLayoutManager.DEFAULT_ACTION_FILE + " (Tactical / Action)"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Select default controlmap");
        builder.setItems(presets, (d, which) -> {
            CustomControls preset = (which == 0)
                    ? ControlLayoutManager.INSTANCE.createDefaultFpsPreset()
                    : ControlLayoutManager.INSTANCE.createDefaultActionPreset();
            String fileName = (which == 0) ? ControlLayoutManager.DEFAULT_FPS_FILE : ControlLayoutManager.DEFAULT_ACTION_FILE;
            ControlLayoutManager.INSTANCE.saveLayoutAs(context, fileName, preset);
            controlLayout.loadLayout(preset);
            Toast.makeText(context, "Reset & loaded " + fileName, Toast.LENGTH_SHORT).show();
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
