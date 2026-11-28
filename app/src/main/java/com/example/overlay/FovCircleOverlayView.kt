package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.utils.ControlUtils

@SuppressLint("ViewConstructor")
class FovCircleOverlayView(context: Context) : View(context) {

    private val fovPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = ControlUtils.dpToPx(context, 1.5f)
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radiusPx = ControlLayoutManager.getAiFovRadiusPx(context)

        canvas.drawCircle(cx, cy, radiusPx, fovPaint)
        canvas.drawCircle(cx, cy, 3f, centerDotPaint)
    }
}
