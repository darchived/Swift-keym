package com.example.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.example.customcontrols.ShareBackupManager
import com.example.customcontrols.utils.ControlUtils
import com.example.input.LwjglGlfwKeycode
import com.example.vision.AiVisionTerminalBus
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
        fun onAimProfileUpdated()
        fun onReleaseAllKeysPanic()
        fun onGyroSettingsChanged()
        fun onCrosshairSettingsChanged()
        fun onHapticToggled(enabled: Boolean)
        fun onPositionsLockToggled(locked: Boolean)
        fun onExportControlMap()
        fun onStopServiceConfirmed()
        fun onDrawerVisibilityChanged(visible: Boolean)
    }

    fun refreshRecoilSlotUi() {
        if (isShowing && currentPage == Page.MAIN) {
            refreshContent(Page.MAIN)
        }
    }

    private enum class Page {
        MAIN,
        PRESETS,
        TURN_OFF_CONFIRM
    }

    private var rootWrapper: FrameLayout? = null
    private var rootParams: WindowManager.LayoutParams? = null
    private var currentPage = Page.MAIN

    private val aiTerminalHandler = Handler(Looper.getMainLooper())
    private var aiTerminalStatusTv: TextView? = null
    private var aiTerminalTargetTv: TextView? = null
    private var aiTerminalLogTv: TextView? = null

    private val aiTerminalPollRunnable = object : Runnable {
        override fun run() {
            if (!isShowing || currentPage != Page.MAIN) return
            updateAiTerminalViews()
            aiTerminalHandler.postDelayed(this, 350L)
        }
    }

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
        aiTerminalHandler.removeCallbacks(aiTerminalPollRunnable)
        aiTerminalStatusTv = null
        aiTerminalTargetTv = null
        aiTerminalLogTv = null
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
        aiTerminalHandler.removeCallbacks(aiTerminalPollRunnable)
        aiTerminalStatusTv = null
        aiTerminalTargetTv = null
        aiTerminalLogTv = null
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
            val ver = if (BuildConfig.VERSION_NAME.startsWith("v")) BuildConfig.VERSION_NAME else "v${BuildConfig.VERSION_NAME}"
            text = "$ver • StarDevs"
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

            addSwitchRow(list, "Snap-to-Grid & Guides", controlLayout.isSnapToGridEnabled, sp) { checked ->
                controlLayout.isSnapToGridEnabled = checked
                ControlLayoutManager.setSnapToGridEnabled(context, checked)
                callbacks.onAimProfileUpdated()
            }
            val alignRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, sp)
            }
            val alignGap = ControlUtils.dpToPx(context, 4f).toInt()
            val snapAllBtn = createPillButton("Snap All", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()).apply {
                setOnClickListener { controlLayout.snapAllToGrid() }
            }
            val centerHBtn = createPillButton("Center H", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()).apply {
                setOnClickListener { controlLayout.centerSelectedHorizontally() }
            }
            val centerVBtn = createPillButton("Center V", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()).apply {
                setOnClickListener { controlLayout.centerSelectedVertically() }
            }
            alignRow.addView(snapAllBtn, LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 32f).toInt(), 1f).apply { marginEnd = alignGap })
            alignRow.addView(centerHBtn, LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 32f).toInt(), 1f).apply { marginEnd = alignGap })
            alignRow.addView(centerVBtn, LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 32f).toInt(), 1f))
            list.addView(alignRow)
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
            label = "Horizontal Aim Sens (X)",
            min = 0.2f,
            max = 5.0f,
            step = 0.1f,
            initial = ControlLayoutManager.getAimSensX(context),
            sp = sp,
            format = "%.1fx"
        ) { value ->
            controlLayout.aimSensitivity = value
            ControlLayoutManager.setAimSensitivity(context, value)
            ControlLayoutManager.setAimSensX(context, value)
            callbacks.onAimProfileUpdated()
        }
        addFloatSliderRow(
            list,
            label = "Vertical Aim Sens (Y)",
            min = 0.2f,
            max = 5.0f,
            step = 0.1f,
            initial = ControlLayoutManager.getAimSensY(context),
            sp = sp,
            format = "%.1fx"
        ) { value ->
            ControlLayoutManager.setAimSensY(context, value)
            callbacks.onAimProfileUpdated()
        }
        addChipSelectorRow(
            list,
            labels = arrayOf("Scope 1x", "2x–4x", "6x+"),
            selectedIndex = ControlLayoutManager.getAimZoomLevel(context),
            sp = sp
        ) { idx ->
            ControlLayoutManager.setAimZoomLevel(context, idx)
            callbacks.onAimProfileUpdated()
        }
        addFloatSliderRow(
            list,
            label = "ADS Scope Multiplier",
            min = 0.30f,
            max = 1.20f,
            step = 0.05f,
            initial = ControlLayoutManager.getAimAdsMultiplier(context),
            sp = sp,
            format = "%.2fx"
        ) { mult ->
            ControlLayoutManager.setAimAdsMultiplier(context, mult)
        }
        addFloatSliderRow(
            list,
            label = "ADS Ramp Smooth",
            min = 0f,
            max = 250f,
            step = 10f,
            initial = ControlLayoutManager.getAimAdsRampMs(context).toFloat(),
            sp = sp,
            format = "%.0fms"
        ) { ms ->
            ControlLayoutManager.setAimAdsRampMs(context, ms.toInt())
        }
        addFloatSliderRow(
            list,
            label = "Sticky Aim (Micro-Track)",
            min = 0.30f,
            max = 0.85f,
            step = 0.05f,
            initial = ControlLayoutManager.getAimStickySlowdown(context),
            sp = sp,
            format = "%.2fx"
        ) { factor ->
            ControlLayoutManager.setAimStickySlowdown(context, factor)
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
        addFloatSliderRow(
            list,
            label = "Aim Inertia Glide",
            min = 0f,
            max = 0.85f,
            step = 0.05f,
            initial = ControlLayoutManager.getAimInertia(context),
            sp = sp,
            format = "%.0f%%",
            displayAsPercent = true
        ) { inertia ->
            ControlLayoutManager.setAimInertia(context, inertia)
        }
        addFloatSliderRow(
            list,
            label = "Edge-Guard Bezel Margin",
            min = 0f,
            max = 20f,
            step = 1f,
            initial = ControlLayoutManager.getAimEdgeGuardDp(context).toFloat(),
            sp = sp,
            format = "%.0fdp"
        ) { dp ->
            ControlLayoutManager.setAimEdgeGuardDp(context, dp.toInt())
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

        addDivider(list)
        val activeSlot = ControlLayoutManager.getActiveRecoilSlot(context)
        addSectionHeader(list, "3-STAGE ANTI-RECOIL (SLOT G$activeSlot)")

        addChipSelectorRow(
            list,
            labels = arrayOf("Weapon 1 (G1)", "Weapon 2 (G2)"),
            selectedIndex = activeSlot - 1,
            sp = sp
        ) { idx ->
            ControlLayoutManager.setActiveRecoilSlot(context, idx + 1)
            callbacks.onAimProfileUpdated()
            refreshContent(Page.MAIN)
        }

        addSwitchRow(
            list,
            "Only Compensate While ADS",
            ControlLayoutManager.isRecoilAdsOnly(context),
            sp
        ) { checked ->
            ControlLayoutManager.setRecoilAdsOnly(context, checked)
        }

        val earlyY = ControlLayoutManager.getRecoilEarlyY(context, activeSlot)
        val midY = ControlLayoutManager.getRecoilMidY(context, activeSlot)
        val lateY = ControlLayoutManager.getRecoilLateY(context, activeSlot)
        val driftX = ControlLayoutManager.getRecoilDriftX(context, activeSlot)

        val recoilPresetIdx = when {
            earlyY <= 0.001f -> 0
            Math.abs(earlyY - 0.15f) < 0.04f -> 1
            Math.abs(earlyY - 0.35f) < 0.04f -> 2
            Math.abs(earlyY - 0.65f) < 0.04f -> 3
            else -> -1
        }
        addChipSelectorRow(
            list,
            labels = arrayOf("Off (0.00)", "Low (0.15)", "Med (0.35)", "High (0.65)"),
            selectedIndex = recoilPresetIdx,
            sp = sp
        ) { idx ->
            val v = when (idx) {
                1 -> 0.15f
                2 -> 0.35f
                3 -> 0.65f
                else -> 0.00f
            }
            ControlLayoutManager.setRecoilEarlyY(context, activeSlot, v)
            ControlLayoutManager.setRecoilMidY(context, activeSlot, (v * 0.90f))
            ControlLayoutManager.setRecoilLateY(context, activeSlot, (v * 0.75f))
            refreshContent(Page.MAIN)
        }

        addFloatSliderRow(
            list,
            label = "Stage 1 Early (0–350ms)",
            min = 0.00f,
            max = 3.00f,
            step = 0.05f,
            initial = earlyY,
            sp = sp,
            format = "%.2f",
            valueFormatter = { v -> if (v <= 0.001f) "OFF" else String.format(Locale.US, "%.2f", v) }
        ) { v ->
            ControlLayoutManager.setRecoilEarlyY(context, activeSlot, v)
        }

        addFloatSliderRow(
            list,
            label = "Stage 2 Mid (350–1200ms)",
            min = 0.00f,
            max = 3.00f,
            step = 0.05f,
            initial = midY,
            sp = sp,
            format = "%.2f",
            valueFormatter = { v -> if (v <= 0.001f) "OFF" else String.format(Locale.US, "%.2f", v) }
        ) { v ->
            ControlLayoutManager.setRecoilMidY(context, activeSlot, v)
        }

        addFloatSliderRow(
            list,
            label = "Stage 3 Late (1200ms+)",
            min = 0.00f,
            max = 3.00f,
            step = 0.05f,
            initial = lateY,
            sp = sp,
            format = "%.2f",
            valueFormatter = { v -> if (v <= 0.001f) "OFF" else String.format(Locale.US, "%.2f", v) }
        ) { v ->
            ControlLayoutManager.setRecoilLateY(context, activeSlot, v)
        }

        addFloatSliderRow(
            list,
            label = "Horizontal Drift Comp",
            min = -1.50f,
            max = 1.50f,
            step = 0.05f,
            initial = driftX,
            sp = sp,
            format = "%+.2f",
            valueFormatter = { v ->
                when {
                    Math.abs(v) < 0.01f -> "CENTER (0.00)"
                    v < 0f -> String.format(Locale.US, "%.2f (Left)", v)
                    else -> String.format(Locale.US, "+%.2f (Right)", v)
                }
            }
        ) { drift ->
            ControlLayoutManager.setRecoilDriftX(context, activeSlot, drift)
        }

        addFloatSliderRow(
            list,
            label = "Initial Kick Boost (350ms)",
            min = 0.0f,
            max = 1.0f,
            step = 0.05f,
            initial = ControlLayoutManager.getAimInitialKickBoost(context),
            sp = sp,
            format = "%.0f%%",
            displayAsPercent = true
        ) { boost ->
            ControlLayoutManager.setAimInitialKickBoost(context, boost)
        }

        // Interactive Live Recoil Test Pad
        val testPad = RecoilTestPadView(context, activeSlot).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ControlUtils.dpToPx(context, 108f).toInt()
            ).apply {
                topMargin = sp
                bottomMargin = sp
            }
        }
        list.addView(testPad)

        addSwitchRow(
            list,
            "Auto-Pause Aim on ESC/Tab/M",
            ControlLayoutManager.isAutoPauseAimOnMenu(context),
            sp
        ) { checked ->
            ControlLayoutManager.setAutoPauseAimOnMenu(context, checked)
        }
        addDivider(list)

        // 4. AI SCREEN VISION AIM ASSIST
        addSectionHeader(list, "AI SCREEN VISION AIM ASSIST")
        val aiStrength = ControlLayoutManager.getAiAimStrength(context)
        val aiPresetIdx = when {
            aiStrength < 0.25f -> 0
            Math.abs(aiStrength - 1.2f) < 0.2f -> 1
            Math.abs(aiStrength - 2.5f) < 0.2f -> 2
            Math.abs(aiStrength - 4.2f) < 0.2f -> 3
            else -> -1
        }
        addChipSelectorRow(
            list,
            labels = arrayOf("Off", "Low (1.2)", "Med (2.5)", "High (4.2)"),
            selectedIndex = aiPresetIdx,
            sp = sp
        ) { idx ->
            val v = when (idx) {
                1 -> 1.2f
                2 -> 2.5f
                3 -> 4.2f
                else -> 0.0f
            }
            ControlLayoutManager.setAiAimStrength(context, v)
            callbacks.onAimProfileUpdated()
            AiVisionTerminalBus.appendLog(
                "DRAWER: AI Strength set to ${if (v < 0.5f) "OFF" else String.format(Locale.US, "%.1f", v)}"
            )
            refreshContent(Page.MAIN)
        }

        // Exact Off (0.0) -> 0.5..5.0 slider (46 steps: 0=OFF, 1..46=0.5..5.0)
        run {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(sp / 2, sp / 2, sp / 2, sp / 2)
            }
            val curStep = if (aiStrength < 0.25f) 0 else (1 + Math.round((aiStrength - 0.5f) * 10f)).coerceIn(1, 46)
            val tv = TextView(context).apply {
                text = "AI Aim Strength: ${if (aiStrength < 0.5f) "OFF" else String.format(Locale.US, "%.1f / 5.0", aiStrength)}"
                setTextColor(0xFFE0E0E0.toInt())
                textSize = 12f
            }
            val seek = SeekBar(context).apply {
                max = 46
                progress = curStep
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val next = if (progress <= 0) 0.0f else (0.5f + ((progress - 1) * 0.1f)).coerceIn(0.5f, 5.0f)
                        tv.text = "AI Aim Strength: ${if (next < 0.5f) "OFF" else String.format(Locale.US, "%.1f / 5.0", next)}"
                        ControlLayoutManager.setAiAimStrength(context, next)
                        callbacks.onAimProfileUpdated()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            box.addView(tv)
            box.addView(seek)
            list.addView(box)
        }

        addFloatSliderRow(
            list,
            label = "Crosshair Gate",
            min = 0.10f,
            max = 0.40f,
            step = 0.02f,
            initial = ControlLayoutManager.getAiCrosshairGatePct(context),
            sp = sp,
            format = "%.0f%%",
            displayAsPercent = true
        ) { pct ->
            ControlLayoutManager.setAiCrosshairGatePct(context, pct)
        }

        addSwitchRow(
            list,
            "Warzone Teammate Filter",
            ControlLayoutManager.isAiTeamFilterEnabled(context),
            sp
        ) { checked ->
            ControlLayoutManager.setAiTeamFilterEnabled(context, checked)
        }

        // Compact Live AI Vision Terminal directly below AI Aim Assist controls
        val termBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF121212.toInt())
                setStroke(1, 0x44FFFFFF)
                cornerRadius = ControlUtils.dpToPx(context, 8f)
            }
            val tp = ControlUtils.dpToPx(context, 8f).toInt()
            setPadding(tp, tp, tp, tp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = sp / 2
                bottomMargin = sp
            }
        }

        val termHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val termTitle = TextView(context).apply {
            text = "AI VISION TERMINAL"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val clearTermBtn = createPillButton("Clear", 0xFF2C2C2C.toInt(), 0xFFD0D0D0.toInt()).apply {
            textSize = 10f
            setOnClickListener {
                AiVisionTerminalBus.clearLogs()
                updateAiTerminalViews()
            }
        }
        termHeader.addView(termTitle)
        termHeader.addView(clearTermBtn)
        termBox.addView(termHeader)

        val statusTv = TextView(context).apply {
            setTextColor(0xFF69F0AE.toInt())
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 2)
        }
        val targetTv = TextView(context).apply {
            setTextColor(0xFFA8A8A8.toInt())
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 4)
        }
        val logsTv = TextView(context).apply {
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
        }
        aiTerminalStatusTv = statusTv
        aiTerminalTargetTv = targetTv
        aiTerminalLogTv = logsTv

        termBox.addView(statusTv)
        termBox.addView(targetTv)
        termBox.addView(logsTv)
        list.addView(termBox)

        updateAiTerminalViews()
        aiTerminalHandler.postDelayed(aiTerminalPollRunnable, 350L)

        addDivider(list)

        // 5. GYRO SETTINGS
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
        addSwitchRow(list, "Live Input HUD Pill", ControlLayoutManager.isLiveHudPillEnabled(context), sp) { checked ->
            ControlLayoutManager.setLiveHudPillEnabled(context, checked)
            callbacks.onAimProfileUpdated()
        }
        addSwitchRow(list, "Auto-Hide Outside Game", ControlLayoutManager.isAutoHideOutsideGame(context), sp) { checked ->
            ControlLayoutManager.setAutoHideOutsideGame(context, checked)
            callbacks.onAimProfileUpdated()
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

        // 6. PANIC, KEYBOARD, HIDE HUD & TURN OFF SERVICE
        addSpacer(list, 6f)
        val panicBtn = createFullWidthButton(
            label = "⚡ Release All Held Keys & Macros",
            bgColor = 0xFF333333.toInt(),
            textColor = 0xFFFFFFFF.toInt()
        ).apply {
            setOnClickListener {
                callbacks.onReleaseAllKeysPanic()
            }
        }
        list.addView(panicBtn)
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
        val saveBtn = createPillButton("Save", 0xFFFFFFFF.toInt(), 0xFF1E1E1E.toInt()).apply {
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
        val exportBtn = createPillButton("Copy JSON", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                callbacks.onExportControlMap()
            }
        }
        val shareBtn = createPillButton("SKM1://", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener {
                val code = ShareBackupManager.encodeShareCode(controlLayout.layout)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SwiftKeyM Preset", code))
                Toast.makeText(context, "Copied SKM1:// share code", Toast.LENGTH_SHORT).show()
            }
        }
        val gap = ControlUtils.dpToPx(context, 4f).toInt()
        actionRow.addView(
            saveBtn,
            LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 36f).toInt(), 1f).apply { marginEnd = gap }
        )
        actionRow.addView(
            exportBtn,
            LinearLayout.LayoutParams(0, ControlUtils.dpToPx(context, 36f).toInt(), 1f).apply { marginEnd = gap }
        )
        actionRow.addView(
            shareBtn,
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

    private fun updateAiTerminalViews() {
        val stTv = aiTerminalStatusTv ?: return
        val tgTv = aiTerminalTargetTv ?: return
        val lgTv = aiTerminalLogTv ?: return

        val active = AiVisionTerminalBus.isScreenVisionActive.value
        val headline = AiVisionTerminalBus.statusHeadline.value
        val target = AiVisionTerminalBus.activeTarget.value
        val logs = AiVisionTerminalBus.terminalLogs.value

        stTv.text = if (active) "● $headline" else "○ $headline (Start in Main App)"
        stTv.setTextColor(
            when {
                target?.isLocked == true -> 0xFFFF8A80.toInt()
                active -> 0xFF69F0AE.toInt()
                else -> 0xFFA8A8A8.toInt()
            }
        )
        tgTv.text = if (target != null) {
            "TARGET: ${target.targetName} | ${target.tagType} | OFF: (${target.dxPx},${target.dyPx})px | TEAM SKIP: ${target.teammatesSkipped}"
        } else {
            "TARGET: -- | TAG: NONE | OFF: (0,0)px | TEAM SKIP: 0"
        }
        lgTv.text = logs.takeLast(6).joinToString("\n")
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

    /**
     * Interactive Live Recoil Test Pad: touch & hold inside the canvas to simulate weapon kick
     * vs live 3-Stage compensation curve (Early 0–350ms, Mid 350–1200ms, Late 1200ms+).
     */
    @SuppressLint("ClickableViewAccessibility")
    private class RecoilTestPadView(
        context: Context,
        private val slot: Int
    ) : View(context) {

        private var isFiring = false
        private var startTimeMs = 0L
        private var dotX = 0f
        private var dotY = 0f
        private var rawY = 0f
        private val trail = Path()
        private val handler = Handler(Looper.getMainLooper())

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF161616.toInt()
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = ControlUtils.dpToPx(context, 1f)
        }
        private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
            strokeWidth = 1.5f
        }
        private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = ControlUtils.dpToPx(context, 2f)
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA8A8A8.toInt()
            textSize = ControlUtils.dpToPx(context, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
        }

        private val simTick = object : Runnable {
            override fun run() {
                if (!isFiring) return
                val elapsed = SystemClock.uptimeMillis() - startTimeMs
                val early = ControlLayoutManager.getRecoilEarlyY(context, slot)
                val mid = ControlLayoutManager.getRecoilMidY(context, slot)
                val late = ControlLayoutManager.getRecoilLateY(context, slot)
                val drift = ControlLayoutManager.getRecoilDriftX(context, slot)
                val boost = ControlLayoutManager.getAimInitialKickBoost(context)

                val stageY = when {
                    elapsed <= 350L -> early * (1f + boost)
                    elapsed <= 1200L -> mid
                    else -> late
                }
                // Simulated upward gun kick (0.45px/tick) minus user's downward anti-recoil
                val netClimb = 0.45f - stageY
                dotY = (dotY - netClimb * 2.2f).coerceIn(10f, height - 10f)
                dotX = (dotX + drift * 1.8f).coerceIn(10f, width - 10f)
                rawY = (rawY - 0.9f).coerceAtLeast(8f)
                trail.lineTo(dotX, dotY)
                invalidate()
                handler.postDelayed(this, 16L)
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            isFiring = false
            handler.removeCallbacks(simTick)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    isFiring = true
                    startTimeMs = SystemClock.uptimeMillis()
                    dotX = width / 2f
                    dotY = height * 0.72f
                    rawY = dotY
                    trail.reset()
                    trail.moveTo(dotX, dotY)
                    handler.removeCallbacks(simTick)
                    handler.post(simTick)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFiring = false
                    handler.removeCallbacks(simTick)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val r = ControlUtils.dpToPx(context, 8f)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, bgPaint)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, borderPaint)

            val cx = width / 2f
            val cy = height * 0.72f
            canvas.drawLine(cx - 24f, cy, cx + 24f, cy, crosshairPaint)
            canvas.drawLine(cx, cy - 24f, cx, cy + 24f, crosshairPaint)

            if (isFiring) {
                canvas.drawPath(trail, trailPaint)
                canvas.drawCircle(dotX, dotY, ControlUtils.dpToPx(context, 4.5f), dotPaint)
                val elapsed = SystemClock.uptimeMillis() - startTimeMs
                val stage = when {
                    elapsed <= 350L -> "STAGE 1: EARLY"
                    elapsed <= 1200L -> "STAGE 2: MID"
                    else -> "STAGE 3: LATE"
                }
                canvas.drawText("G$slot • $stage (${elapsed}ms)", 14f, 26f, textPaint)
            } else {
                canvas.drawCircle(cx, cy, ControlUtils.dpToPx(context, 4f), crosshairPaint)
                canvas.drawText("HOLD PAD TO TEST G$slot 3-STAGE RECOIL", 14f, 26f, textPaint)
            }
        }
    }
}
