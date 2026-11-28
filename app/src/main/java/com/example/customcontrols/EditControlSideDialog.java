package com.example.customcontrols;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.R;
import com.example.customcontrols.colorselector.ColorSelector;
import com.example.customcontrols.utils.ControlUtils;
import com.example.customcontrols.utils.OverlayWindowHelper;
import com.example.input.LwjglGlfwKeycode;

import java.util.Locale;

/**
 * Right-side Swift KeyM Editor properties drawer matching MojoLauncher's EditControlSideDialog.
 */
public class EditControlSideDialog {

    private final Context context;
    private final ControlLayout parentLayout;
    private final ControlInterface target;
    private final boolean isOverlayWindow;
    private final OverlayWindowHelper windowHost;

    public EditControlSideDialog(Context context, ControlLayout parentLayout, ControlInterface target, boolean isOverlayWindow) {
        this.context = context;
        this.parentLayout = parentLayout;
        this.target = target;
        this.isOverlayWindow = isOverlayWindow;

        windowHost = new OverlayWindowHelper(context, isOverlayWindow);
        windowHost.setOnDismissListener(parentLayout::notifyLayoutModified);

        buildContent();
    }

    private void buildContent() {
        ControlData data = target.getProperties();
        int pad = (int) ControlUtils.dpToPx(context, 14f);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundResource(R.drawable.bg_dialog_panel);
        outer.setPadding(pad, pad, pad, pad);

        // Header
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(R.string.edit_ctrl_title);
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setTextSize(15f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button doneBtn = new Button(context);
        doneBtn.setText(R.string.edit_ctrl_done);
        doneBtn.setTextColor(0xFFFFFFFF);
        doneBtn.setBackgroundColor(Color.TRANSPARENT);
        doneBtn.setOnClickListener(v -> windowHost.dismiss());

        header.addView(titleTv);
        header.addView(doneBtn);
        outer.addView(header);

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        outer.addView(scroll);

        // Name Input
        addSectionLabel(body, context.getString(R.string.edit_ctrl_name));
        EditText nameInput = createInputField(data.name, InputType.TYPE_CLASS_TEXT);
        nameInput.addTextChangedListener(new SimpleTextWatcher(s -> {
            data.name = s;
            target.updateProperties();
        }));
        body.addView(nameInput);

        // Size (Width x Height)
        addSectionLabel(body, context.getString(R.string.edit_ctrl_size));
        LinearLayout sizeRow = new LinearLayout(context);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText widthInput = createInputField(String.valueOf((int) data.width), InputType.TYPE_CLASS_NUMBER);
        widthInput.setGravity(Gravity.CENTER);
        EditText heightInput = createInputField(String.valueOf((int) data.height), InputType.TYPE_CLASS_NUMBER);
        heightInput.setGravity(Gravity.CENTER);

        TextView timesTv = new TextView(context);
        timesTv.setText("  x  ");
        timesTv.setTextColor(0xFFA8A8A8);

        widthInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heightInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        widthInput.addTextChangedListener(new SimpleTextWatcher(s -> {
            try {
                float v = Float.parseFloat(s);
                if (v >= 16f && v <= 400f) {
                    data.width = v;
                    target.updateProperties();
                    parentLayout.syncSelectionOverlays();
                }
            } catch (NumberFormatException ignored) {}
        }));
        heightInput.addTextChangedListener(new SimpleTextWatcher(s -> {
            try {
                float v = Float.parseFloat(s);
                if (v >= 16f && v <= 400f) {
                    data.height = v;
                    target.updateProperties();
                    parentLayout.syncSelectionOverlays();
                }
            } catch (NumberFormatException ignored) {}
        }));

        sizeRow.addView(widthInput);
        sizeRow.addView(timesTv);
        sizeRow.addView(heightInput);
        body.addView(sizeRow);

        // 4 Mapping Slots (2x2 Grid opening Zalith-style VisualBindPickerDialog)
        if (!(target instanceof ControlJoystick) && !(target instanceof ControlDrawer)) {
            addSectionLabel(body, context.getString(R.string.edit_ctrl_mapping));
            for (int rowIdx = 0; rowIdx < 2; rowIdx++) {
                LinearLayout mapRow = new LinearLayout(context);
                mapRow.setOrientation(LinearLayout.HORIZONTAL);
                mapRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams mrLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                mrLp.bottomMargin = (int) ControlUtils.dpToPx(context, 4f);
                mapRow.setLayoutParams(mrLp);

                int slotA = rowIdx * 2;
                int slotB = slotA + 1;

                Button btnA = createSlotButton(data, slotA);
                TextView plusTv = new TextView(context);
                plusTv.setText(" + ");
                plusTv.setTextColor(0xFFA8A8A8);
                Button btnB = createSlotButton(data, slotB);

                btnA.setLayoutParams(new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 38f), 1f));
                btnB.setLayoutParams(new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 38f), 1f));

                mapRow.addView(btnA);
                mapRow.addView(plusTv);
                mapRow.addView(btnB);
                body.addView(mapRow);
            }
        }

        // Toggleable, Mouse pass-thru, Swipeable switches
        if (target instanceof ControlJoystick) {
            ControlJoystickData joyData = ((ControlJoystick) target).getProperties();
            addSwitchRow(body, context.getString(R.string.edit_ctrl_forward_lock), joyData.forwardLock, checked -> {
                joyData.forwardLock = checked;
                target.updateProperties();
                parentLayout.notifyLayoutModified();
            });
            addSwitchRow(body, context.getString(R.string.edit_ctrl_absolute_stick), joyData.absolute, checked -> {
                joyData.absolute = checked;
                target.updateProperties();
                parentLayout.notifyLayoutModified();
            });
        } else if (!(target instanceof ControlDrawer)) {
            addSwitchRow(body, context.getString(R.string.edit_ctrl_toggle), data.isToggle, checked -> {
                data.isToggle = checked;
                parentLayout.notifyLayoutModified();
            });
            addSwitchRow(body, context.getString(R.string.edit_ctrl_passthru), data.passThruEnabled, checked -> {
                data.passThruEnabled = checked;
                parentLayout.notifyLayoutModified();
            });
            addSwitchRow(body, context.getString(R.string.edit_ctrl_swipeable), data.isSwipeable, checked -> {
                data.isSwipeable = checked;
                parentLayout.notifyLayoutModified();
            });
            addSwitchRow(body, context.getString(R.string.edit_ctrl_macro_sequential), data.macroSequential, checked -> {
                data.macroSequential = checked;
                parentLayout.notifyLayoutModified();
            });

            // v1.0.0 Advanced Action Modes
            addSectionLabel(body, "v1.0.0 Pro Control Modes");
            EditText secNameInput = createInputField(data.secondaryName, InputType.TYPE_CLASS_TEXT);
            secNameInput.setHint("Fn / Long-Press Label");
            secNameInput.addTextChangedListener(new SimpleTextWatcher(s -> {
                data.secondaryName = s;
                target.updateProperties();
            }));
            body.addView(secNameInput);

            Button secSlotBtn = createSecondarySlotButton(data);
            LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) ControlUtils.dpToPx(context, 38f)
            );
            ssLp.topMargin = (int) ControlUtils.dpToPx(context, 6f);
            secSlotBtn.setLayoutParams(ssLp);
            body.addView(secSlotBtn);

            addSwitchRow(body, "Layer Shift Modifier (Fn Key)", data.isLayerShiftKey, checked -> {
                data.isLayerShiftKey = checked;
                parentLayout.notifyLayoutModified();
            });
            addSwitchRow(body, "Dual-Action (Tap vs 300ms Hold)", data.dualActionEnabled, checked -> {
                data.dualActionEnabled = checked;
                parentLayout.notifyLayoutModified();
            });
            addSwitchRow(body, "8-Way Radial Quick Wheel", data.isRadialWheel, checked -> {
                data.isRadialWheel = checked;
                parentLayout.notifyLayoutModified();
            });
            Button radialEditBtn = createFlatRowButton("Configure 8 Radial Slices (N..NW)", 0xFFFFFFFF);
            LinearLayout.LayoutParams radLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) ControlUtils.dpToPx(context, 36f)
            );
            radLp.topMargin = (int) ControlUtils.dpToPx(context, 4f);
            radialEditBtn.setLayoutParams(radLp);
            radialEditBtn.setOnClickListener(v -> showRadialSlicePicker(data));
            body.addView(radialEditBtn);
            addSwitchRow(body, "Virtual Scroll Wheel Strip", data.isScrollStrip, checked -> {
                data.isScrollStrip = checked;
                parentLayout.notifyLayoutModified();
            });
            addSwitchRow(body, "Free-Look Button (Head Turn)", data.isFreeLookButton, checked -> {
                data.isFreeLookButton = checked;
                parentLayout.notifyLayoutModified();
            });

            // Quick Turn (Off / 90° / 180°)
            addSectionLabel(body, "Quick Turn Snap");
            int qtSel = data.quickTurnDegrees == 90 ? 1 : (data.quickTurnDegrees == 180 ? 2 : 0);
            LinearLayout qtRow = new LinearLayout(context);
            qtRow.setOrientation(LinearLayout.HORIZONTAL);
            String[] qtLabels = new String[]{"Off", "90° Turn", "180° Flip"};
            Button[] qtChips = new Button[3];
            for (int i = 0; i < 3; i++) {
                final int pos = i;
                Button chip = new Button(context);
                chip.setText(qtLabels[i]);
                chip.setAllCaps(false);
                chip.setTextSize(10.5f);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                chip.setMinWidth(0);
                chip.setMinHeight(0);
                chip.setStateListAnimator(null);
                LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 30f), 1f);
                if (i < 2) qLp.setMarginEnd((int) ControlUtils.dpToPx(context, 4f));
                chip.setLayoutParams(qLp);
                styleOrientChip(chip, i == qtSel);
                chip.setOnClickListener(v -> {
                    for (int j = 0; j < 3; j++) styleOrientChip(qtChips[j], j == pos);
                    data.quickTurnDegrees = pos == 1 ? 90 : (pos == 2 ? 180 : 0);
                    parentLayout.notifyLayoutModified();
                });
                qtChips[i] = chip;
                qtRow.addView(chip);
            }
            body.addView(qtRow);

            // Macro Linker Row
            addSectionLabel(body, "Linked Pro Macro");
            String currentMacroLabel = "None";
            java.util.List<com.example.input.MacroDefinition> savedMacros = ControlLayoutManager.INSTANCE.getMacros(context);
            for (com.example.input.MacroDefinition m : savedMacros) {
                if (m.getId().equals(data.macroId)) {
                    currentMacroLabel = "Macro: " + m.getName();
                    break;
                }
            }
            Button macroBtn = createFlatRowButton(currentMacroLabel + " (Tap to Cycle)", 0xFFFFFFFF);
            LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) ControlUtils.dpToPx(context, 38f)
            );
            mbLp.topMargin = (int) ControlUtils.dpToPx(context, 4f);
            macroBtn.setLayoutParams(mbLp);
            macroBtn.setOnClickListener(v -> {
                java.util.List<com.example.input.MacroDefinition> macros = ControlLayoutManager.INSTANCE.getMacros(context);
                if (macros.isEmpty()) {
                    Toast.makeText(context, "Create macros in Home -> Pro Macro Builder first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                int curIdx = -1;
                for (int i = 0; i < macros.size(); i++) {
                    if (macros.get(i).getId().equals(data.macroId)) {
                        curIdx = i;
                        break;
                    }
                }
                int nextIdx = curIdx + 1;
                if (nextIdx >= macros.size()) {
                    data.macroId = "";
                    macroBtn.setText("None (Tap to Cycle)");
                } else {
                    com.example.input.MacroDefinition chosen = macros.get(nextIdx);
                    data.macroId = chosen.getId();
                    macroBtn.setText("Macro: " + chosen.getName() + " (Tap to Cycle)");
                }
                parentLayout.notifyLayoutModified();
            });
            body.addView(macroBtn);
        }

        // Drawer-specific orientation & + Add Sub-Button
        if (target instanceof ControlDrawer) {
            ControlDrawer drawer = (ControlDrawer) target;
            ControlDrawerData drawerData = drawer.getDrawerData();
            addSectionLabel(body, context.getString(R.string.edit_ctrl_orientation));

            String[] orientations = new String[]{"Down", "Left", "Up", "Right", "Free"};
            LinearLayout orientRow = new LinearLayout(context);
            orientRow.setOrientation(LinearLayout.HORIZONTAL);
            Button[] orientChips = new Button[orientations.length];
            int selIdx = ControlUtils.clamp(drawerData.orientation, 0, 4);
            for (int i = 0; i < orientations.length; i++) {
                final int pos = i;
                Button chip = new Button(context);
                chip.setText(orientations[i]);
                chip.setAllCaps(false);
                chip.setTextSize(10.5f);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                chip.setMinWidth(0);
                chip.setMinHeight(0);
                chip.setStateListAnimator(null);
                chip.setPadding(4, 2, 4, 2);
                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                        0, (int) ControlUtils.dpToPx(context, 30f), 1f
                );
                if (i < orientations.length - 1) {
                    cLp.setMarginEnd((int) ControlUtils.dpToPx(context, 3f));
                }
                chip.setLayoutParams(cLp);
                styleOrientChip(chip, i == selIdx);
                chip.setOnClickListener(v -> {
                    for (int j = 0; j < orientChips.length; j++) {
                        styleOrientChip(orientChips[j], j == pos);
                    }
                    drawerData.orientation = pos;
                    drawer.syncButtons();
                    parentLayout.notifyLayoutModified();
                });
                orientChips[i] = chip;
                orientRow.addView(chip);
            }
            body.addView(orientRow);

            Button addSubBtn = createFlatRowButton(context.getString(R.string.edit_ctrl_add_subbutton), 0xFFFFFFFF);
            addSubBtn.setOnClickListener(v -> {
                ControlData sub = new ControlData(
                        "Sub",
                        new int[]{LwjglGlfwKeycode.GLFW_KEY_1, 0, 0, 0},
                        data.dynamicX,
                        data.dynamicY,
                        48f,
                        48f
                );
                drawer.addSubButton(sub);
                parentLayout.notifyLayoutModified();
            });
            body.addView(addSubBtn);
        }

        // Custom PNG Icon Picker (for Buttons / Sub-Buttons / Drawers)
        if (!(target instanceof ControlJoystick)) {
            addSectionLabel(body, context.getString(R.string.edit_ctrl_custom_icon));

            LinearLayout imgBtnRow = new LinearLayout(context);
            imgBtnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams irLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            irLp.topMargin = (int) ControlUtils.dpToPx(context, 4f);
            imgBtnRow.setLayoutParams(irLp);

            Button pickImgBtn = createFlatRowButton(context.getString(R.string.edit_ctrl_pick_image), 0xFFFFFFFF);
            pickImgBtn.setLayoutParams(new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 36f), 1f));
            pickImgBtn.setOnClickListener(v -> {
                if (parentLayout.getImagePickListener() != null) {
                    parentLayout.getImagePickListener().onRequestImagePick(savedPath -> {
                        if (savedPath != null) {
                            data.iconPath = savedPath;
                            try {
                                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(savedPath);
                                if (bmp != null) {
                                    int maxDim = 256;
                                    float scale = Math.min((float) maxDim / bmp.getWidth(), (float) maxDim / bmp.getHeight());
                                    if (scale < 1.0f) {
                                        int w = Math.round(bmp.getWidth() * scale);
                                        int h = Math.round(bmp.getHeight() * scale);
                                        bmp = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true);
                                    }
                                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, baos);
                                    data.imageBase64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
                                }
                            } catch (Throwable ignored) {}
                            target.updateProperties();
                            parentLayout.notifyLayoutModified();
                        }
                    });
                } else {
                    Toast.makeText(context, "Open HUD & Bind Editor to pick custom PNG icons.", Toast.LENGTH_SHORT).show();
                }
            });

            Button clearImgBtn = createFlatRowButton(context.getString(R.string.edit_ctrl_clear_image), 0xFFA8A8A8);
            LinearLayout.LayoutParams ciLp = new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 36f), 0.7f);
            ciLp.setMarginStart((int) ControlUtils.dpToPx(context, 4f));
            clearImgBtn.setLayoutParams(ciLp);
            clearImgBtn.setOnClickListener(v -> {
                data.iconPath = "";
                data.imageBase64 = "";
                target.updateProperties();
                parentLayout.notifyLayoutModified();
            });

            imgBtnRow.addView(pickImgBtn);
            imgBtnRow.addView(clearImgBtn);
            body.addView(imgBtnRow);
        }

        // Background Color & Stroke Color rows
        LinearLayout colorRow = new LinearLayout(context);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        crLp.topMargin = (int) ControlUtils.dpToPx(context, 10f);
        colorRow.setLayoutParams(crLp);

        Button bgColorBtn = createColorButton(context.getString(R.string.edit_ctrl_bg_color), data.bgColor);
        bgColorBtn.setOnClickListener(v -> new ColorSelector(context, data.bgColor, isOverlayWindow, color -> {
            data.bgColor = color;
            updateColorSwatch(bgColorBtn, color);
            target.updateProperties();
            parentLayout.notifyLayoutModified();
        }).show());

        Button strokeColorBtn = createColorButton(context.getString(R.string.edit_ctrl_stroke_color), data.strokeColor);
        strokeColorBtn.setOnClickListener(v -> new ColorSelector(context, data.strokeColor, isOverlayWindow, color -> {
            data.strokeColor = color;
            updateColorSwatch(strokeColorBtn, color);
            target.updateProperties();
            parentLayout.notifyLayoutModified();
        }).show());

        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 40f), 1f);
        c1.setMarginEnd((int) ControlUtils.dpToPx(context, 6f));
        bgColorBtn.setLayoutParams(c1);
        strokeColorBtn.setLayoutParams(new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 40f), 1f));
        colorRow.addView(bgColorBtn);
        colorRow.addView(strokeColorBtn);
        body.addView(colorRow);

        // Stroke Width, Corner Radius, Button Opacity Sliders
        addSliderRow(body, context.getString(R.string.edit_ctrl_stroke_width), 0, 10, data.strokeWidth, val -> {
            data.strokeWidth = val;
            target.updateProperties();
        });

        if (!(target instanceof ControlJoystick)) {
            addSliderRow(body, context.getString(R.string.edit_ctrl_corner_radius), 0, 100, (int) data.cornerRadius, val -> {
                data.cornerRadius = val;
                target.updateProperties();
            });
        }

        addSliderRow(body, context.getString(R.string.edit_ctrl_opacity), 10, 100, (int) (data.opacity * 100f), val -> {
            data.opacity = val / 100f;
            target.updateProperties();
        });

        // Visibility checkboxes
        addSectionLabel(body, context.getString(R.string.edit_ctrl_visibility));
        LinearLayout visRow = new LinearLayout(context);
        visRow.setOrientation(LinearLayout.HORIZONTAL);
        CheckBox inGameCb = new CheckBox(context);
        inGameCb.setText(R.string.edit_ctrl_in_game);
        inGameCb.setTextColor(0xFFFFFFFF);
        inGameCb.setChecked(data.displayInGame);
        inGameCb.setOnCheckedChangeListener((b, c) -> {
            data.displayInGame = c;
            parentLayout.notifyLayoutModified();
        });

        CheckBox inMenuCb = new CheckBox(context);
        inMenuCb.setText(R.string.edit_ctrl_in_menus);
        inMenuCb.setTextColor(0xFFFFFFFF);
        inMenuCb.setChecked(data.displayInMenu);
        inMenuCb.setOnCheckedChangeListener((b, c) -> {
            data.displayInMenu = c;
            parentLayout.notifyLayoutModified();
        });
        visRow.addView(inGameCb);
        visRow.addView(inMenuCb);
        body.addView(visRow);

        // DELETE / CLONE Footer
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fLp.topMargin = (int) ControlUtils.dpToPx(context, 10f);
        footer.setLayoutParams(fLp);

        Button deleteBtn = new Button(context);
        deleteBtn.setText(R.string.edit_ctrl_delete);
        deleteBtn.setTextColor(0xFFD96B6B);
        deleteBtn.setTypeface(Typeface.DEFAULT_BOLD);
        deleteBtn.setBackgroundResource(R.drawable.bg_input_field);
        deleteBtn.setOnClickListener(v -> {
            target.removeButton();
            windowHost.dismiss();
        });

        Button cloneBtn = new Button(context);
        cloneBtn.setText(R.string.edit_ctrl_clone);
        cloneBtn.setTextColor(0xFFFFFFFF);
        cloneBtn.setTypeface(Typeface.DEFAULT_BOLD);
        cloneBtn.setBackgroundResource(R.drawable.bg_input_field);
        cloneBtn.setOnClickListener(v -> {
            target.cloneButton();
            windowHost.dismiss();
        });

        LinearLayout.LayoutParams fl1 = new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 40f), 1f);
        fl1.setMarginEnd((int) ControlUtils.dpToPx(context, 6f));
        deleteBtn.setLayoutParams(fl1);
        cloneBtn.setLayoutParams(new LinearLayout.LayoutParams(0, (int) ControlUtils.dpToPx(context, 40f), 1f));
        footer.addView(deleteBtn);
        footer.addView(cloneBtn);
        outer.addView(footer);

        int maxW = (int) ControlUtils.dpToPx(context, 320f);
        int maxH = (int) (ControlUtils.getScreenHeight(context) * 0.90f);
        windowHost.setContentView(outer, maxW, maxH, Gravity.END | Gravity.CENTER_VERTICAL, true);
    }

    private Button createSlotButton(ControlData data, int slotIdx) {
        Button slotBtn = new Button(context);
        slotBtn.setAllCaps(false);
        slotBtn.setTextSize(11f);
        slotBtn.setTextColor(0xFFFFFFFF);
        slotBtn.setBackgroundResource(R.drawable.bg_input_field);
        int code = data.keycodes[slotIdx];
        slotBtn.setText(code == 0 ? "UNKNOWN" : LwjglGlfwKeycode.getKeyName(code).toUpperCase(Locale.US));
        slotBtn.setOnClickListener(v -> {
            new VisualBindPickerDialog(context, isOverlayWindow, (selectedCode, label) -> {
                data.keycodes[slotIdx] = selectedCode;
                slotBtn.setText(selectedCode == 0 ? "UNKNOWN" : label.toUpperCase(Locale.US));
                if (slotIdx == 0 && (data.name == null || data.name.equals("New") || data.name.equals("Key") || data.name.equals("Sub"))) {
                    data.name = selectedCode == 0 ? "New" : label;
                }
                target.updateProperties();
                parentLayout.notifyLayoutModified();
            }).show();
        });
        return slotBtn;
    }

    private Button createSecondarySlotButton(ControlData data) {
        if (data.secondaryKeycodes == null || data.secondaryKeycodes.length < 4) {
            data.secondaryKeycodes = new int[]{0, 0, 0, 0};
        }
        Button slotBtn = new Button(context);
        slotBtn.setAllCaps(false);
        slotBtn.setTextSize(11f);
        slotBtn.setTextColor(0xFFFFFFFF);
        slotBtn.setBackgroundResource(R.drawable.bg_input_field);
        int code = data.secondaryKeycodes[0];
        String keyLabel = code == 0 ? "NONE" : LwjglGlfwKeycode.getKeyName(code).toUpperCase(Locale.US);
        slotBtn.setText("Fn / Hold Bind: " + keyLabel);
        slotBtn.setOnClickListener(v -> {
            new VisualBindPickerDialog(context, isOverlayWindow, (selectedCode, label) -> {
                data.secondaryKeycodes[0] = selectedCode;
                String nextLbl = selectedCode == 0 ? "NONE" : label.toUpperCase(Locale.US);
                slotBtn.setText("Fn / Hold Bind: " + nextLbl);
                if (data.secondaryName == null || data.secondaryName.isEmpty()) {
                    data.secondaryName = selectedCode == 0 ? "" : label;
                }
                target.updateProperties();
                parentLayout.notifyLayoutModified();
            }).show();
        });
        return slotBtn;
    }

    private void showRadialSlicePicker(ControlData data) {
        data.ensureValid();
        final int[] selectedSlice = new int[]{0};
        String[] compass = new String[]{"Top (0)", "Top-R (1)", "Right (2)", "Bot-R (3)", "Bot (4)", "Bot-L (5)", "Left (6)", "Top-L (7)"};
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) ControlUtils.dpToPx(context, 12f);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundColor(0xFF222222);

        TextView title = new TextView(context);
        title.setText("Radial Wheel Slice Editor");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        Button sliceBtn = createFlatRowButton("", 0xFFFFFFFF);
        Button bindBtn = createFlatRowButton("", 0xFFFFFFFF);

        Runnable refreshLabels = () -> {
            int idx = selectedSlice[0];
            int kc = data.radialKeycodes[idx];
            String kName = kc == 0 ? "UNBOUND" : LwjglGlfwKeycode.getKeyName(kc).toUpperCase(Locale.US);
            sliceBtn.setText("Slice: " + compass[idx] + "  (Tap to Next)");
            bindBtn.setText("Bind: " + kName + " [Label: " + data.radialLabels[idx] + "]  (Tap to Change)");
        };
        refreshLabels.run();

        sliceBtn.setOnClickListener(v -> {
            selectedSlice[0] = (selectedSlice[0] + 1) % 8;
            refreshLabels.run();
        });
        bindBtn.setOnClickListener(v -> {
            new VisualBindPickerDialog(context, isOverlayWindow, (code, label) -> {
                int idx = selectedSlice[0];
                data.radialKeycodes[idx] = code;
                data.radialLabels[idx] = code == 0 ? "-" : label;
                refreshLabels.run();
                parentLayout.notifyLayoutModified();
            }).show();
        });

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) ControlUtils.dpToPx(context, 40f)
        );
        bLp.topMargin = (int) ControlUtils.dpToPx(context, 8f);
        sliceBtn.setLayoutParams(bLp);
        bindBtn.setLayoutParams(bLp);
        card.addView(sliceBtn);
        card.addView(bindBtn);

        Button doneBtn = createFlatRowButton("Close Slice Editor", 0xFFFFFFFF);
        doneBtn.setLayoutParams(bLp);
        doneBtn.setOnClickListener(v -> show());
        card.addView(doneBtn);

        windowHost.setContentView(
                card,
                (int) ControlUtils.dpToPx(context, 310f),
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL,
                true
        );
    }

    private EditText createInputField(String initial, int inputType) {
        EditText et = new EditText(context);
        et.setBackgroundResource(R.drawable.bg_input_field);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF757575);
        et.setSingleLine(true);
        et.setInputType(inputType);
        et.setText(initial);
        int p = (int) ControlUtils.dpToPx(context, 8f);
        et.setPadding(p, p / 2, p, p / 2);
        return et;
    }

    private Button createFlatRowButton(String label, int textColor) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(11.5f);
        btn.setTextColor(textColor);
        btn.setBackgroundResource(R.drawable.bg_input_field);
        btn.setMinHeight(0);
        btn.setPadding(8, 4, 8, 4);
        return btn;
    }

    private void addSectionLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFFA8A8A8);
        tv.setTextSize(11.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = (int) ControlUtils.dpToPx(context, 10f);
        lp.bottomMargin = (int) ControlUtils.dpToPx(context, 4f);
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private interface IntConsumer {
        void accept(int val);
    }

    private interface BoolConsumer {
        void accept(boolean val);
    }

    private interface StringConsumer {
        void accept(String val);
    }

    private static final class SimpleTextWatcher implements TextWatcher {
        private final StringConsumer consumer;

        SimpleTextWatcher(StringConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            consumer.accept(s.toString());
        }
    }

    private void addSliderRow(LinearLayout parent, String label, int min, int max, int initial, IntConsumer onChange) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = (int) ControlUtils.dpToPx(context, 8f);
        row.setLayoutParams(lp);

        TextView tv = new TextView(context);
        tv.setTextColor(0xFFE0E0E0);
        tv.setTextSize(12f);
        tv.setText(label + ": " + initial);

        SeekBar seek = new SeekBar(context);
        seek.setMax(max - min);
        seek.setProgress(ControlUtils.clamp(initial - min, 0, max - min));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int actual = min + progress;
                tv.setText(label + ": " + actual);
                onChange.accept(actual);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                parentLayout.notifyLayoutModified();
            }
        });

        row.addView(tv);
        row.addView(seek);
        parent.addView(row);
    }

    private void addSwitchRow(LinearLayout parent, String label, boolean initial, BoolConsumer onChange) {
        Switch sw = new Switch(context);
        sw.setText(label);
        sw.setTextColor(0xFFFFFFFF);
        sw.setTextSize(13f);
        sw.setChecked(initial);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = (int) ControlUtils.dpToPx(context, 6f);
        sw.setLayoutParams(lp);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> onChange.accept(isChecked));
        parent.addView(sw);
    }

    private Button createColorButton(String label, int color) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(11.5f);
        btn.setTextColor(0xFFFFFFFF);
        updateColorSwatch(btn, color);
        return btn;
    }

    private void updateColorSwatch(Button btn, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setStroke(2, 0xFF888888);
        gd.setCornerRadius(ControlUtils.dpToPx(context, 8f));
        btn.setBackground(gd);
    }

    private void styleOrientChip(Button chip, boolean active) {
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

    public void show() {
        windowHost.show();
    }

    public void dismiss() {
        windowHost.dismiss();
    }
}
