package com.example.customcontrols.colorselector;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.R;
import com.example.customcontrols.utils.ControlUtils;
import com.example.customcontrols.utils.OverlayWindowHelper;

/**
 * Overlay-safe ARGB ColorSelector dialog styled in Swift KeyM grey and white.
 */
public class ColorSelector {

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    private static final int[] PRESET_COLORS = new int[]{
            0x802C2C2C, 0xCCFFFFFF, 0x994D4D4D, 0xCC1E1E1E,
            0xCCFF5252, 0xCCFFB300, 0xCCE0E0E0, 0x66000000
    };

    private final OverlayWindowHelper windowHost;
    private int currentColor;
    private boolean updatingFromCode = false;

    public ColorSelector(Context context, int initialColor, boolean isOverlayWindow, OnColorSelectedListener listener) {
        this.currentColor = initialColor;
        this.windowHost = new OverlayWindowHelper(context, isOverlayWindow);

        int pad = (int) ControlUtils.dpToPx(context, 16f);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog_panel);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText(R.string.color_selector_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16f);
        title.setPadding(0, 0, 0, (int) ControlUtils.dpToPx(context, 10f));
        root.addView(title);

        View preview = new View(context);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) ControlUtils.dpToPx(context, 40f)
        );
        previewLp.bottomMargin = (int) ControlUtils.dpToPx(context, 10f);
        preview.setLayoutParams(previewLp);
        root.addView(preview);

        LinearLayout swatchRow = new LinearLayout(context);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams swatchRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        swatchRowLp.bottomMargin = (int) ControlUtils.dpToPx(context, 10f);
        swatchRow.setLayoutParams(swatchRowLp);
        root.addView(swatchRow);

        EditText hexInput = new EditText(context);
        hexInput.setBackgroundResource(R.drawable.bg_input_field);
        hexInput.setTextColor(0xFFFFFFFF);
        hexInput.setHintTextColor(0xFFA8A8A8);
        hexInput.setHint(R.string.color_selector_hex);
        hexInput.setSingleLine(true);
        int hp = (int) ControlUtils.dpToPx(context, 10f);
        hexInput.setPadding(hp, hp / 2, hp, hp / 2);
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hexLp.bottomMargin = (int) ControlUtils.dpToPx(context, 10f);
        hexInput.setLayoutParams(hexLp);
        root.addView(hexInput);

        SeekBar alphaSeek = createChannelRow(context, root, "Alpha");
        SeekBar redSeek = createChannelRow(context, root, "Red");
        SeekBar greenSeek = createChannelRow(context, root, "Green");
        SeekBar blueSeek = createChannelRow(context, root, "Blue");

        Runnable refreshUi = () -> {
            updatingFromCode = true;
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(currentColor);
            gd.setStroke(2, 0xFF757575);
            gd.setCornerRadius(ControlUtils.dpToPx(context, 8f));
            preview.setBackground(gd);

            alphaSeek.setProgress(Color.alpha(currentColor));
            redSeek.setProgress(Color.red(currentColor));
            greenSeek.setProgress(Color.green(currentColor));
            blueSeek.setProgress(Color.blue(currentColor));

            String hex = String.format("%08X", currentColor);
            if (!hexInput.getText().toString().equalsIgnoreCase(hex)) {
                hexInput.setText(hex);
            }
            updatingFromCode = false;
        };

        for (int preset : PRESET_COLORS) {
            View swatch = new View(context);
            int sz = (int) ControlUtils.dpToPx(context, 28f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMarginEnd((int) ControlUtils.dpToPx(context, 6f));
            swatch.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(preset);
            gd.setStroke(2, 0xFF888888);
            swatch.setBackground(gd);
            swatch.setOnClickListener(v -> {
                currentColor = preset;
                refreshUi.run();
            });
            swatchRow.addView(swatch);
        }

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updatingFromCode || !fromUser) return;
                currentColor = Color.argb(
                        alphaSeek.getProgress(),
                        redSeek.getProgress(),
                        greenSeek.getProgress(),
                        blueSeek.getProgress()
                );
                refreshUi.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        alphaSeek.setOnSeekBarChangeListener(seekListener);
        redSeek.setOnSeekBarChangeListener(seekListener);
        greenSeek.setOnSeekBarChangeListener(seekListener);
        blueSeek.setOnSeekBarChangeListener(seekListener);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingFromCode) return;
                String raw = s.toString().trim().replace("#", "");
                if (raw.length() == 6 || raw.length() == 8) {
                    try {
                        currentColor = Color.parseColor("#" + raw);
                        refreshUi.run();
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        });

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnRowLp.topMargin = (int) ControlUtils.dpToPx(context, 12f);
        btnRow.setLayoutParams(btnRowLp);

        Button cancelBtn = new Button(context);
        cancelBtn.setText(R.string.color_selector_cancel);
        cancelBtn.setTextColor(0xFFA8A8A8);
        cancelBtn.setBackgroundColor(Color.TRANSPARENT);
        cancelBtn.setOnClickListener(v -> windowHost.dismiss());

        Button applyBtn = new Button(context);
        applyBtn.setText(R.string.color_selector_apply);
        applyBtn.setTextColor(0xFFFFFFFF);
        applyBtn.setBackgroundColor(Color.TRANSPARENT);
        applyBtn.setOnClickListener(v -> {
            if (listener != null) listener.onColorSelected(currentColor);
            windowHost.dismiss();
        });

        btnRow.addView(cancelBtn);
        btnRow.addView(applyBtn);
        root.addView(btnRow);

        refreshUi.run();
        windowHost.setContentView(
                root,
                (int) ControlUtils.dpToPx(context, 310f),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
                true
        );
    }

    private SeekBar createChannelRow(Context context, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowLp.bottomMargin = (int) ControlUtils.dpToPx(context, 6f);
        row.setLayoutParams(rowLp);

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(0xFFE0E0E0);
        tv.setTextSize(12f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                (int) ControlUtils.dpToPx(context, 48f),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(255);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        row.addView(tv);
        row.addView(seekBar);
        parent.addView(row);
        return seekBar;
    }

    public void show() {
        windowHost.show();
    }
}
