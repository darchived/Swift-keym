package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.utils.ControlUtils
import com.example.input.InputBridge
import com.example.input.LwjglGlfwKeycode

/**
 * WindowManager-attached Floating QWERTY Typing Keyboard for OverlayService.
 * Styled in Grey & White theme (#1E1E1E background, #2C2C2C keys, #FFFFFF text).
 */
class FloatingKeyboardDrawerView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onClose: () -> Unit
) {
    private var rootLayout: LinearLayout? = null
    private var rootParams: WindowManager.LayoutParams? = null
    private var isShiftActive = false

    val isShowing: Boolean
        get() = rootLayout?.isAttachedToWindow == true

    fun toggle() {
        if (isShowing) {
            dismiss()
        } else {
            show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return

        val screenW = ControlUtils.getScreenWidth(context)
        val screenH = ControlUtils.getScreenHeight(context)
        val kbWidth = screenW.coerceAtMost(ControlUtils.dpToPx(context, 680f).toInt())
        val kbHeight = ControlUtils.dpToPx(context, 236f).toInt()
        val maxX = (screenW - kbWidth).coerceAtLeast(0)
        val maxY = (screenH - kbHeight).coerceAtLeast(0)

        val defaultX = ((screenW - kbWidth) / 2).coerceIn(0, maxX)
        val defaultY = (screenH - kbHeight - ControlUtils.dpToPx(context, 20f).toInt()).coerceIn(0, maxY)
        val savedX = ControlLayoutManager.getKeyboardPosX(context, defaultX).coerceIn(0, maxX)
        val savedY = ControlLayoutManager.getKeyboardPosY(context, defaultY).coerceIn(0, maxY)

        val params = WindowManager.LayoutParams(
            kbWidth,
            kbHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF21E1E1E.toInt())
                setStroke(ControlUtils.dpToPx(context, 1.5f).toInt().coerceAtLeast(1), 0x55FFFFFF)
                cornerRadius = ControlUtils.dpToPx(context, 12f)
            }
            val p = ControlUtils.dpToPx(context, 8f).toInt()
            setPadding(p, p, p, p)
            elevation = ControlUtils.dpToPx(context, 16f)
        }

        val dragTouchListener = object : View.OnTouchListener {
            private var startWindowX = 0
            private var startWindowY = 0
            private var startRawX = 0f
            private var startRawY = 0f
            private var activePointerId = MotionEvent.INVALID_POINTER_ID

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activePointerId = event.getPointerId(0)
                        startWindowX = params.x
                        startWindowY = params.y
                        startRawX = event.rawX
                        startRawY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
                        val dx = (event.rawX - startRawX).toInt()
                        val dy = (event.rawY - startRawY).toInt()
                        val curMaxX = (ControlUtils.getScreenWidth(context) - params.width).coerceAtLeast(0)
                        val curMaxY = (ControlUtils.getScreenHeight(context) - params.height).coerceAtLeast(0)
                        params.x = (startWindowX + dx).coerceIn(0, curMaxX)
                        params.y = (startWindowY + dy).coerceIn(0, curMaxY)
                        if (layout.isAttachedToWindow) {
                            try {
                                windowManager.updateViewLayout(layout, params)
                            } catch (_: Throwable) {
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        ControlLayoutManager.setKeyboardPos(context, params.x, params.y)
                        return true
                    }
                }
                return false
            }
        }

        layout.setOnTouchListener(dragTouchListener)

        // Dedicated Top Drag Header Bar
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF2A2A2A.toInt())
                setStroke(1, 0x33FFFFFF)
                cornerRadius = ControlUtils.dpToPx(context, 8f)
            }
            val hp = ControlUtils.dpToPx(context, 8f).toInt()
            val vp = ControlUtils.dpToPx(context, 4f).toInt()
            setPadding(hp, vp, hp, vp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = ControlUtils.dpToPx(context, 6f).toInt()
            }
            setOnTouchListener(dragTouchListener)
        }
        val title = TextView(context).apply {
            text = "⠿ Drag Keyboard"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = Button(context).apply {
            text = "✕ Close"
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            val chp = ControlUtils.dpToPx(context, 10f).toInt()
            val cvp = ControlUtils.dpToPx(context, 3f).toInt()
            setPadding(chp, cvp, chp, cvp)
            background = GradientDrawable().apply {
                setColor(0xFF3A3A3A.toInt())
                setStroke(1, 0x44FFFFFF)
                cornerRadius = ControlUtils.dpToPx(context, 6f)
            }
            setOnClickListener { dismiss() }
        }
        header.addView(title)
        header.addView(closeBtn)
        layout.addView(header)

        // Keyboard Rows
        val row1Keys = listOf(
            Triple("Esc", LwjglGlfwKeycode.GLFW_KEY_ESCAPE, 1.2f),
            Triple("Tab", LwjglGlfwKeycode.GLFW_KEY_TAB, 1.2f),
            Triple("1", LwjglGlfwKeycode.GLFW_KEY_1, 1f),
            Triple("2", LwjglGlfwKeycode.GLFW_KEY_2, 1f),
            Triple("3", LwjglGlfwKeycode.GLFW_KEY_3, 1f),
            Triple("4", LwjglGlfwKeycode.GLFW_KEY_4, 1f),
            Triple("5", LwjglGlfwKeycode.GLFW_KEY_5, 1f),
            Triple("6", LwjglGlfwKeycode.GLFW_KEY_6, 1f),
            Triple("7", LwjglGlfwKeycode.GLFW_KEY_7, 1f),
            Triple("8", LwjglGlfwKeycode.GLFW_KEY_8, 1f),
            Triple("9", LwjglGlfwKeycode.GLFW_KEY_9, 1f),
            Triple("0", LwjglGlfwKeycode.GLFW_KEY_0, 1f),
            Triple("⌫", LwjglGlfwKeycode.GLFW_KEY_BACKSPACE, 1.5f)
        )

        val row2Keys = listOf(
            Triple("Q", LwjglGlfwKeycode.GLFW_KEY_Q, 1f),
            Triple("W", LwjglGlfwKeycode.GLFW_KEY_W, 1f),
            Triple("E", LwjglGlfwKeycode.GLFW_KEY_E, 1f),
            Triple("R", LwjglGlfwKeycode.GLFW_KEY_R, 1f),
            Triple("T", LwjglGlfwKeycode.GLFW_KEY_T, 1f),
            Triple("Y", LwjglGlfwKeycode.GLFW_KEY_Y, 1f),
            Triple("U", LwjglGlfwKeycode.GLFW_KEY_U, 1f),
            Triple("I", LwjglGlfwKeycode.GLFW_KEY_I, 1f),
            Triple("O", LwjglGlfwKeycode.GLFW_KEY_O, 1f),
            Triple("P", LwjglGlfwKeycode.GLFW_KEY_P, 1f),
            Triple("-", LwjglGlfwKeycode.GLFW_KEY_MINUS, 1f),
            Triple("=", LwjglGlfwKeycode.GLFW_KEY_EQUAL, 1f)
        )

        val row3Keys = listOf(
            Triple("A", LwjglGlfwKeycode.GLFW_KEY_A, 1f),
            Triple("S", LwjglGlfwKeycode.GLFW_KEY_S, 1f),
            Triple("D", LwjglGlfwKeycode.GLFW_KEY_D, 1f),
            Triple("F", LwjglGlfwKeycode.GLFW_KEY_F, 1f),
            Triple("G", LwjglGlfwKeycode.GLFW_KEY_G, 1f),
            Triple("H", LwjglGlfwKeycode.GLFW_KEY_H, 1f),
            Triple("J", LwjglGlfwKeycode.GLFW_KEY_J, 1f),
            Triple("K", LwjglGlfwKeycode.GLFW_KEY_K, 1f),
            Triple("L", LwjglGlfwKeycode.GLFW_KEY_L, 1f),
            Triple(";", LwjglGlfwKeycode.GLFW_KEY_SEMICOLON, 1f),
            Triple("Enter", LwjglGlfwKeycode.GLFW_KEY_ENTER, 1.8f)
        )

        val row4Keys = listOf(
            Triple("Shift", LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, 1.5f),
            Triple("Z", LwjglGlfwKeycode.GLFW_KEY_Z, 1f),
            Triple("X", LwjglGlfwKeycode.GLFW_KEY_X, 1f),
            Triple("C", LwjglGlfwKeycode.GLFW_KEY_C, 1f),
            Triple("V", LwjglGlfwKeycode.GLFW_KEY_V, 1f),
            Triple("B", LwjglGlfwKeycode.GLFW_KEY_B, 1f),
            Triple("N", LwjglGlfwKeycode.GLFW_KEY_N, 1f),
            Triple("M", LwjglGlfwKeycode.GLFW_KEY_M, 1f),
            Triple(",", LwjglGlfwKeycode.GLFW_KEY_COMMA, 1f),
            Triple(".", LwjglGlfwKeycode.GLFW_KEY_PERIOD, 1f),
            Triple("/", LwjglGlfwKeycode.GLFW_KEY_SLASH, 1f)
        )

        val row5Keys = listOf(
            Triple("Space", LwjglGlfwKeycode.GLFW_KEY_SPACE, 4.5f),
            Triple("Ctrl", LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, 1.2f),
            Triple("Alt", LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT, 1.2f)
        )

        val rowList = listOf(row1Keys, row2Keys, row3Keys, row4Keys, row5Keys)
        val keyHeight = ControlUtils.dpToPx(context, 32f).toInt()
        val keyGap = ControlUtils.dpToPx(context, 3f).toInt()

        rowList.forEach { rowData ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    keyHeight
                ).apply {
                    bottomMargin = keyGap
                }
                setOnTouchListener(dragTouchListener)
            }

            rowData.forEach { (label, glfwCode, weight) ->
                val keyBtn = Button(context).apply {
                    text = label
                    isAllCaps = false
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    minWidth = 0
                    minHeight = 0
                    stateListAnimator = null
                    setPadding(2, 2, 2, 2)
                    background = GradientDrawable().apply {
                        setColor(0xFF2C2C2C.toInt())
                        setStroke(1, 0x44FFFFFF)
                        cornerRadius = ControlUtils.dpToPx(context, 5f)
                    }
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                background = GradientDrawable().apply {
                                    setColor(0xFF505050.toInt())
                                    setStroke(1, 0xFFFFFFFF.toInt())
                                    cornerRadius = ControlUtils.dpToPx(context, 5f)
                                }
                                InputBridge.sendKey(glfwCode, true)
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                background = GradientDrawable().apply {
                                    setColor(0xFF2C2C2C.toInt())
                                    setStroke(1, 0x44FFFFFF)
                                    cornerRadius = ControlUtils.dpToPx(context, 5f)
                                }
                                InputBridge.sendKey(glfwCode, false)
                                true
                            }
                            else -> false
                        }
                    }
                }
                val lp = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    weight
                ).apply {
                    marginEnd = keyGap
                }
                rowLayout.addView(keyBtn, lp)
            }
            layout.addView(rowLayout)
        }

        rootLayout = layout
        rootParams = params
        windowManager.addView(layout, params)
    }

    fun dismiss() {
        val layout = rootLayout
        rootLayout = null
        rootParams = null
        if (layout != null && layout.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(layout)
            } catch (_: Throwable) {
            }
        }
        onClose()
    }
}
