package com.example.customcontrols;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.R;
import com.example.customcontrols.utils.ControlUtils;
import com.example.customcontrols.utils.OverlayWindowHelper;
import com.example.input.LwjglGlfwKeycode;

/**
 * Zalith Launcher 2-style full-width Visual Key, Mouse & Overlay Bind Picker modal.
 */
public class VisualBindPickerDialog {

    public interface OnBindSelectedListener {
        void onBindSelected(int keyCode, String label);
    }

    private static final class KeyCap {
        final String label;
        final int code;
        final float weight;

        KeyCap(String label, int code, float weight) {
            this.label = label;
            this.code = code;
            this.weight = weight;
        }
    }

    private final Context context;
    private final OverlayWindowHelper windowHost;
    private final OnBindSelectedListener listener;

    private LinearLayout keyTabContent;
    private LinearLayout mouseTabContent;
    private LinearLayout overlayTabContent;
    private TextView tabKeyBtn;
    private TextView tabMouseBtn;
    private TextView tabOverlayBtn;

    public VisualBindPickerDialog(Context context, boolean isOverlayWindow, OnBindSelectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.windowHost = new OverlayWindowHelper(context, isOverlayWindow);
        buildUi();
    }

    private void buildUi() {
        int pad = (int) ControlUtils.dpToPx(context, 12f);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog_panel);
        root.setPadding(pad, pad, pad, pad);

        // Header + Tabs
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText(R.string.bind_picker_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        View spacer = new View(context);
        headerRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        tabKeyBtn = createTabButton(context.getString(R.string.bind_tab_key));
        tabMouseBtn = createTabButton(context.getString(R.string.bind_tab_mouse));
        tabOverlayBtn = createTabButton(context.getString(R.string.bind_tab_overlay));

        tabKeyBtn.setOnClickListener(v -> selectTab(0));
        tabMouseBtn.setOnClickListener(v -> selectTab(1));
        tabOverlayBtn.setOnClickListener(v -> selectTab(2));

        headerRow.addView(tabKeyBtn);
        headerRow.addView(tabMouseBtn);
        headerRow.addView(tabOverlayBtn);
        root.addView(headerRow);

        // Tab Body Container
        ScrollView bodyScroll = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        scrollLp.topMargin = (int) ControlUtils.dpToPx(context, 8f);
        scrollLp.bottomMargin = (int) ControlUtils.dpToPx(context, 8f);
        bodyScroll.setLayoutParams(scrollLp);

        LinearLayout bodyContainer = new LinearLayout(context);
        bodyContainer.setOrientation(LinearLayout.VERTICAL);
        bodyScroll.addView(bodyContainer);
        root.addView(bodyScroll);

        keyTabContent = buildKeyboardGrid();
        mouseTabContent = buildMouseGrid();
        overlayTabContent = buildOverlayGrid();

        bodyContainer.addView(keyTabContent);
        bodyContainer.addView(mouseTabContent);
        bodyContainer.addView(overlayTabContent);

        selectTab(0);

        // Bottom Footer (Delete Keybind / Close)
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END);

        Button clearBtn = createActionPill(context.getString(R.string.bind_clear), 0xFF3A3A3A, 0xFFE0E0E0);
        clearBtn.setOnClickListener(v -> {
            if (listener != null) listener.onBindSelected(LwjglGlfwKeycode.GLFW_KEY_UNKNOWN, "UNKNOWN");
            windowHost.dismiss();
        });

        Button closeBtn = createActionPill(context.getString(R.string.bind_close), 0xFFFFFFFF, 0xFF1E1E1E);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) ControlUtils.dpToPx(context, 38f)
        );
        closeLp.setMarginStart((int) ControlUtils.dpToPx(context, 8f));
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> windowHost.dismiss());

        footer.addView(clearBtn);
        footer.addView(closeBtn);
        root.addView(footer);

        int w = (int) (ControlUtils.getScreenWidth(context) * 0.90f);
        int h = (int) (ControlUtils.getScreenHeight(context) * 0.88f);
        windowHost.setContentView(root, Math.max((int) ControlUtils.dpToPx(context, 520f), w), h, Gravity.CENTER, false);
    }

    private TextView createTabButton(String label) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        int hp = (int) ControlUtils.dpToPx(context, 14f);
        int vp = (int) ControlUtils.dpToPx(context, 6f);
        tv.setPadding(hp, vp, hp, vp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginStart((int) ControlUtils.dpToPx(context, 6f));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void selectTab(int index) {
        keyTabContent.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        mouseTabContent.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        overlayTabContent.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        styleTab(tabKeyBtn, index == 0);
        styleTab(tabMouseBtn, index == 1);
        styleTab(tabOverlayBtn, index == 2);
    }

    private void styleTab(TextView tab, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(ControlUtils.dpToPx(context, 8f));
        if (active) {
            gd.setColor(0xFFFFFFFF);
            tab.setTextColor(0xFF1E1E1E);
        } else {
            gd.setColor(0xFF333333);
            tab.setTextColor(0xFFD0D0D0);
        }
        tab.setBackground(gd);
    }

    private LinearLayout buildKeyboardGrid() {
        LinearLayout board = new LinearLayout(context);
        board.setOrientation(LinearLayout.VERTICAL);

        KeyCap[][] rows = new KeyCap[][]{
                {
                        new KeyCap("Esc", LwjglGlfwKeycode.GLFW_KEY_ESCAPE, 1.1f),
                        new KeyCap("F1", LwjglGlfwKeycode.GLFW_KEY_F1, 1f),
                        new KeyCap("F2", LwjglGlfwKeycode.GLFW_KEY_F2, 1f),
                        new KeyCap("F3", LwjglGlfwKeycode.GLFW_KEY_F3, 1f),
                        new KeyCap("F4", LwjglGlfwKeycode.GLFW_KEY_F4, 1f),
                        new KeyCap("F5", LwjglGlfwKeycode.GLFW_KEY_F5, 1f),
                        new KeyCap("F6", LwjglGlfwKeycode.GLFW_KEY_F6, 1f),
                        new KeyCap("F7", LwjglGlfwKeycode.GLFW_KEY_F7, 1f),
                        new KeyCap("F8", LwjglGlfwKeycode.GLFW_KEY_F8, 1f),
                        new KeyCap("F9", LwjglGlfwKeycode.GLFW_KEY_F9, 1f),
                        new KeyCap("F10", LwjglGlfwKeycode.GLFW_KEY_F10, 1f),
                        new KeyCap("F11", LwjglGlfwKeycode.GLFW_KEY_F11, 1f),
                        new KeyCap("F12", LwjglGlfwKeycode.GLFW_KEY_F12, 1f)
                },
                {
                        new KeyCap("`", LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT, 0.9f),
                        new KeyCap("1", LwjglGlfwKeycode.GLFW_KEY_1, 1f),
                        new KeyCap("2", LwjglGlfwKeycode.GLFW_KEY_2, 1f),
                        new KeyCap("3", LwjglGlfwKeycode.GLFW_KEY_3, 1f),
                        new KeyCap("4", LwjglGlfwKeycode.GLFW_KEY_4, 1f),
                        new KeyCap("5", LwjglGlfwKeycode.GLFW_KEY_5, 1f),
                        new KeyCap("6", LwjglGlfwKeycode.GLFW_KEY_6, 1f),
                        new KeyCap("7", LwjglGlfwKeycode.GLFW_KEY_7, 1f),
                        new KeyCap("8", LwjglGlfwKeycode.GLFW_KEY_8, 1f),
                        new KeyCap("9", LwjglGlfwKeycode.GLFW_KEY_9, 1f),
                        new KeyCap("0", LwjglGlfwKeycode.GLFW_KEY_0, 1f),
                        new KeyCap("-", LwjglGlfwKeycode.GLFW_KEY_MINUS, 0.9f),
                        new KeyCap("+", LwjglGlfwKeycode.GLFW_KEY_EQUAL, 0.9f),
                        new KeyCap("Backspace", LwjglGlfwKeycode.GLFW_KEY_BACKSPACE, 1.9f)
                },
                {
                        new KeyCap("Tab", LwjglGlfwKeycode.GLFW_KEY_TAB, 1.35f),
                        new KeyCap("Q", LwjglGlfwKeycode.GLFW_KEY_Q, 1f),
                        new KeyCap("W", LwjglGlfwKeycode.GLFW_KEY_W, 1f),
                        new KeyCap("E", LwjglGlfwKeycode.GLFW_KEY_E, 1f),
                        new KeyCap("R", LwjglGlfwKeycode.GLFW_KEY_R, 1f),
                        new KeyCap("T", LwjglGlfwKeycode.GLFW_KEY_T, 1f),
                        new KeyCap("Y", LwjglGlfwKeycode.GLFW_KEY_Y, 1f),
                        new KeyCap("U", LwjglGlfwKeycode.GLFW_KEY_U, 1f),
                        new KeyCap("I", LwjglGlfwKeycode.GLFW_KEY_I, 1f),
                        new KeyCap("O", LwjglGlfwKeycode.GLFW_KEY_O, 1f),
                        new KeyCap("P", LwjglGlfwKeycode.GLFW_KEY_P, 1f),
                        new KeyCap("[", LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET, 0.9f),
                        new KeyCap("]", LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET, 0.9f),
                        new KeyCap("\\", LwjglGlfwKeycode.GLFW_KEY_BACKSLASH, 1.1f)
                },
                {
                        new KeyCap("Capslock", LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK, 1.55f),
                        new KeyCap("A", LwjglGlfwKeycode.GLFW_KEY_A, 1f),
                        new KeyCap("S", LwjglGlfwKeycode.GLFW_KEY_S, 1f),
                        new KeyCap("D", LwjglGlfwKeycode.GLFW_KEY_D, 1f),
                        new KeyCap("F", LwjglGlfwKeycode.GLFW_KEY_F, 1f),
                        new KeyCap("G", LwjglGlfwKeycode.GLFW_KEY_G, 1f),
                        new KeyCap("H", LwjglGlfwKeycode.GLFW_KEY_H, 1f),
                        new KeyCap("J", LwjglGlfwKeycode.GLFW_KEY_J, 1f),
                        new KeyCap("K", LwjglGlfwKeycode.GLFW_KEY_K, 1f),
                        new KeyCap("L", LwjglGlfwKeycode.GLFW_KEY_L, 1f),
                        new KeyCap(";", LwjglGlfwKeycode.GLFW_KEY_SEMICOLON, 0.95f),
                        new KeyCap("'", LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE, 0.95f),
                        new KeyCap("Enter", LwjglGlfwKeycode.GLFW_KEY_ENTER, 1.75f)
                },
                {
                        new KeyCap("Shift", LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, 1.9f),
                        new KeyCap("Z", LwjglGlfwKeycode.GLFW_KEY_Z, 1f),
                        new KeyCap("X", LwjglGlfwKeycode.GLFW_KEY_X, 1f),
                        new KeyCap("C", LwjglGlfwKeycode.GLFW_KEY_C, 1f),
                        new KeyCap("V", LwjglGlfwKeycode.GLFW_KEY_V, 1f),
                        new KeyCap("B", LwjglGlfwKeycode.GLFW_KEY_B, 1f),
                        new KeyCap("N", LwjglGlfwKeycode.GLFW_KEY_N, 1f),
                        new KeyCap("M", LwjglGlfwKeycode.GLFW_KEY_M, 1f),
                        new KeyCap(",", LwjglGlfwKeycode.GLFW_KEY_COMMA, 0.95f),
                        new KeyCap(".", LwjglGlfwKeycode.GLFW_KEY_PERIOD, 0.95f),
                        new KeyCap("/", LwjglGlfwKeycode.GLFW_KEY_SLASH, 0.95f),
                        new KeyCap("Shift", LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT, 1.9f)
                },
                {
                        new KeyCap("Ctrl", LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, 1.3f),
                        new KeyCap("Alt", LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT, 1.15f),
                        new KeyCap("Space", LwjglGlfwKeycode.GLFW_KEY_SPACE, 4.4f),
                        new KeyCap("Alt", LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT, 1.15f),
                        new KeyCap("Ctrl", LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL, 1.3f),
                        new KeyCap("←", LwjglGlfwKeycode.GLFW_KEY_LEFT, 0.85f),
                        new KeyCap("↑", LwjglGlfwKeycode.GLFW_KEY_UP, 0.85f),
                        new KeyCap("↓", LwjglGlfwKeycode.GLFW_KEY_DOWN, 0.85f),
                        new KeyCap("→", LwjglGlfwKeycode.GLFW_KEY_RIGHT, 0.85f)
                }
        };

        int rowGap = (int) ControlUtils.dpToPx(context, 4f);
        int keyGap = (int) ControlUtils.dpToPx(context, 3f);
        int keyHeight = (int) ControlUtils.dpToPx(context, 38f);

        for (KeyCap[] rowKeys : rows) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rLp.bottomMargin = rowGap;
            row.setLayoutParams(rLp);

            for (int i = 0; i < rowKeys.length; i++) {
                KeyCap cap = rowKeys[i];
                Button btn = createKeyCapView(cap.label);
                LinearLayout.LayoutParams kLp = new LinearLayout.LayoutParams(0, keyHeight, cap.weight);
                if (i < rowKeys.length - 1) kLp.setMarginEnd(keyGap);
                btn.setLayoutParams(kLp);
                btn.setOnClickListener(v -> {
                    if (listener != null) listener.onBindSelected(cap.code, cap.label);
                    windowHost.dismiss();
                });
                row.addView(btn);
            }
            board.addView(row);
        }
        return board;
    }

    private LinearLayout buildMouseGrid() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);

        KeyCap[] mouseCaps = new KeyCap[]{
                new KeyCap("MB1 (Left Click)", LwjglGlfwKeycode.SPECIALBTN_MOUSEPRI, 1f),
                new KeyCap("MB2 (Right Click)", LwjglGlfwKeycode.SPECIALBTN_MOUSESEC, 1f),
                new KeyCap("MB3 (Middle Click)", LwjglGlfwKeycode.SPECIALBTN_MOUSEMID, 1f),
                new KeyCap("Scroll Up", LwjglGlfwKeycode.SPECIALBTN_SCROLLUP, 1f),
                new KeyCap("Scroll Down", LwjglGlfwKeycode.SPECIALBTN_SCROLLDOWN, 1f)
        };

        for (KeyCap cap : mouseCaps) {
            Button btn = createKeyCapView(cap.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) ControlUtils.dpToPx(context, 46f)
            );
            lp.bottomMargin = (int) ControlUtils.dpToPx(context, 8f);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onBindSelected(cap.code, cap.label);
                windowHost.dismiss();
            });
            panel.addView(btn);
        }
        return panel;
    }

    private LinearLayout buildOverlayGrid() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);

        KeyCap[] overlayCaps = new KeyCap[]{
                new KeyCap("Hide / Show HUD", LwjglGlfwKeycode.SPECIALBTN_TOGGLECTRL, 1f),
                new KeyCap("Toggle Gyro", LwjglGlfwKeycode.SPECIALBTN_TOGGLEGYRO, 1f),
                new KeyCap("Toggle Edit Mode", LwjglGlfwKeycode.SPECIALBTN_MENU, 1f),
                new KeyCap("Clear Slot (UNKNOWN)", LwjglGlfwKeycode.GLFW_KEY_UNKNOWN, 1f)
        };

        for (KeyCap cap : overlayCaps) {
            Button btn = createKeyCapView(cap.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) ControlUtils.dpToPx(context, 46f)
            );
            lp.bottomMargin = (int) ControlUtils.dpToPx(context, 8f);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onBindSelected(cap.code, cap.label);
                windowHost.dismiss();
            });
            panel.addView(btn);
        }
        return panel;
    }

    private Button createKeyCapView(String label) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(11f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(4, 2, 4, 2);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setStateListAnimator(null);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF3A3A3A);
        gd.setStroke(1, 0x55FFFFFF);
        gd.setCornerRadius(ControlUtils.dpToPx(context, 7f));
        btn.setBackground(gd);
        return btn;
    }

    private Button createActionPill(String label, int bgColor, int textColor) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(12f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(textColor);
        int hp = (int) ControlUtils.dpToPx(context, 14f);
        btn.setPadding(hp, 4, hp, 4);
        btn.setMinHeight(0);
        btn.setStateListAnimator(null);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(ControlUtils.dpToPx(context, 16f));
        btn.setBackground(gd);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) ControlUtils.dpToPx(context, 38f)
        ));
        return btn;
    }

    public void show() {
        windowHost.show();
    }
}
