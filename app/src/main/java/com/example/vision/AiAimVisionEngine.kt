package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import com.example.customcontrols.ControlLayoutManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AiTargetTelemetry(
    val targetName: String,
    val tagType: String,
    val dxPx: Int,
    val dyPx: Int,
    val distancePx: Int,
    val confidencePct: Int,
    val teammatesSkipped: Int,
    val isLocked: Boolean,
    val timestampMs: Long
)

/**
 * Thread-safe live telemetry & terminal log bus for AI Vision Aim Assist.
 */
object AiVisionTerminalBus {
    private const val MAX_LOGS = 36

    private val _isScreenVisionActive = MutableStateFlow(false)
    val isScreenVisionActive: StateFlow<Boolean> = _isScreenVisionActive.asStateFlow()

    private val _statusHeadline = MutableStateFlow("OFF (Capture Inactive)")
    val statusHeadline: StateFlow<String> = _statusHeadline.asStateFlow()

    private val _activeTarget = MutableStateFlow<AiTargetTelemetry?>(null)
    val activeTarget: StateFlow<AiTargetTelemetry?> = _activeTarget.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(
        listOf("[SYSTEM] AI Vision Terminal ready. Enable Screen Capture & set strength > Off.")
    )
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun setScreenVisionActive(active: Boolean) {
        _isScreenVisionActive.value = active
        if (!active) {
            _activeTarget.value = null
            _statusHeadline.value = "OFF (Capture Stopped)"
        }
    }

    fun updateStatus(headline: String, target: AiTargetTelemetry? = _activeTarget.value) {
        _statusHeadline.value = headline
        _activeTarget.value = target
    }

    @Synchronized
    fun appendLog(message: String) {
        val ts = timeFormat.format(Date())
        val line = "[$ts] $message"
        val current = _terminalLogs.value
        val next = if (current.size >= MAX_LOGS) {
            current.drop(current.size - MAX_LOGS + 1) + line
        } else {
            current + line
        }
        _terminalLogs.value = next
    }

    @Synchronized
    fun clearLogs() {
        _terminalLogs.value = listOf("[SYSTEM] Terminal cleared.")
    }
}

/**
 * Real-time on-device Screen Vision analyzer for Warzone / FPS red nametag tracking:
 * - Detects bright red enemy nametag clusters near the center crosshair.
 * - Excludes teammates using Warzone/FPS squad marker hues (Blue, Cyan/Teal, Green, Purple, Yellow)
 *   surrounding or overlapping the nametag, plus damage-flash & outer HUD edge guards.
 * - Activates steering ONLY when a confirmed non-teammate target is within the crosshair proximity gate.
 * - Runs throttled bundled ML Kit OCR on the cropped enemy nametag to display real player names in the terminal.
 */
class AiAimVisionAnalyzer(
    private val context: Context,
    private val onSteerDelta: (Float, Float) -> Unit
) {

    private data class CandidateBox(
        var minX: Int,
        var maxX: Int,
        var minY: Int,
        var maxY: Int,
        var redCount: Int
    ) {
        val width: Int get() = (maxX - minX + 1)
        val height: Int get() = (maxY - minY + 1)
        val centerX: Float get() = (minX + maxX) * 0.5f
        val centerY: Float get() = (minY + maxY) * 0.5f
    }

    private var pixelBuffer = IntArray(0)
    private var lastFrameProcessMs = 0L
    private var lastOcrRequestMs = 0L
    private var lastLogEmitMs = 0L
    private var lastTeammateLogMs = 0L
    private var lastRecognizedName = "Hostile Red Tag"

    private val ocrLock = Any()
    @Volatile private var ocrInFlight = false
    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun close() {
        try {
            textRecognizer.close()
        } catch (_: Throwable) {
        }
    }

    fun processBitmap(bitmap: Bitmap) {
        val now = SystemClock.uptimeMillis()
        // Cap analysis at ~30 FPS for crisp tracking without starving UI/game threads
        if (now - lastFrameProcessMs < 32L) return
        lastFrameProcessMs = now

        val strength = ControlLayoutManager.getAiAimStrength(context)
        if (strength < 0.5f) {
            AiVisionTerminalBus.updateStatus("ARMED • Strength OFF (0.0)", null)
            return
        }

        val w = bitmap.width
        val h = bitmap.height
        if (w < 64 || h < 64) return

        val needed = w * h
        if (pixelBuffer.size != needed) {
            pixelBuffer = IntArray(needed)
        }
        bitmap.getPixels(pixelBuffer, 0, w, 0, 0, w, h)

        val teamFilterEnabled = ControlLayoutManager.isAiTeamFilterEnabled(context)
        val gatePct = ControlLayoutManager.getAiCrosshairGatePct(context)
        val ocrEnabled = ControlLayoutManager.isAiOcrNamesEnabled(context)

        // Central combat ROI: ignore outer minimap/killfeed/weapon-HUD corners
        val roiLeft = (w * 0.14f).toInt().coerceAtLeast(0)
        val roiRight = (w * 0.86f).toInt().coerceAtMost(w - 1)
        val roiTop = (h * 0.10f).toInt().coerceAtLeast(0)
        val roiBottom = (h * 0.82f).toInt().coerceAtMost(h - 1)

        val step = 2
        val gridCols = ((roiRight - roiLeft) / step) + 1
        val gridRows = ((roiBottom - roiTop) / step) + 1
        val totalSampled = max(1, gridCols * gridRows)

        val candidates = ArrayList<CandidateBox>(16)
        var totalRedPixels = 0

        // Pass 1: Detect bright red nametag runs on every sampled row
        var y = roiTop
        while (y <= roiBottom) {
            val rowOffset = y * w
            var runStartX = -1
            var runRedCount = 0
            var x = roiLeft
            while (x <= roiRight) {
                val px = pixelBuffer[rowOffset + x]
                if (isEnemyRedTagPixel(px)) {
                    totalRedPixels++
                    if (runStartX < 0) {
                        runStartX = x
                        runRedCount = 1
                    } else {
                        runRedCount++
                    }
                } else if (runStartX >= 0) {
                    val runEndX = x - step
                    if (runRedCount >= 3) {
                        mergeRedRun(candidates, runStartX, runEndX, y, runRedCount)
                    }
                    runStartX = -1
                    runRedCount = 0
                }
                x += step
            }
            if (runStartX >= 0 && runRedCount >= 3) {
                mergeRedRun(candidates, runStartX, roiRight, y, runRedCount)
            }
            y += step
        }

        // Damage vignette / blood screen guard
        if (totalRedPixels.toFloat() / totalSampled.toFloat() > 0.22f) {
            AiVisionTerminalBus.updateStatus("GUARD • Damage/Flash Vignette Ignored", null)
            if (now - lastLogEmitMs >= 900L) {
                lastLogEmitMs = now
                AiVisionTerminalBus.appendLog("GUARD: Heavy red vignette/flash ignored (0 steer).")
            }
            return
        }

        val centerX = w * 0.5f
        val centerY = h * 0.5f
        val fovRadiusPx = ControlLayoutManager.getAiFovRadiusPx(context)
        val captureScale = w.toFloat() / 1080f
        val gateRadiusPx = fovRadiusPx * captureScale.coerceIn(0.5f, 2.5f)

        var teammatesSkipped = 0
        var bestEnemy: CandidateBox? = null
        var bestDist = Float.MAX_VALUE
        var bestAimDx = 0f
        var bestAimDy = 0f
        var nearestOutOfRangeDist = Float.MAX_VALUE

        for (box in candidates) {
            // Compact nametag / enemy diamond geometry check
            if (box.redCount < 5) continue
            if (box.width > (w * 0.28f) || box.height > (h * 0.09f)) continue

            // Check for Warzone / FPS teammate guide markers around/above/left of the nametag
            if (teamFilterEnabled && hasTeammateGuideNearby(pixelBuffer, w, h, box)) {
                teammatesSkipped++
                if (now - lastTeammateLogMs >= 750L) {
                    lastTeammateLogMs = now
                    val tdx = (box.centerX - centerX).roundToInt()
                    val tdy = (box.centerY - centerY).roundToInt()
                    AiVisionTerminalBus.appendLog(
                        "TEAMMATE SKIPPED: Friendly squad indicator @ ($tdx, $tdy)px [Protected]"
                    )
                }
                continue
            }

            // Aim point sits slightly below the red nametag onto upper torso / head
            val targetX = box.centerX
            val bodyDropPx = (box.height * 1.45f).coerceIn(10f, 34f)
            val targetY = (box.maxY + bodyDropPx).coerceAtMost(roiBottom.toFloat())

            val dx = targetX - centerX
            val dy = targetY - centerY
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (dist <= gateRadiusPx) {
                if (dist < bestDist) {
                    bestDist = dist
                    bestEnemy = box
                    bestAimDx = dx
                    bestAimDy = dy
                }
            } else if (dist < nearestOutOfRangeDist) {
                nearestOutOfRangeDist = dist
            }
        }

        if (bestEnemy == null) {
            val status = when {
                teammatesSkipped > 0 -> "SCANNING • Teammate Ignored ($teammatesSkipped)"
                nearestOutOfRangeDist < Float.MAX_VALUE ->
                    "STANDBY • Red Tag Outside Gate (${nearestOutOfRangeDist.roundToInt()}px > ${gateRadiusPx.roundToInt()}px)"
                else -> "SCANNING • No Hostile Red Tag Near Crosshair"
            }
            AiVisionTerminalBus.updateStatus(
                status,
                AiTargetTelemetry(
                    targetName = if (teammatesSkipped > 0) "Teammate Filtered" else "--",
                    tagType = if (teammatesSkipped > 0) "TEAMMATE" else "NONE",
                    dxPx = 0,
                    dyPx = 0,
                    distancePx = if (nearestOutOfRangeDist < Float.MAX_VALUE) nearestOutOfRangeDist.roundToInt() else 0,
                    confidencePct = 0,
                    teammatesSkipped = teammatesSkipped,
                    isLocked = false,
                    timestampMs = now
                )
            )
            return
        }

        // Throttled on-device ML Kit OCR to read the player nametag string
        if (ocrEnabled && !ocrInFlight && (now - lastOcrRequestMs >= 260L)) {
            lastOcrRequestMs = now
            runOcrOnTag(bitmap, bestEnemy)
        }

        // Smooth proportional aim steering toward target inside crosshair gate
        val dist = bestDist.coerceAtLeast(1f)
        val deadzonePx = 3.0f
        var steerX = 0f
        var steerY = 0f
        if (dist > deadzonePx) {
            // Gain scales cleanly from 0.5 (subtle) to 5.0 (strong)
            val gain = strength * 0.14f
            val maxStepPerFrame = strength * 6.5f
            val falloff = (1.0f - (dist / (gateRadiusPx * 1.45f))).coerceIn(0.45f, 1.0f)
            steerX = (bestAimDx * gain * falloff).coerceIn(-maxStepPerFrame, maxStepPerFrame)
            steerY = (bestAimDy * gain * falloff).coerceIn(-maxStepPerFrame, maxStepPerFrame)
            if (abs(steerX) > 0.05f || abs(steerY) > 0.05f) {
                onSteerDelta(steerX, steerY)
            }
        }

        val confPct = ((bestEnemy.redCount.toFloat() / 24f).coerceIn(0.55f, 0.99f) * 100f).roundToInt()
        val displayName = if (ocrEnabled) lastRecognizedName else "Hostile Red Tag"
        val telemetry = AiTargetTelemetry(
            targetName = displayName,
            tagType = "ENEMY RED",
            dxPx = bestAimDx.roundToInt(),
            dyPx = bestAimDy.roundToInt(),
            distancePx = dist.roundToInt(),
            confidencePct = confPct,
            teammatesSkipped = teammatesSkipped,
            isLocked = true,
            timestampMs = now
        )
        AiVisionTerminalBus.updateStatus("LOCKED: $displayName (${dist.roundToInt()}px)", telemetry)

        if (now - lastLogEmitMs >= 320L) {
            lastLogEmitMs = now
            AiVisionTerminalBus.appendLog(
                String.format(
                    Locale.US,
                    "TARGET READ: \"%s\" • RedTag conf=%d%% • off=(%+d,%+d)px • STEER (%.1f, %.1f)",
                    displayName,
                    confPct,
                    bestAimDx.roundToInt(),
                    bestAimDy.roundToInt(),
                    steerX,
                    steerY
                )
            )
        }
    }

    private fun mergeRedRun(
        candidates: MutableList<CandidateBox>,
        x1: Int,
        x2: Int,
        y: Int,
        count: Int
    ) {
        for (i in candidates.indices) {
            val box = candidates[i]
            if (y - box.maxY <= 8 && x1 <= box.maxX + 14 && x2 >= box.minX - 14) {
                box.minX = min(box.minX, x1)
                box.maxX = max(box.maxX, x2)
                box.maxY = max(box.maxY, y)
                box.redCount += count
                return
            }
        }
        if (candidates.size < 32) {
            candidates.add(CandidateBox(x1, x2, y, y, count))
        }
    }

    /**
     * Checks a halo around the detected red nametag for Warzone / FPS teammate guide colors
     * (Blue, Cyan/Teal, Squad Green, Purple/Violet, Yellow squad dots/chevrons/outlines).
     */
    private fun hasTeammateGuideNearby(
        pixels: IntArray,
        w: Int,
        h: Int,
        box: CandidateBox
    ): Boolean {
        val left = (box.minX - 24).coerceAtLeast(0)
        val right = (box.maxX + 24).coerceAtMost(w - 1)
        val top = (box.minY - 22).coerceAtLeast(0)
        val bottom = (box.maxY + 26).coerceAtMost(h - 1)

        var teamMarkers = 0
        var y = top
        while (y <= bottom) {
            val row = y * w
            var x = left
            while (x <= right) {
                if (isTeammateGuidePixel(pixels[row + x])) {
                    teamMarkers++
                    if (teamMarkers >= 5) return true
                }
                x += 2
            }
            y += 2
        }
        return false
    }

    private fun isEnemyRedTagPixel(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Fast red nametag / red diamond filter without per-pixel float HSV allocation
        return r >= 165 && (r - g) >= 72 && (r - b) >= 72 && g <= 115 && b <= 115
    }

    private fun isTeammateGuidePixel(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF

        // 1. Warzone Blue / Squad Cyan / Teal marker
        val isBlueOrCyan = b >= 145 && (b - r) >= 55 && r <= 120
        // 2. Squad Green chevron / dot
        val isGreen = g >= 155 && (g - r) >= 50 && (g - b) >= 25
        // 3. Squad Purple / Magenta marker (not red)
        val isPurple = b >= 160 && r >= 120 && g <= 105 && (b - g) >= 65
        // 4. Squad Yellow / Gold marker
        val isYellow = r >= 190 && g >= 175 && b <= 90

        return isBlueOrCyan || isGreen || isPurple || isYellow
    }

    private fun runOcrOnTag(source: Bitmap, box: CandidateBox) {
        val cropLeft = (box.minX - 28).coerceAtLeast(0)
        val cropTop = (box.minY - 22).coerceAtLeast(0)
        val cropRight = (box.maxX + 64).coerceAtMost(source.width)
        val cropBottom = (box.maxY + 18).coerceAtMost(source.height)
        val cw = cropRight - cropLeft
        val ch = cropBottom - cropTop
        if (cw < 24 || ch < 12) return

        ocrInFlight = true
        try {
            val cropped = Bitmap.createBitmap(source, cropLeft, cropTop, cw, ch)
            val scaled = Bitmap.createScaledBitmap(cropped, cw * 2, ch * 2, true)
            if (cropped != scaled) cropped.recycle()

            val image = InputImage.fromBitmap(scaled, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val raw = visionText.text
                        .replace("\n", " ")
                        .replace(Regex("[^a-zA-Z0-9_\\[\\]\\-. ]"), "")
                        .trim()
                    if (raw.length >= 2) {
                        lastRecognizedName = raw.take(22)
                    }
                }
                .addOnCompleteListener {
                    scaled.recycle()
                    ocrInFlight = false
                }
        } catch (_: Throwable) {
            ocrInFlight = false
        }
    }
}
