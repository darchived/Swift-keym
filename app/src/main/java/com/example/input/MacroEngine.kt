package com.example.input

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import com.example.customcontrols.ControlLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Executes conditional, loopable, humanizer-jittered, and interruptible macros
 * through InputBridge, and provides live macro recording and dry-run/test playback.
 */
object MacroEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val interruptibleMacroIds = ConcurrentHashMap.newKeySet<String>()
    private val cooldownLastTriggerMs = ConcurrentHashMap<String, Long>()
    private val activeLoopingIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var isRecording: Boolean = false
        private set

    private var lastRecordedAtMs: Long = 0L
    private val recordedBuffer = mutableListOf<MacroStep>()

    @Synchronized
    fun startRecording() {
        recordedBuffer.clear()
        lastRecordedAtMs = SystemClock.uptimeMillis()
        isRecording = true
    }

    @Synchronized
    fun stopRecording(): List<MacroStep> {
        isRecording = false
        return recordedBuffer.toList()
    }

    @Synchronized
    fun onObservedInput(code: Int, isMouse: Boolean, down: Boolean) {
        if (!isRecording) return
        val now = SystemClock.uptimeMillis()
        val elapsed = (now - lastRecordedAtMs).coerceIn(0L, 4000L)
        if (recordedBuffer.isNotEmpty() && elapsed >= 15L) {
            recordedBuffer.add(MacroStep(type = MacroStepType.WAIT_MS, delayMs = elapsed))
        }
        lastRecordedAtMs = now
        val stepType = if (isMouse) {
            if (down) MacroStepType.MOUSE_BTN_DOWN else MacroStepType.MOUSE_BTN_UP
        } else {
            if (down) MacroStepType.KEY_DOWN else MacroStepType.KEY_UP
        }
        val mappedCode = if (isMouse) {
            when (code) {
                MotionEvent.BUTTON_PRIMARY -> 1
                MotionEvent.BUTTON_SECONDARY -> 2
                MotionEvent.BUTTON_TERTIARY -> 3
                else -> 1
            }
        } else {
            code
        }
        recordedBuffer.add(MacroStep(type = stepType, code = mappedCode))
    }

    fun notifyInterruptKey(glfwKeycode: Int) {
        if (glfwKeycode == LwjglGlfwKeycode.GLFW_KEY_SPACE ||
            glfwKeycode == LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT ||
            glfwKeycode == LwjglGlfwKeycode.GLFW_KEY_ESCAPE
        ) {
            interruptibleMacroIds.toList().forEach { id ->
                activeLoopingIds.remove(id)
                activeJobs.remove(id)?.cancel()
                interruptibleMacroIds.remove(id)
            }
        }
    }

    @JvmStatic
    fun triggerPress(context: Context, macroId: String) {
        val macro = ControlLayoutManager.getMacroById(context, macroId) ?: return
        if (!checkCondition(macro.condition)) return

        val now = SystemClock.uptimeMillis()
        if (macro.cooldownMs > 0L) {
            val prev = cooldownLastTriggerMs[macroId] ?: 0L
            if (now - prev < macro.cooldownMs) return
        }
        cooldownLastTriggerMs[macroId] = now

        activeJobs.remove(macroId)?.cancel()
        if (macro.cancelOnInterrupt) {
            interruptibleMacroIds.add(macroId)
        } else {
            interruptibleMacroIds.remove(macroId)
        }

        if (macro.loopEnabled) {
            activeLoopingIds.add(macroId)
            val job = scope.launch {
                val baseIntervalMs = (1000L / macro.cps.coerceIn(1, 20)).coerceAtLeast(30L)
                while (isActive && activeLoopingIds.contains(macroId)) {
                    if (!checkCondition(macro.condition)) break
                    executeSteps(macro.onPressSteps, macro.humanizerJitterMs, isTest = false)
                    val jitter = if (macro.humanizerJitterMs > 0) {
                        Random.nextInt(-macro.humanizerJitterMs, macro.humanizerJitterMs + 1).toLong()
                    } else 0L
                    delay((baseIntervalMs + jitter).coerceAtLeast(16L))
                }
            }
            activeJobs[macroId] = job
        } else if (macro.onPressSteps.isNotEmpty()) {
            val job = scope.launch {
                executeSteps(macro.onPressSteps, macro.humanizerJitterMs, isTest = false)
            }
            activeJobs[macroId] = job
        }
    }

    @JvmStatic
    fun triggerRelease(context: Context, macroId: String) {
        activeLoopingIds.remove(macroId)
        val macro = ControlLayoutManager.getMacroById(context, macroId) ?: return
        if (macro.loopEnabled) {
            activeJobs.remove(macroId)?.cancel()
        }
        if (macro.onReleaseSteps.isNotEmpty() && checkCondition(macro.condition)) {
            scope.launch {
                executeSteps(macro.onReleaseSteps, macro.humanizerJitterMs, isTest = false)
            }
        }
    }

    fun testMacro(
        macro: MacroDefinition,
        dryRunOnly: Boolean = true,
        onLog: (String) -> Unit
    ): Job {
        return scope.launch {
            onLog("▶ Testing '${macro.name}' (${macro.condition.label})")
            for ((idx, step) in macro.onPressSteps.withIndex()) {
                if (!isActive) break
                onLog("  Press[${idx + 1}]: ${step.describe()}")
                if (dryRunOnly) {
                    delay(if (step.type == MacroStepType.WAIT_MS) step.delayMs.coerceIn(20L, 400L) else 45L)
                } else {
                    executeSingleStep(step, macro.humanizerJitterMs)
                }
            }
            if (macro.onReleaseSteps.isNotEmpty()) {
                onLog("  --- On Release ---")
                for ((idx, step) in macro.onReleaseSteps.withIndex()) {
                    if (!isActive) break
                    onLog("  Release[${idx + 1}]: ${step.describe()}")
                    if (dryRunOnly) {
                        delay(if (step.type == MacroStepType.WAIT_MS) step.delayMs.coerceIn(20L, 400L) else 45L)
                    } else {
                        executeSingleStep(step, macro.humanizerJitterMs)
                    }
                }
            }
            onLog("✓ Test complete")
        }
    }

    @JvmStatic
    fun cancelAll() {
        activeLoopingIds.clear()
        interruptibleMacroIds.clear()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    private fun checkCondition(condition: MacroCondition): Boolean {
        return when (condition) {
            MacroCondition.ALWAYS -> true
            MacroCondition.ADS_ONLY -> InputBridge.isSecondaryMouseDown
            MacroCondition.SPRINT_ONLY -> InputBridge.isKeyDown(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT)
        }
    }

    private suspend fun executeSteps(
        steps: List<MacroStep>,
        jitterMs: Int,
        isTest: Boolean
    ) {
        if (isTest) return
        for (step in steps) {
            executeSingleStep(step, jitterMs)
        }
    }

    private suspend fun executeSingleStep(step: MacroStep, jitterMs: Int) {
        val jitter = if (jitterMs > 0) Random.nextInt(0, jitterMs + 1).toLong() else 0L
        when (step.type) {
            MacroStepType.KEY_TAP -> {
                InputBridge.sendKey(step.code, true)
                delay((28L + jitter).coerceAtLeast(16L))
                InputBridge.sendKey(step.code, false)
            }
            MacroStepType.KEY_DOWN -> InputBridge.sendKey(step.code, true)
            MacroStepType.KEY_UP -> InputBridge.sendKey(step.code, false)
            MacroStepType.MOUSE_BTN_TAP -> {
                val btn = mouseCodeToButton(step.code)
                InputBridge.sendMouseButton(btn, true)
                delay((28L + jitter).coerceAtLeast(16L))
                InputBridge.sendMouseButton(btn, false)
            }
            MacroStepType.MOUSE_BTN_DOWN -> InputBridge.sendMouseButton(mouseCodeToButton(step.code), true)
            MacroStepType.MOUSE_BTN_UP -> InputBridge.sendMouseButton(mouseCodeToButton(step.code), false)
            MacroStepType.WAIT_MS -> delay((step.delayMs + jitter).coerceAtLeast(1L))
            MacroStepType.MOVE_MOUSE -> InputBridge.sendMouseMove(step.dx, step.dy)
        }
    }

    private fun mouseCodeToButton(code: Int): Int = when (code) {
        2 -> MotionEvent.BUTTON_SECONDARY
        3 -> MotionEvent.BUTTON_TERTIARY
        else -> MotionEvent.BUTTON_PRIMARY
    }
}
