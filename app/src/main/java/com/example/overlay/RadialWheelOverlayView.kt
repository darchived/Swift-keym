package com.example.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.customcontrols.ControlData
import com.example.customcontrols.utils.ControlUtils
import com.example.input.InputBridge
import com.example.input.LwjglGlfwKeycode
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Floating 8-Direction Grey & White Radial Quick Wheel attached via WindowManager
 * while holding a Radial Wheel control button.
 */
class RadialWheelOverlayView(
    private val context: Context,
    private val windowManager: WindowManager
) : View(context) {

    private var activeData: ControlData? = null
    private var centerScreenX: Float = 0f
    private var centerScreenY: Float = 0f
    private var selectedSlice: Int = -1
    private var isShowing = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sizePx = ControlUtils.dpToPx(context, 220f).toInt()
    private val innerDeadzonePx = ControlUtils.dpToPx(context, 28f)
    private val outerRadiusPx = sizePx * 0.46f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6202020.toInt()
        style = Paint.Style.FILL
    }
    private val activeSlicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = ControlUtils.dpToPx(context, 1.5f)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = ControlUtils.dpToPx(context, 1f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = ControlUtils.dpToPx(context, 12.5f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private val windowParams = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun showWheel(data: ControlData, screenX: Float, screenY: Float) {
        activeData = data
        centerScreenX = screenX
        centerScreenY = screenY
        selectedSlice = -1
        windowParams.x = (screenX - sizePx / 2f).toInt()
        windowParams.y = (screenY - sizePx / 2f).toInt()
        if (!isShowing) {
            isShowing = true
            try {
                windowManager.addView(this, windowParams)
            } catch (_: Throwable) {
                isShowing = false
            }
        } else if (isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(this, windowParams)
            } catch (_: Throwable) {
            }
        }
        invalidate()
    }

    fun updatePointer(screenX: Float, screenY: Float) {
        if (!isShowing) return
        val dx = screenX - centerScreenX
        val dy = screenY - centerScreenY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val nextSlice = if (dist < innerDeadzonePx) {
            -1
        } else {
            // 0 = Top (-90 deg), rotating clockwise in 45 deg slices
            var deg = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat() + 90f + 22.5f
            while (deg < 0f) deg += 360f
            while (deg >= 360f) deg -= 360f
            (deg / 45f).toInt().coerceIn(0, 7)
        }
        if (nextSlice != selectedSlice) {
            selectedSlice = nextSlice
            invalidate()
        }
    }

    fun releaseAndDismiss() {
        val data = activeData
        val slice = selectedSlice
        dismissImmediate()
        if (data != null && slice in 0..7) {
            val keycode = data.radialKeycodes.getOrElse(slice) { LwjglGlfwKeycode.GLFW_KEY_UNKNOWN }
            if (keycode != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) {
                InputBridge.sendKey(keycode, true)
                mainHandler.postDelayed({ InputBridge.sendKey(keycode, false) }, 35L)
            }
        }
    }

    fun dismissImmediate() {
        activeData = null
        selectedSlice = -1
        if (isShowing) {
            isShowing = false
            if (isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(this)
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val arcRect = RectF(cx - outerRadiusPx, cy - outerRadiusPx, cx + outerRadiusPx, cy + outerRadiusPx)

        // Outer dark wheel circle
        canvas.drawCircle(cx, cy, outerRadiusPx, bgPaint)

        // Highlighted active slice
        if (selectedSlice in 0..7) {
            val startAngle = -112.5f + selectedSlice * 45f
            canvas.drawArc(arcRect, startAngle, 45f, true, activeSlicePaint)
        }

        // Slice dividers
        for (i in 0 until 8) {
            val angleRad = (-112.5 + i * 45.0) * PI / 180.0
            val ix = cx + (innerDeadzonePx * cos(angleRad)).toFloat()
            val iy = cy + (innerDeadzonePx * sin(angleRad)).toFloat()
            val ox = cx + (outerRadiusPx * cos(angleRad)).toFloat()
            val oy = cy + (outerRadiusPx * sin(angleRad)).toFloat()
            canvas.drawLine(ix, iy, ox, oy, dividerPaint)
        }

        // Outer & inner deadzone rings
        canvas.drawCircle(cx, cy, outerRadiusPx, strokePaint)
        canvas.drawCircle(cx, cy, innerDeadzonePx, bgPaint)
        canvas.drawCircle(cx, cy, innerDeadzonePx, strokePaint)

        // Slice labels
        val data = activeData ?: return
        val labelRadius = (innerDeadzonePx + outerRadiusPx) * 0.54f
        val fm = labelPaint.fontMetrics
        val textOffset = (fm.descent - fm.ascent) / 2f - fm.descent

        for (i in 0 until 8) {
            val centerAngle = (-90.0 + i * 45.0) * PI / 180.0
            val lx = cx + (labelRadius * cos(centerAngle)).toFloat()
            val ly = cy + (labelRadius * sin(centerAngle)).toFloat() + textOffset
            val lbl = data.radialLabels.getOrNull(i)?.takeIf { it.isNotBlank() }
                ?: LwjglGlfwKeycode.getKeyName(data.radialKeycodes.getOrElse(i) { 0 })
            labelPaint.color = if (i == selectedSlice) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawText(lbl.take(5), lx, ly, labelPaint)
        }

        labelPaint.color = if (selectedSlice == -1) 0xFFFFFFFF.toInt() else 0xFFA8A8A8.toInt()
        canvas.drawText("✕", cx, cy + textOffset, labelPaint)
    }
}
