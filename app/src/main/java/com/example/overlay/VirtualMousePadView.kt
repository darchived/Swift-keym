package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.utils.ControlUtils
import com.example.input.InputBridge
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
class VirtualMousePadView(
    context: Context,
    private val windowManager: WindowManager,
    private val windowParams: WindowManager.LayoutParams
) : View(context) {

    var onKeepAlive: (() -> Unit)? = null

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var isWindowMoving = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88121212.toInt()
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = ControlUtils.dpToPx(context, 1.5f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textSize = ControlUtils.dpToPx(context, 11f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = ControlUtils.dpToPx(context, 1f)
    }

    init {
        elevation = ControlUtils.dpToPx(context, 8f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = ControlUtils.dpToPx(context, 16f)

        canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
        canvas.drawRoundRect(0f, 0f, w, h, r, r, borderPaint)

        val cx = w / 2f
        val cy = h / 2f
        canvas.drawLine(cx - 16f, cy, cx + 16f, cy, crosshairPaint)
        canvas.drawLine(cx, cy - 16f, cx, cy + 16f, crosshairPaint)

        canvas.drawText("VIRTUAL MOUSE PAD", cx, ControlUtils.dpToPx(context, 18f), textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val sensX = ControlLayoutManager.getVirtualMousePadSensX(context)
        val sensY = ControlLayoutManager.getVirtualMousePadSensY(context)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val px = event.getX(idx)
                val py = event.getY(idx)

                if (py < ControlUtils.dpToPx(context, 32f)) {
                    isWindowMoving = true
                    downX = event.rawX
                    downY = event.rawY
                    return true
                }

                if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
                    activePointerId = event.getPointerId(idx)
                    downX = px
                    downY = py
                    lastX = px
                    lastY = py
                    isDragging = false
                }
                onKeepAlive?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isWindowMoving) {
                    val rawX = event.rawX
                    val rawY = event.rawY
                    val dx = rawX - downX
                    val dy = rawY - downY
                    downX = rawX
                    downY = rawY
                    windowParams.x += dx.toInt()
                    windowParams.y += dy.toInt()
                    try {
                        windowManager.updateViewLayout(this, windowParams)
                    } catch (_: Throwable) {}
                    return true
                }

                val idx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    event.findPointerIndex(activePointerId)
                } else {
                    -1
                }
                if (idx < 0) return true

                val currX = event.getX(idx)
                val currY = event.getY(idx)

                if (!isDragging) {
                    val dist = hypot((currX - downX).toDouble(), (currY - downY).toDouble())
                    if (dist < touchSlop) return true
                    isDragging = true
                    lastX = currX
                    lastY = currY
                    return true
                }

                val dx = currX - lastX
                val dy = currY - lastY
                lastX = currX
                lastY = currY

                if (dx != 0f || dy != 0f) {
                    val moveX = dx * sensX
                    val moveY = dy * sensY
                    InputBridge.sendMouseMove(moveX, moveY)
                    onKeepAlive?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                if (isWindowMoving) {
                    isWindowMoving = false
                    return true
                }
                val idx = event.actionIndex
                if (event.getPointerId(idx) == activePointerId || event.actionMasked != MotionEvent.ACTION_POINTER_UP) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    isDragging = false
                }
                onKeepAlive?.invoke()
                return true
            }
        }
        return true
    }
}
