package com.example.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.example.customcontrols.utils.ControlUtils

/**
 * Non-interactive centered reticle overlay window for hipfire / sniper alignment.
 * Always attached with FLAG_NOT_TOUCHABLE so zero touches are ever intercepted.
 */
class CrosshairOverlayView(context: Context) : View(context) {

    companion object {
        const val STYLE_CROSS_DOT = 0
        const val STYLE_DOT = 1
        const val STYLE_CROSS = 2
        const val STYLE_CIRCLE = 3

        val STYLE_LABELS = arrayOf("Cross + Dot", "Center Dot", "Open Cross", "Tactical Ring")
    }

    var crosshairStyle: Int = STYLE_CROSS_DOT
        set(value) {
            field = value.coerceIn(0, 3)
            invalidate()
        }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC121212.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = ControlUtils.dpToPx(context, 3.2f)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF5FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = ControlUtils.dpToPx(context, 1.6f)
    }

    private val dotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC121212.toInt()
        style = Paint.Style.FILL
    }

    private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val gap = ControlUtils.dpToPx(context, 4f)
        val arm = ControlUtils.dpToPx(context, 7f)
        val dotR = ControlUtils.dpToPx(context, 2.0f)

        when (crosshairStyle) {
            STYLE_DOT -> {
                canvas.drawCircle(cx, cy, dotR + ControlUtils.dpToPx(context, 1.0f), dotOutlinePaint)
                canvas.drawCircle(cx, cy, dotR, dotFillPaint)
            }
            STYLE_CROSS -> {
                drawCrossArms(canvas, cx, cy, gap, arm)
            }
            STYLE_CIRCLE -> {
                val ringR = ControlUtils.dpToPx(context, 10f)
                canvas.drawCircle(cx, cy, ringR, outlinePaint)
                canvas.drawCircle(cx, cy, ringR, strokePaint)
                canvas.drawCircle(cx, cy, dotR * 0.85f + ControlUtils.dpToPx(context, 0.8f), dotOutlinePaint)
                canvas.drawCircle(cx, cy, dotR * 0.85f, dotFillPaint)
            }
            else -> {
                drawCrossArms(canvas, cx, cy, gap, arm)
                canvas.drawCircle(cx, cy, dotR * 0.8f + ControlUtils.dpToPx(context, 0.8f), dotOutlinePaint)
                canvas.drawCircle(cx, cy, dotR * 0.8f, dotFillPaint)
            }
        }
    }

    private fun drawCrossArms(canvas: Canvas, cx: Float, cy: Float, gap: Float, arm: Float) {
        val pairs = arrayOf(
            floatArrayOf(cx - gap - arm, cy, cx - gap, cy),
            floatArrayOf(cx + gap, cy, cx + gap + arm, cy),
            floatArrayOf(cx, cy - gap - arm, cx, cy - gap),
            floatArrayOf(cx, cy + gap, cx, cy + gap + arm)
        )
        for (p in pairs) {
            canvas.drawLine(p[0], p[1], p[2], p[3], outlinePaint)
            canvas.drawLine(p[0], p[1], p[2], p[3], strokePaint)
        }
    }
}
