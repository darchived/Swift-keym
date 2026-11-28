package com.example.input

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

enum class MacroStepType {
    KEY_TAP,
    KEY_DOWN,
    KEY_UP,
    MOUSE_BTN_TAP,
    MOUSE_BTN_DOWN,
    MOUSE_BTN_UP,
    WAIT_MS,
    MOVE_MOUSE
}

enum class MacroCondition(val label: String) {
    ALWAYS("Always"),
    ADS_ONLY("ADS Only (Scope)"),
    SPRINT_ONLY("Sprint Only")
}

data class MacroStep(
    val type: MacroStepType = MacroStepType.KEY_TAP,
    val code: Int = LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val delayMs: Long = 35L
) {
    fun describe(): String = when (type) {
        MacroStepType.KEY_TAP -> "Tap ${LwjglGlfwKeycode.getKeyName(code)}"
        MacroStepType.KEY_DOWN -> "Hold ${LwjglGlfwKeycode.getKeyName(code)}"
        MacroStepType.KEY_UP -> "Release ${LwjglGlfwKeycode.getKeyName(code)}"
        MacroStepType.MOUSE_BTN_TAP -> "Click MB$code"
        MacroStepType.MOUSE_BTN_DOWN -> "Hold MB$code"
        MacroStepType.MOUSE_BTN_UP -> "Release MB$code"
        MacroStepType.WAIT_MS -> "Wait ${delayMs}ms"
        MacroStepType.MOVE_MOUSE -> "Move (${dx.toInt()}, ${dy.toInt()})"
    }
}

data class MacroDefinition(
    val id: String = "macro_${System.currentTimeMillis()}",
    var name: String = "New Macro",
    var onPressSteps: MutableList<MacroStep> = mutableListOf(),
    var onReleaseSteps: MutableList<MacroStep> = mutableListOf(),
    var condition: MacroCondition = MacroCondition.ALWAYS,
    var loopEnabled: Boolean = false,
    var cps: Int = 8,
    var humanizerJitterMs: Int = 6,
    var cooldownMs: Long = 0L,
    var cancelOnInterrupt: Boolean = true
) {
    companion object {
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

        fun toJson(macro: MacroDefinition): String = GSON.toJson(macro)

        fun fromJson(json: String): MacroDefinition? {
            return try {
                GSON.fromJson(json, MacroDefinition::class.java)?.also { m ->
                    if (m.onPressSteps == null) m.onPressSteps = mutableListOf()
                    if (m.onReleaseSteps == null) m.onReleaseSteps = mutableListOf()
                }
            } catch (_: Throwable) {
                null
            }
        }

        fun listToJson(list: List<MacroDefinition>): String = GSON.toJson(list)

        fun listFromJson(json: String?): MutableList<MacroDefinition> {
            if (json.isNullOrBlank()) return mutableListOf()
            return try {
                val type = object : TypeToken<MutableList<MacroDefinition>>() {}.type
                GSON.fromJson<MutableList<MacroDefinition>>(json, type) ?: mutableListOf()
            } catch (_: Throwable) {
                mutableListOf()
            }
        }
    }
}
