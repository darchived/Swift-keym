package com.example.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.BuildConfig
import com.example.R
import com.example.customcontrols.ControlData
import com.example.customcontrols.ControlDrawerData
import com.example.customcontrols.ControlJoystickData
import com.example.customcontrols.ControlLayout
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.CustomControls
import com.example.customcontrols.utils.ControlUtils
import com.example.input.LwjglGlfwKeycode
import java.util.Locale

/**
 * Pure WindowManager-attached Floating Settings Drawer for OverlayService.
 * Uses zero Dialog / AlertDialog / PopupWindow instances so tapping the Gear (⚙)
 * button can never throw BadTokenException or crash the overlay service.
 */
class FloatingSettingsDrawerView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val controlLayout: ControlLayout,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun isAimEnabled(): Boolean
        fun onSetAimEnabled(enabled: Boolean)
        fun onToggleEditMode()
        fun onToggleHideHud()
        fun onToggleKeyboard()
        fun onAimSensitivitySet(sensitivity: Float)
        fun onGyroSettingsChanged()
        fun onCrosshairSettingsChanged()
        fun onHapticToggled(enabled: Boolean)
        fun onPositionsLockToggled(locked: Boolean)
        fun onExportControlMap()
        fun onStopServiceConfirmed()
        fun onDrawerVisibilityChanged(visible: Boolean)
    }

    private enum class Page {
        MAIN,
        PRESETS,
        TURN_OFF_CONFIRM
    }

    private var rootWrapper: FrameLayout? = null
    private var rootParams: WindowManager.LayoutParams? = null
    private var currentPage = Page.MAIN

    val isShowing: Boolean
        get() = rootWrapper?.isAttachedToWindow == true

    fun toggle() {
        if (isShowing) {
            dismiss()
        } else {
            show()
        }
    }

    fun show() {
        try {
            if (isShowing) {
                refreshContent(Page.MAIN)
                return
            }
            currentPage = Page.MAIN

            val wrapper = object : FrameLayout(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        if (currentPage != Page.MAIN) {
                            refreshContent(Page.MAIN)
                        } else {
                            dismiss()
                        }
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }
            wrapper.isFocusableInTouchMode = true

            val drawerWidth = ControlUtils.dpToPx(context, 304f).toInt()
            val screenW = ControlUtils.getScreenWidth(context)
            val screenH = ControlUtils.getScreenHeight(context)
            val safeWidth = drawerWidth.coerceAtMost((screenW * 0.86f).toInt().coerceAtLeast(240))
            val safeHeight = (screenH * 0.90f).toInt().coerceAtLeast(320)

            val params = WindowManager.LayoutParams(
                safeWidth,
                safeHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseFlags(focusableForIme = false),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = ControlUtils.dpToPx(context, 8f).toInt()
                y = 0
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            rootWrapper = wrapper
            rootParams = params
            buildPageIntoWrapper(wrapper, Page.MAIN)

            windowManager.addView(wrapper, params)
            callbacks.onDrawerVisibilityChanged(true)
        } catch (t: Throwable) {
            rootWrapper = null
            rootParams = null
            callbacks.onDrawerVisibilityChanged(false)
            try {
                Toast.makeText(context, "Unable to open menu: ${t.message ?: "overlay error"}", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
            }
        }
    }

    fun dismiss() {
        val wrapper = rootWrapper
        rootWrapper = null
        rootParams = null
        currentPage = Page.MAIN
        if (wrapper != null && wrapper.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(wrapper)
            } catch (_: Throwable) {
            }
        }
        callbacks.onDrawerVisibilityChanged(false)
    }

    private fun baseFlags(focusableForIme: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!focusableForIme) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return flags
    }

    private fun updateImeFocusability(focusableForIme: Boolean) {
        val wrapper = rootWrapper ?: return
        val params = rootParams ?: return
        params.flags = baseFlags(focusableForIme)
        if (wrapper.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(wrapper, params)
            } catch (_: Throwable) {
            }
        }
    }

    private fun refreshContent(page: Page) {
        val wrapper = rootWrapper ?: return
        currentPage = page
        updateImeFocusability(focusableForIme = (page == Page.PRESETS))
        buildPageIntoWrapper(wrapper, page)
    }

    private fun buildPageIntoWrapper(wrapper: FrameLayout, page: Page) {
        wrapper.removeAllViews()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF2242424.toInt())
                setStroke(ControlUtils.dpToPx(context, 1.2f).toInt().coerceAtLeast(1), 0x55FFFFFF)
                cornerRadius = ControlUtils.dpToPx(context, 16f)
            }
            val p = ControlUtils.dpToPx(context, 12f).toInt()
            setPadding(p, p, p, p)
            elevation = ControlUtils.dpToPx(context, 14f)
        }

        when (page) {
            Page.MAIN -> buildMainPage(card)
            Page.PRESETS -> buildPresetsPage(card)
            Page.TURN_OFF_CONFIRM -> buildTurnOffConfirmPage(card)
        }

        wrapper.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun buildHeader(
        parent: LinearLayout,
        titleText: String,
        showBackButton: Boolean
    ) {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, ControlUtils.dpToPx(context, 8f).toInt())
        }

        if (showBackButton) {
            val backBtn = createPillButton("Back", bgColor = 0xFF363636.toInt(), textColor = 0xFFFFFFFF.toInt()).apply {
                setOnClickListener { refreshContent(Page.MAIN) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ControlUtils.dpToPx(context, 32f).toInt()
            ).apply {
                marginEnd = ControlUtils.dpToPx(context, 8f).toInt()
            }
            header.addView(backBtn, lp)
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(context).apply {
            text = titleText
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })
        titleCol.addView(TextView(context).apply {
            text = "v${BuildConfig.VERSION_NAME} • StarDevs"
            setTextColor(0xFFA8A8A8.toInt())
            textSize = 10.5f
        })
        header.addView(titleCol)

        val closeBtn = createPillButton("Close", bgColor = 0xFF363636.toInt(), textColor = 0xFFFFFFFF.toInt()).apply {
            setOnClickListener { dismiss() }
        }
        header.addView(
            closeBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ControlUtils.dpToPx(context, 32f).toInt()
            )
        )

        parent.addView(header)
        addDivider(parent)
    }

    private fun buildMainPage(root: LinearLayout) {
        buildHeader(root, "Swift KeyM Menu", showBackButton = false)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val topPad = ControlUtils.dpToPx(context, 6f).toInt()
            setPadding(0, topPad, 0, topPad)
        }
        scroll.addView(list)
        root.addView(scroll)

        val sp = ControlUtils.dpToPx(context, 6f).toInt()

        // 1. EDIT CONTROLS & QUICK ACTIONS
        val isEditing = controlLayout.isModifiable
        val editBtn = createFullWidthButton(
            label = if (isEditing) "Done Editing (Play Mode)" else "Edit Controls Live",
            bgColor = if (isEditing) 0xFFFFFFFF.toInt() else 0xFF3A3A3A.toInt(),
            textColor = if (isEditing) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        ).apply {
            setOnClickListener {
                callbacks.onToggleEditMode()
                dismiss()
            }
        }
        list.addView(editBtn)

        if (isEditing) {
            val histRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, sp, 0, 0)
            }
            val undoBtn = createPillButton("Undo", 0xFF333333.toInt(), 0xFFFFFFFF.toInt())
            val redoBtn = createPillButton("Redo", 0xFF333333.toInt(), 0xFFFFFFFF.toInt())
            updateEnabledVisual(undoBtn, controlLayout.canUndo())
            updateEnabledVisual(redoBtn, controlLayout.canRedo())

            undoBtn.setOnClickListener {
                controlLayout.undo()
                updateEnabledVisual(undoBtn, controlLayout.canUndo())
                updateEnabledVisual(redoBtn, controlLayout.canRedo())
            }
            redoBtn.setOnClickListener {
                controlLayout.redo()
                updateEnabledVisual(undoBtn, controlLayout.canUndo())
                updateEnabledVisual(redoBtn, controlLayout.canRedo())
            }

            val h1 = LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 34f).toInt(), 1f).apply {
                marginEnd = ControlUtils.dpToPx(context, 4f).toInt()
            }
            val h2 = LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 34f).toInt(), 1f)
            histRow.addView(undoBtn, h1)
            histRow.addView(redoBtn, h2)
            list.addView(histRow)
        }

        // Quick Add Row (+ Button / + Drawer / + Joystick)
        val addRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, sp, 0, sp)
        }
        val addBtn = createPillButton("+ Button", 0xFF2E2E2E.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                controlLayout.isModifiable = true
                controlLayout.addControlButton(
                    ControlData(
                        "New",
                        intArrayOf(0, 0, 0, 0),
                        "0.50 * \${screen_width}",
                        "0.50 * \${screen_height}",
                        64f,
                        56f
                    )
                )
                controlLayout.notifyLayoutModified()
                dismiss()
            }
        }
        val addDrawerBtn = createPillButton("+ Drawer", 0xFF2E2E2E.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                controlLayout.isModifiable = true
                val drawer = ControlDrawerData()
                drawer.properties = ControlData(
                    "Drawer",
                    intArrayOf(0, 0, 0, 0),
                    "0.50 * \${screen_width}",
                    "0.30 * \${screen_height}",
                    68f,
                    44f
                )
                drawer.buttonProperties.add(
                    ControlData("1", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_1, 0, 0, 0), "0", "0", 52f, 40f)
                )
                drawer.buttonProperties.add(
                    ControlData("2", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_2, 0, 0, 0), "0", "0", 52f, 40f)
                )
                controlLayout.addControlDrawer(drawer)
                controlLayout.notifyLayoutModified()
                dismiss()
            }
        }
        val addStickBtn = createPillButton("+ Stick", 0xFF2E2E2E.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                controlLayout.isModifiable = true
                val joy = ControlJoystickData()
                joy.dynamicX = "0.14 * \${screen_width}"
                joy.dynamicY = "0.50 * \${screen_height}"
                controlLayout.addControlJoystick(joy)
                controlLayout.notifyLayoutModified()
                dismiss()
            }
        }
        val gap = ControlUtils.dpToPx(context, 4f).toInt()
        val aLp1 = LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 34f).toInt(), 1f).apply { marginEnd = gap }
        val aLp2 = LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 34f).toInt(), 1f).apply { marginEnd = gap }
        val aLp3 = LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 34f).toInt(), 1f)
        addRow.addView(addBtn, aLp1)
        addRow.addView(addDrawerBtn, aLp2)
        addRow.addView(addStickBtn, aLp3)
        list.addView(addRow)

        // 2. PRESETS BUTTON (Inline Page Navigation)
        val activePresetLabel = ControlLayoutManager.getActiveLayoutName(context)
            .removeSuffix(".json")
            .replace("_", " ")
        val presetsBtn = createFullWidthButton(
            label = "Presets  ($activePresetLabel)  >",
            bgColor = 0xFF333333.toInt(),
            textColor = 0xFFFFFFFF.toInt()
        ).apply {
            setOnClickListener { refreshContent(Page.PRESETS) }
        }
        list.addView(presetsBtn)
        addSpacer(list, 6f)
        addDivider(list)

        // 3. AIM LOOK & SENSITIVITY
        addSectionHeader(list, "AIM LOOK & SENSITIVITY")
        addSwitchRow(list, "3D Aim Swipe Zone", callbacks.isAimEnabled(), sp) { checked ->
            callbacks.onSetAimEnabled(checked)
        }
        addFloatSliderRow(
            list,
            label = "Aim Sensitivity",
            min = 0.2f,
            max = 5.0f,
            step = 0.1f,
            initial = controlLayout.aimSensitivity,
            sp = sp,
            format = "%.1fx"
        ) { value ->
            controlLayout.aimSensitivity = value
            ControlLayoutManager.setAimSensitivity(context, value)
            callbacks.onAimSensitivitySet(value)
        }
        addFloatSliderRow(
            list,
            label = "Head-Level Stabilizer",
            min = 0f,
            max = 0.80f,
            step = 0.05f,
            initial = ControlLayoutManager.getAimVertDampening(context),
            sp = sp,
            format = "%.0f%%",
            displayAsPercent = true
        ) { dampening ->
            ControlLayoutManager.setAimVertDampening(context, dampening)
        }
        addSwitchRow(
            list,
            "Speed Acceleration",
            ControlLayoutManager.isAimSpeedAccelEnabled(context),
            sp
        ) { checked ->
            ControlLayoutManager.setAimSpeedAccelEnabled(context, checked)
        }
        addFloatSliderRow(
            list,
            label = "Speed Accel Boost",
            min = 1.0f,
            max = 3.0f,
            step = 0.1f,
            initial = ControlLayoutManager.getAimSpeedAccelMultiplier(context),
            sp = sp,
            format = "%.1fx"
        ) { mult ->
            ControlLayoutManager.setAimSpeedAccelMultiplier(context, mult)
        }
        addFloatSliderRow(
            list,
            label = "Anti-Recoil Pull-Down",
            min = 0f,
            max = 20f,
            step = 1f,
            initial = ControlLayoutManager.getAimAntiRecoil(context).toFloat(),
            sp = sp,
            format = "%.0f",
            valueFormatter = { v -> if (v.toInt() == 0) "OFF" else "${v.toInt()}" }
        ) { recoil ->
            ControlLayoutManager.setAimAntiRecoil(context, Math.round(recoil))
        }
        addSwitchRow(
            list,
            "Auto-Pause Aim on ESC/Tab/M",
            ControlLayoutManager.isAutoPauseAimOnMenu(context),
            sp
        ) { checked ->
            ControlLayoutManager.setAutoPauseAimOnMenu(context, checked)
        }
        addDivider(list)

        // 4. GYRO SETTINGS
        addSectionHeader(list, "GYRO SETTINGS")
        addSwitchRow(
            list,
            "Gyroscope Aiming",
            ControlLayoutManager.isGyroEnabled(context),
            sp
        ) { checked ->
            ControlLayoutManager.setGyroEnabled(context, checked)
            callbacks.onGyroSettingsChanged()
        }
        addChipSelectorRow(
            list,
            labels = arrayOf("Always On", "ADS Only", "Firing"),
            selectedIndex = ControlLayoutManager.getGyroMode(context),
            sp = sp
        ) { idx ->
            ControlLayoutManager.setGyroMode(context, idx)
            callbacks.onGyroSettingsChanged()
        }
        addFloatSliderRow(
            list,
            label = "Gyro X Sens",
            min = 0.2f,
            max = 8.0f,
            step = 0.2f,
            initial = ControlLayoutManager.getGyroSensX(context),
            sp = sp,
            format = "%.1fx"
        ) { valX ->
            ControlLayoutManager.setGyroSensX(context, valX)
            callbacks.onGyroSettingsChanged()
        }
        addFloatSliderRow(
            list,
            label = "Gyro Y Sens",
            min = 0.2f,
            max = 8.0f,
            step = 0.2f,
            initial = ControlLayoutManager.getGyroSensY(context),
            sp = sp,
            format = "%.1fx"
        ) { valY ->
            ControlLayoutManager.setGyroSensY(context, valY)
            callbacks.onGyroSettingsChanged()
        }
        addSwitchRow(list, "Invert Gyro X-Axis", ControlLayoutManager.isGyroInvertX(context), sp) { checked ->
            ControlLayoutManager.setGyroInvertX(context, checked)
            callbacks.onGyroSettingsChanged()
        }
        addSwitchRow(list, "Invert Gyro Y-Axis", ControlLayoutManager.isGyroInvertY(context), sp) { checked ->
            ControlLayoutManager.setGyroInvertY(context, checked)
            callbacks.onGyroSettingsChanged()
        }
        addFloatSliderRow(
            list,
            label = "Anti-Shake Smoothing",
            min = 0f,
            max = 1.0f,
            step = 0.05f,
            initial = ControlLayoutManager.getGyroSmoothing(context),
            sp = sp,
            format = "%.0f%%",
            displayAsPercent = true
        ) { smooth ->
            ControlLayoutManager.setGyroSmoothing(context, smooth)
            callbacks.onGyroSettingsChanged()
        }
        addDivider(list)

        // 5. CROSSHAIR & HUD
        addSectionHeader(list, "CROSSHAIR & HUD")
        addSwitchRow(list, "Center Crosshair Overlay", ControlLayoutManager.isCrosshairEnabled(context), sp) { checked ->
            ControlLayoutManager.setCrosshairEnabled(context, checked)
            callbacks.onCrosshairSettingsChanged()
        }
        addChipSelectorRow(
            list,
            labels = arrayOf("Cross+", "Dot", "Cross", "Ring"),
            selectedIndex = ControlLayoutManager.getCrosshairStyle(context),
            sp = sp
        ) { idx ->
            ControlLayoutManager.setCrosshairStyle(context, idx)
            if (!ControlLayoutManager.isCrosshairEnabled(context)) {
                ControlLayoutManager.setCrosshairEnabled(context, true)
            }
            callbacks.onCrosshairSettingsChanged()
        }

        val currentOpacityPct = Math.round(controlLayout.globalOpacity * 100f)
        addFloatSliderRow(
            list,
            label = "Global Opacity",
            min = 15f,
            max = 100f,
            step = 1f,
            initial = currentOpacityPct.toFloat(),
            sp = sp,
            format = "%.0f%%"
        ) { pct ->
            val v = (pct / 100f).coerceIn(0.15f, 1.0f)
            controlLayout.globalOpacity = v
            ControlLayoutManager.setGlobalOpacity(context, v)
        }

        addSwitchRow(list, context.getString(R.string.cog_touch_ripple), controlLayout.isTouchRippleEnabled, sp) { checked ->
            controlLayout.isTouchRippleEnabled = checked
            ControlLayoutManager.setTouchRippleEnabled(context, checked)
        }
        addSwitchRow(list, "Tactile Haptics (12ms)", controlLayout.isHapticEnabled, sp) { checked ->
            controlLayout.isHapticEnabled = checked
            ControlLayoutManager.setHapticEnabled(context, checked)
            callbacks.onHapticToggled(checked)
        }
        addSwitchRow(list, "Lock Button Positions", controlLayout.isPositionsLocked, sp) { checked ->
            controlLayout.isPositionsLocked = checked
            ControlLayoutManager.setPositionsLocked(context, checked)
            callbacks.onPositionsLockToggled(checked)
        }
        addSwitchRow(list, "Remember Last Preset", ControlLayoutManager.isAutoLoadLastPreset(context), sp) { checked ->
            ControlLayoutManager.setAutoLoadLastPreset(context, checked)
        }
        addDivider(list)

        // 6. KEYBOARD, HIDE HUD & TURN OFF SERVICE
        addSpacer(list, 6f)
        val kbBtn = createFullWidthButton(
            label = "Virtual Keyboard (⌨)",
            bgColor = 0xFF333333.toInt(),
            textColor = 0xFFFFFFFF.toInt()
        ).apply {
            setOnClickListener {
                dismiss()
                callbacks.onToggleKeyboard()
            }
        }
        list.addView(kbBtn)
        addSpacer(list, 6f)

        val hideBtn = createFullWidthButton(
            label = context.getString(R.string.cog_hide_hud),
            bgColor = 0xFF333333.toInt(),
            textColor = 0xFFFFFFFF.toInt()
        ).apply {
            setOnClickListener {
                dismiss()
                callbacks.onToggleHideHud()
            }
        }
        list.addView(hideBtn)
        addSpacer(list, 6f)

        val turnOffBtn = createFullWidthButton(
            label = "Turn Off Service",
            bgColor = 0xFF3D2424.toInt(),
            textColor = 0xFFFF8A80.toInt()
        ).apply {
            setOnClickListener { refreshContent(Page.TURN_OFF_CONFIRM) }
        }
        list.addView(turnOffBtn)
    }

    private fun buildPresetsPage(root: LinearLayout) {
        buildHeader(root, "Presets & Profiles", showBackButton = true)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val topPad = ControlUtils.dpToPx(context, 6f).toInt()
            setPadding(0, topPad, 0, topPad)
        }
        scroll.addView(list)
        root.addView(scroll)

        // Save Active & Export JSON row
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, ControlUtils.dpToPx(context, 8f).toInt())
        }
        val saveBtn = createPillButton("Save Current", 0xFFFFFFFF.toInt(), 0xFF1E1E1E.toInt()).apply {
            setOnClickListener {
                ControlLayoutManager.saveActiveLayout(context, controlLayout.layout)
                controlLayout.markSaved()
                Toast.makeText(
                    context,
                    "Saved ${ControlLayoutManager.getActiveLayoutName(context)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        val exportBtn = createPillButton("Export JSON", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                callbacks.onExportControlMap()
            }
        }
        val gap = ControlUtils.dpToPx(context, 6f).toInt()
        actionRow.addView(
            saveBtn,
            LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 36f).toInt(), 1f).apply { marginEnd = gap }
        )
        actionRow.addView(
            exportBtn,
            LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 36f).toInt(), 1f)
        )
        list.addView(actionRow)

        // Inline Create New Preset Box
        addSectionHeader(list, "CREATE NEW PRESET")
        val createCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1B1B1B.toInt())
                setStroke(1, 0x33FFFFFF)
                cornerRadius = ControlUtils.dpToPx(context, 10f)
            }
            val cp = ControlUtils.dpToPx(context, 8f).toInt()
            setPadding(cp, cp, cp, cp)
        }
        val nameInput = EditText(context).apply {
            hint = "Preset name (e.g. codm_3finger)"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
            textSize = 12.5f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.bg_input_field)
            val hp = ControlUtils.dpToPx(context, 8f).toInt()
            setPadding(hp, hp / 2, hp, hp / 2)
        }
        val gameInput = EditText(context).apply {
            hint = "Game label (e.g. Warzone Mobile)"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
            textSize = 12.5f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.bg_input_field)
            val hp = ControlUtils.dpToPx(context, 8f).toInt()
            setPadding(hp, hp / 2, hp, hp / 2)
        }
        val createBtn = createFullWidthButton("Create & Save Current HUD", 0xFF3E3E3E.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                val raw = nameInput.text.toString().trim().ifEmpty { "preset_${System.currentTimeMillis()}" }
                val game = gameInput.text.toString().trim()
                if (game.isNotEmpty()) {
                    controlLayout.layout.gameName = game
                }
                val savedFile = ControlLayoutManager.saveLayoutAs(context, raw, controlLayout.layout)
                controlLayout.markSaved()
                Toast.makeText(context, "Saved preset $savedFile", Toast.LENGTH_SHORT).show()
                refreshContent(Page.PRESETS)
            }
        }
        createCard.addView(nameInput)
        addSpacer(createCard, 6f)
        createCard.addView(gameInput)
        addSpacer(createCard, 8f)
        createCard.addView(createBtn)
        list.addView(createCard)

        addSpacer(list, 8f)
        addSectionHeader(list, "SAVED PRESETS")

        val activeFile = ControlLayoutManager.getActiveLayoutName(context)
        val profiles = ControlLayoutManager.listLayouts(context)
        profiles.forEach { info ->
            val isActive = info.fileName == activeFile
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(if (isActive) 0xFF3A3A3A.toInt() else 0xFF1E1E1E.toInt())
                    setStroke(if (isActive) 2 else 1, if (isActive) 0xFFFFFFFF.toInt() else 0x33FFFFFF)
                    cornerRadius = ControlUtils.dpToPx(context, 9f)
                }
                val p = ControlUtils.dpToPx(context, 10f).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    try {
                        val loaded = ControlLayoutManager.loadLayoutByName(context, info.fileName)
                        ControlLayoutManager.setActiveLayoutName(context, info.fileName)
                        controlLayout.loadLayout(loaded)
                        Toast.makeText(context, "Loaded ${info.displayName}", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } catch (_: Throwable) {
                        Toast.makeText(context, "Failed to load ${info.displayName}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row.addView(TextView(context).apply {
                text = if (isActive) "${info.displayName}  (Active)" else info.displayName
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
            })
            row.addView(TextView(context).apply {
                text = "${info.gameName} • ${info.buttonCount} btns, ${info.joystickCount} stick"
                setTextColor(0xFFA8A8A8.toInt())
                textSize = 11f
            })
            val rLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = ControlUtils.dpToPx(context, 6f).toInt()
            }
            list.addView(row, rLp)
        }

        addSpacer(list, 6f)
        addSectionHeader(list, "RESET / BUILT-IN TEMPLATES")
        val builtIns = listOf(
            Triple("Warzone / CODM (FPS)", ControlLayoutManager.DEFAULT_FPS_FILE) {
                ControlLayoutManager.createDefaultFpsPreset()
            },
            Triple("Minecraft Java (Hotbar + WASD)", ControlLayoutManager.DEFAULT_MINECRAFT_FILE) {
                ControlLayoutManager.createMinecraftJavaPreset()
            },
            Triple("Action / RPG (Q/E/R + Stick)", ControlLayoutManager.DEFAULT_ACTION_FILE) {
                ControlLayoutManager.createDefaultActionPreset()
            },
            Triple("Tactical Arena Shooter", ControlLayoutManager.DEFAULT_ARENA_FILE) {
                ControlLayoutManager.createArenaShooterPreset()
            },
            Triple("MOBA / ARPG (Q/W/E/R + D/F)", ControlLayoutManager.DEFAULT_MOBA_FILE) {
                ControlLayoutManager.createMobaArpgPreset()
            },
            Triple("Blank Canvas", ControlLayoutManager.DEFAULT_BLANK_FILE) {
                ControlLayoutManager.createBlankCanvasPreset()
            }
        )
        builtIns.forEach { (title, fileName, factory) ->
            val btn = createFullWidthButton(title, 0xFF2B2B2B.toInt(), 0xFFE0E0E0.toInt()).apply {
                setOnClickListener {
                    val preset: CustomControls = factory()
                    ControlLayoutManager.saveLayoutAs(context, fileName, preset)
                    controlLayout.loadLayout(preset)
                    Toast.makeText(context, "Loaded $title", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
            list.addView(btn)
            addSpacer(list, 5f)
        }
    }

    private fun buildTurnOffConfirmPage(root: LinearLayout) {
        buildHeader(root, "Turn Off Service", showBackButton = true)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            val p = ControlUtils.dpToPx(context, 8f).toInt()
            setPadding(p, p, p, p)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1A1A1A.toInt())
                setStroke(1, 0x44FFFFFF)
                cornerRadius = ControlUtils.dpToPx(context, 12f)
            }
            val cp = ControlUtils.dpToPx(context, 14f).toInt()
            setPadding(cp, cp, cp, cp)
        }

        card.addView(TextView(context).apply {
            text = "Stop Swift KeyM Overlay?"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
        })
        addSpacer(card, 8f)
        card.addView(TextView(context).apply {
            text = "This saves any unsaved layout changes and stops the floating HUD service until started again."
            setTextColor(0xFFD0D0D0.toInt())
            textSize = 12.5f
        })
        addSpacer(card, 16f)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val cancelBtn = createPillButton("Cancel", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener { refreshContent(Page.MAIN) }
        }
        val confirmBtn = createPillButton("Turn Off", 0xFFFF5252.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                if (controlLayout.hasUnsavedChanges()) {
                    ControlLayoutManager.saveActiveLayout(context, controlLayout.layout)
                    controlLayout.markSaved()
                }
                dismiss()
                callbacks.onStopServiceConfirmed()
            }
        }
        val gap = ControlUtils.dpToPx(context, 8f).toInt()
        btnRow.addView(
            cancelBtn,
            LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 40f).toInt(), 1f).apply { marginEnd = gap }
        )
        btnRow.addView(
            confirmBtn,
            LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 40f).toInt(), 1f)
        )
        card.addView(btnRow)
        body.addView(card)
        root.addView(body)
    }

    private fun addSectionHeader(parent: LinearLayout, title: String) {
        parent.addView(TextView(context).apply {
            text = title
            setTextColor(0xFFA8A8A8.toInt())
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            val hp = ControlUtils.dpToPx(context, 4f).toInt()
            val top = ControlUtils.dpToPx(context, 8f).toInt()
            setPadding(hp, top, hp, 4)
        })
    }

    private fun addSwitchRow(
        parent: LinearLayout,
        label: String,
        initial: Boolean,
        sp: Int,
        onChange: (Boolean) -> Unit
    ) {
        val sw = Switch(context).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12.5f
            isChecked = initial
            setPadding(sp / 2, sp, sp / 2, sp)
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        parent.addView(sw)
    }

    private fun addFloatSliderRow(
        parent: LinearLayout,
        label: String,
        min: Float,
        max: Float,
        step: Float,
        initial: Float,
        sp: Int,
        format: String,
        displayAsPercent: Boolean = false,
        valueFormatter: ((Float) -> String)? = null,
        onChange: (Float) -> Unit
    ) {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sp / 2, sp / 2, sp / 2, sp / 2)
        }
        val tv = TextView(context).apply {
            val disp = if (displayAsPercent) initial * 100f else initial
            text = if (valueFormatter != null) {
                "$label: ${valueFormatter(disp)}"
            } else {
                "$label: ${String.format(Locale.US, format, disp)}"
            }
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 12f
        }
        val stepsCount = Math.max(1, Math.round((max - min) / step))
        val seek = SeekBar(context).apply {
            this.max = stepsCount
            progress = ControlUtils.clamp(Math.round((initial - min) / step), 0, stepsCount)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = ControlUtils.clamp(min + (progress * step), min, max)
                    val disp = if (displayAsPercent) value * 100f else value
                    tv.text = if (valueFormatter != null) {
                        "$label: ${valueFormatter(disp)}"
                    } else {
                        "$label: ${String.format(Locale.US, format, disp)}"
                    }
                    onChange(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        box.addView(tv)
        box.addView(seek)
        parent.addView(box)
    }

    private fun addChipSelectorRow(
        parent: LinearLayout,
        labels: Array<String>,
        selectedIndex: Int,
        sp: Int,
        onSelect: (Int) -> Unit
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(sp / 2, 4, sp / 2, sp)
        }
        val buttons = ArrayList<Button>(labels.size)
        labels.forEachIndexed { idx, label ->
            val chip = Button(context).apply {
                text = label
                isAllCaps = false
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                minWidth = 0
                minHeight = 0
                stateListAnimator = null
                setPadding(4, 2, 4, 2)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ControlUtils.dpToPx(context, 30f).toInt(),
                    1f
                ).apply {
                    if (idx < labels.lastIndex) {
                        marginEnd = ControlUtils.dpToPx(context, 4f).toInt()
                    }
                }
                styleChip(this, idx == selectedIndex)
                setOnClickListener {
                    buttons.forEachIndexed { bIdx, b -> styleChip(b, bIdx == idx) }
                    onSelect(idx)
                }
            }
            buttons.add(chip)
            row.addView(chip)
        }
        parent.addView(row)
    }

    private fun styleChip(chip: Button, active: Boolean) {
        val gd = GradientDrawable().apply {
            cornerRadius = ControlUtils.dpToPx(context, 6f)
            if (active) {
                setColor(0xFFFFFFFF.toInt())
                setStroke(1, 0xFFFFFFFF.toInt())
            } else {
                setColor(0xFF2C2C2C.toInt())
                setStroke(1, 0x44FFFFFF)
            }
        }
        chip.background = gd
        chip.setTextColor(if (active) 0xFF1E1E1E.toInt() else 0xFFD0D0D0.toInt())
    }

    private fun createFullWidthButton(label: String, bgColor: Int, textColor: Int): Button {
        return createPillButton(label, bgColor, textColor).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ControlUtils.dpToPx(context, 38f).toInt()
            )
        }
    }

    private fun createPillButton(label: String, bgColor: Int, textColor: Int): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            val hp = ControlUtils.dpToPx(context, 10f).toInt()
            setPadding(hp, 4, hp, 4)
            background = GradientDrawable().apply {
                setColor(bgColor)
                setStroke(1, 0x33FFFFFF)
                cornerRadius = ControlUtils.dpToPx(context, 8f)
            }
        }
    }

    private fun updateEnabledVisual(btn: Button, enabled: Boolean) {
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1.0f else 0.38f
    }

    private fun addDivider(parent: LinearLayout) {
        val div = View(context).apply {
            setBackgroundColor(0x2EFFFFFF)
        }
        parent.addView(
            div,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        )
    }

    private fun addSpacer(parent: LinearLayout, dp: Float) {
        parent.addView(
            View(context),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ControlUtils.dpToPx(context, dp).toInt()
            )
        )
    }
}
