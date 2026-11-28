package com.example.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

/**
 * Hardware SensorManager.TYPE_GYROSCOPE engine for CODM-style 3D aiming.
 * Injects smooth REL_X / REL_Y mouse deltas through InputBridge.sendMouseMove(dx, dy)
 * with orientation-aware axis mapping, anti-shake deadzone, smoothing, and activation modes.
 */
class GyroscopeController(private val context: Context) : SensorEventListener {

    companion object {
        const val MODE_ALWAYS_ON = 0
        const val MODE_ADS_ONLY = 1
        const val MODE_FIRING_ONLY = 2
    }

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile var enabled: Boolean = false
    @Volatile var activationMode: Int = MODE_ALWAYS_ON
    @Volatile var sensitivityX: Float = 2.0f
    @Volatile var sensitivityY: Float = 2.0f
    @Volatile var invertX: Boolean = false
    @Volatile var invertY: Boolean = false
    @Volatile var smoothing: Float = 0.25f // 0.0 (raw/zero deadzone) .. 1.0 (heavy anti-shake)
    @Volatile var paused: Boolean = false
    @Volatile var isTouchSwiping: Boolean = false
    @Volatile var adsZoomScale: Float = 1.0f
    @Volatile var onDeltaListener: ((Float, Float) -> Unit)? = null

    private var isRegistered = false
    private var lastTimestampNs = 0L
    private var filteredX = 0f
    private var filteredY = 0f

    fun isHardwareSupported(): Boolean = gyroSensor != null

    fun start() {
        val sensor = gyroSensor ?: return
        if (isRegistered) return
        lastTimestampNs = 0L
        filteredX = 0f
        filteredY = 0f
        isRegistered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        lastTimestampNs = 0L
        filteredX = 0f
        filteredY = 0f
    }

    fun resetFilters() {
        lastTimestampNs = 0L
        filteredX = 0f
        filteredY = 0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        val ts = event.timestamp
        if (lastTimestampNs == 0L) {
            lastTimestampNs = ts
            return
        }
        val dt = ((ts - lastTimestampNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastTimestampNs = ts
        if (dt <= 0f) return

        if (!enabled || paused || !shouldActivateForCurrentState()) {
            filteredX = 0f
            filteredY = 0f
            return
        }

        val gx = event.values[0]
        val gy = event.values[1]
        val (axisYaw, axisPitch) = mapAxesForDisplayRotation(gx, gy)

        val smoothClamped = smoothing.coerceIn(0f, 1f)
        val deadzoneRadPerSec = 0.015f + smoothClamped * 0.14f

        val dzYaw = applyDeadzone(axisYaw, deadzoneRadPerSec)
        val dzPitch = applyDeadzone(axisPitch, deadzoneRadPerSec)

        // Exponential low-pass filter: higher smoothing retains more prior state to kill micro-tremors
        val alpha = (1.0f - smoothClamped * 0.78f).coerceIn(0.15f, 1.0f)
        filteredX = filteredX + alpha * (dzYaw - filteredX)
        filteredY = filteredY + alpha * (dzPitch - filteredY)

        // Touch-to-Gyro Hybrid Blending: gently attenuate gyro jitter during fast touch swipes
        val hybridTouchAttenuation = if (isTouchSwiping) 0.55f else 1.0f
        val scale = 240f * adsZoomScale * hybridTouchAttenuation
        var dx = filteredX * dt * scale * sensitivityX.coerceIn(0.2f, 8.0f)
        var dy = filteredY * dt * scale * sensitivityY.coerceIn(0.2f, 8.0f)

        if (invertX) dx = -dx
        if (invertY) dy = -dy

        if (abs(dx) > 0.05f || abs(dy) > 0.05f) {
            val sink = onDeltaListener
            if (sink != null) {
                sink.invoke(dx, dy)
            } else {
                InputBridge.sendMouseMove(dx, dy)
            }
        }
    }

    private fun shouldActivateForCurrentState(): Boolean {
        return when (activationMode) {
            MODE_ADS_ONLY -> InputBridge.isSecondaryMouseDown
            MODE_FIRING_ONLY -> InputBridge.isPrimaryMouseDown
            else -> true
        }
    }

    private fun applyDeadzone(value: Float, deadzone: Float): Float {
        val mag = abs(value)
        return if (mag <= deadzone) {
            0f
        } else {
            val sign = if (value >= 0f) 1f else -1f
            sign * (mag - deadzone)
        }
    }

    private fun mapAxesForDisplayRotation(gx: Float, gy: Float): Pair<Float, Float> {
        val rotation = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display.rotation
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }
        } catch (_: Throwable) {
            Surface.ROTATION_90
        }

        return when (rotation) {
            Surface.ROTATION_0 -> -gy to -gx
            Surface.ROTATION_90 -> -gx to gy
            Surface.ROTATION_180 -> gy to gx
            Surface.ROTATION_270 -> gx to -gy
            else -> -gx to gy
        }
    }
}
