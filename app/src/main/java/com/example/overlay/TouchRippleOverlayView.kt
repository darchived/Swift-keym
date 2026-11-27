package com.example.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.customcontrols.utils.ControlUtils

/**
 * Purely visual (non-touchable) ripple ring feedback layer rendered when overlay controls
 * or the aim swipe zone register a touch press.
 */
class TouchRippleOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Ripple(
        val x: Float,
        val y: Float,
        val startTimeMs: Long
    )

    companion object {
        private const val DURATION_MS = 320L
    }

    private val ripples = ArrayList<Ripple>()
    private val interpolator = DecelerateInterpolator(1.6f)
    private val maxRadiusPx = ControlUtils.dpToPx(context, 34f)
    private val minRadiusPx = ControlUtils.dpToPx(context, 6f)
    private val locationOnScreen = IntArray(2)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ControlUtils.dpToPx(context, 2.2f)
        color = 0xFFFFFFFF.toInt()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44FFFFFF
    }

    var onActiveStateChanged: ((Boolean) -> Unit)? = null

    init {
        isClickable = false
        isFocusable = false
    }

    fun spawnRipple(rawX: Float, rawY: Float) {
        val now = SystemClock.uptimeMillis()
        getLocationOnScreen(locationOnScreen)
        val localX = rawX - locationOnScreen[0]
        val localY = rawY - locationOnScreen[1]

        val wasEmpty = ripples.isEmpty()
        ripples.add(Ripple(localX, localY, now))
        if (wasEmpty) {
            onActiveStateChanged?.invoke(true)
        }
        postInvalidateOnAnimation()
    }

    fun clearRipples() {
        val wasNotEmpty = ripples.isNotEmpty()
        ripples.clear()
        if (wasNotEmpty) {
            onActiveStateChanged?.invoke(false)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ripples.isEmpty()) return

        val now = SystemClock.uptimeMillis()
        val iterator = ripples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            val elapsed = now - ripple.startTimeMs
            if (elapsed >= DURATION_MS) {
                iterator.remove()
                continue
            }
            val rawFraction = (elapsed.toFloat() / DURATION_MS.toFloat()).coerceIn(0f, 1f)
            val eased = interpolator.getInterpolation(rawFraction)
            val radius = minRadiusPx + (maxRadiusPx - minRadiusPx) * eased
            val alphaFactor = (1f - rawFraction).coerceIn(0f, 1f)

            fillPaint.alpha = (alphaFactor * 72f).toInt().coerceIn(0, 255)
            strokePaint.alpha = (alphaFactor * 225f).toInt().coerceIn(0, 255)

            canvas.drawCircle(ripple.x, ripple.y, radius * 0.72f, fillPaint)
            canvas.drawCircle(ripple.x, ripple.y, radius, strokePaint)
        }

        if (ripples.isNotEmpty()) {
            postInvalidateOnAnimation()
        } else {
            onActiveStateChanged?.invoke(false)
        }
    }
}
