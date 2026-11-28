package com.example.customcontrols

import android.content.Context
import android.net.Uri
import com.example.input.LwjglGlfwKeycode
import java.io.File

data class LayoutProfileInfo(
    val fileName: String,
    val displayName: String,
    val gameName: String,
    val buttonCount: Int,
    val joystickCount: Int,
    val drawerCount: Int
)

/**
 * Manages Swift KeyM .json game presets inside .../files/controlmap/,
 * custom button PNG icons, built-in game presets, global opacity, touch ripple, and aim sensitivity.
 */
object ControlLayoutManager {

    private const val PREFS_NAME = "swift_keym_prefs"
    private const val KEY_ACTIVE_LAYOUT = "active_layout"
    private const val KEY_AIM_SENSITIVITY = "aim_sensitivity"
    private const val KEY_AIM_VERT_DAMPENING = "aim_vert_dampening"
    private const val KEY_AIM_SPEED_ACCEL_ENABLED = "aim_speed_accel_enabled"
    private const val KEY_AIM_SPEED_ACCEL_MULTIPLIER = "aim_speed_accel_multiplier"
    private const val KEY_AIM_ANTI_RECOIL = "aim_anti_recoil"
    private const val KEY_GLOBAL_OPACITY = "global_opacity"
    private const val KEY_TOUCH_RIPPLE = "touch_ripple_enabled"
    private const val KEY_GYRO_ENABLED = "gyro_enabled"
    private const val KEY_GYRO_MODE = "gyro_mode"
    private const val KEY_GYRO_SENS_X = "gyro_sens_x"
    private const val KEY_GYRO_SENS_Y = "gyro_sens_y"
    private const val KEY_GYRO_INVERT_X = "gyro_invert_x"
    private const val KEY_GYRO_INVERT_Y = "gyro_invert_y"
    private const val KEY_GYRO_SMOOTHING = "gyro_smoothing"
    private const val KEY_CROSSHAIR_ENABLED = "crosshair_enabled"
    private const val KEY_CROSSHAIR_STYLE = "crosshair_style"
    private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    private const val KEY_AUTO_PAUSE_AIM_ON_MENU = "auto_pause_aim_on_menu"
    private const val KEY_LOCK_POSITIONS = "lock_positions"
    private const val KEY_AUTO_LOAD_LAST = "auto_load_last_preset"
    private const val KEY_LAST_CHANGELOG_VERSION = "last_seen_changelog_version"
    private const val DIR_CONTROLMAP = "controlmap"
    private const val DIR_ICONS = "control_icons"

    const val DEFAULT_FPS_FILE = "warzone_fps.json"
    const val DEFAULT_MINECRAFT_FILE = "minecraft_java.json"
    const val DEFAULT_ACTION_FILE = "tactical_action.json"
    const val DEFAULT_ARENA_FILE = "arena_shooter.json"
    const val DEFAULT_MOBA_FILE = "moba_arpg.json"
    const val DEFAULT_BLANK_FILE = "blank_canvas.json"

    fun getControlMapDir(context: Context): File {
        val extRoot = context.getExternalFilesDir(null)
        val dir = File(extRoot ?: context.filesDir, DIR_CONTROLMAP)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getIconsDir(context: Context): File {
        val dir = File(context.filesDir, DIR_ICONS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun copyPickedImageToIcons(context: Context, uri: Uri): String? {
        return try {
            val outFile = File(getIconsDir(context), "icon_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (outFile.exists() && outFile.length() > 0L) outFile.absolutePath else null
        } catch (_: Throwable) {
            null
        }
    }

    fun ensureDefaultLayouts(context: Context) {
        val dir = getControlMapDir(context)
        val fpsFile = File(dir, DEFAULT_FPS_FILE)
        if (!fpsFile.exists()) {
            createDefaultFpsPreset().save(fpsFile)
        }
        val mcFile = File(dir, DEFAULT_MINECRAFT_FILE)
        if (!mcFile.exists()) {
            createMinecraftJavaPreset().save(mcFile)
        }
        val actionFile = File(dir, DEFAULT_ACTION_FILE)
        if (!actionFile.exists()) {
            createDefaultActionPreset().save(actionFile)
        }
        val arenaFile = File(dir, DEFAULT_ARENA_FILE)
        if (!arenaFile.exists()) {
            createArenaShooterPreset().save(arenaFile)
        }
        val mobaFile = File(dir, DEFAULT_MOBA_FILE)
        if (!mobaFile.exists()) {
            createMobaArpgPreset().save(mobaFile)
        }
        val blankFile = File(dir, DEFAULT_BLANK_FILE)
        if (!blankFile.exists()) {
            createBlankCanvasPreset().save(blankFile)
        }
    }

    fun listLayouts(context: Context): List<LayoutProfileInfo> {
        ensureDefaultLayouts(context)
        val files = getControlMapDir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        return files.mapNotNull { file ->
            try {
                val parsed = CustomControls.load(file)
                val inferredGame = parsed.gameName.takeIf { it.isNotBlank() } ?: when (file.name) {
                    DEFAULT_FPS_FILE -> "Warzone / CODM (FPS)"
                    DEFAULT_MINECRAFT_FILE -> "Minecraft Java"
                    DEFAULT_ACTION_FILE -> "Action / RPG"
                    DEFAULT_ARENA_FILE -> "Tactical Arena Shooter"
                    DEFAULT_MOBA_FILE -> "MOBA / ARPG"
                    DEFAULT_BLANK_FILE -> "Blank Canvas"
                    else -> "Custom Game Preset"
                }
                LayoutProfileInfo(
                    fileName = file.name,
                    displayName = file.name.removeSuffix(".json")
                        .replace("_", " ")
                        .replaceFirstChar { it.uppercase() },
                    gameName = inferredGame,
                    buttonCount = parsed.mControlDataList.size,
                    joystickCount = parsed.mJoystickDataList.size,
                    drawerCount = parsed.mDrawerDataList.size
                )
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun getActiveLayoutName(context: Context): String {
        ensureDefaultLayouts(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ACTIVE_LAYOUT, DEFAULT_FPS_FILE) ?: DEFAULT_FPS_FILE
        val target = File(getControlMapDir(context), saved)
        return if (target.exists()) saved else DEFAULT_FPS_FILE
    }

    fun setActiveLayoutName(context: Context, fileName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_LAYOUT, fileName)
            .apply()
    }

    fun loadActiveLayout(context: Context): CustomControls {
        ensureDefaultLayouts(context)
        val file = File(getControlMapDir(context), getActiveLayoutName(context))
        return try {
            CustomControls.load(file)
        } catch (_: Throwable) {
            createDefaultFpsPreset()
        }
    }

    fun loadLayoutByName(context: Context, fileName: String): CustomControls {
        val file = File(getControlMapDir(context), fileName)
        return CustomControls.load(file)
    }

    fun sanitizeFileName(rawFileName: String): String {
        return rawFileName
            .substringAfterLast('/')
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
            .replace(" ", "_")
            .lowercase()
            .let { if (it.endsWith(".json")) it else "$it.json" }
            .let { if (it == ".json" || it.isBlank()) "preset_${System.currentTimeMillis()}.json" else it }
    }

    fun saveLayoutAs(context: Context, rawFileName: String, controls: CustomControls): String {
        val cleanName = sanitizeFileName(rawFileName)
        val file = File(getControlMapDir(context), cleanName)
        controls.save(file)
        setActiveLayoutName(context, cleanName)
        return cleanName
    }

    fun createNewPreset(context: Context, rawName: String, gameName: String, seedPreset: CustomControls = createDefaultFpsPreset()): String {
        seedPreset.gameName = gameName.trim().ifBlank { "Custom Game" }
        return saveLayoutAs(context, rawName, seedPreset)
    }

    fun duplicatePreset(context: Context, sourceFileName: String, newRawName: String): String {
        val loaded = loadLayoutByName(context, sourceFileName)
        if (loaded.gameName.isBlank()) {
            loaded.gameName = sourceFileName.removeSuffix(".json").replace("_", " ")
        }
        return saveLayoutAs(context, newRawName, loaded)
    }

    fun renamePreset(context: Context, oldFileName: String, newRawName: String): String {
        val dir = getControlMapDir(context)
        val oldFile = File(dir, oldFileName)
        val cleanNew = sanitizeFileName(newRawName)
        if (oldFileName == cleanNew || !oldFile.exists()) return oldFileName
        val newFile = File(dir, cleanNew)
        val loaded = CustomControls.load(oldFile)
        loaded.save(newFile)
        oldFile.delete()
        if (getActiveLayoutName(context) == oldFileName) {
            setActiveLayoutName(context, cleanNew)
        }
        return cleanNew
    }

    fun deletePreset(context: Context, fileName: String): Boolean {
        val all = listLayouts(context)
        if (all.size <= 1) return false
        val file = File(getControlMapDir(context), fileName)
        if (!file.exists()) return false
        val deleted = file.delete()
        if (deleted && getActiveLayoutName(context) == fileName) {
            val remaining = listLayouts(context).firstOrNull()?.fileName ?: DEFAULT_FPS_FILE
            setActiveLayoutName(context, remaining)
        }
        return deleted
    }

    fun saveActiveLayout(context: Context, controls: CustomControls) {
        val file = File(getControlMapDir(context), getActiveLayoutName(context))
        controls.save(file)
    }

    fun importLayoutJson(context: Context, rawFileName: String, jsonContent: String): String {
        val parsed = CustomControls.fromJson(jsonContent)
        return saveLayoutAs(context, rawFileName, parsed)
    }

    fun exportLayoutJson(context: Context, fileName: String): String {
        val file = File(getControlMapDir(context), fileName)
        return if (file.exists()) {
            CustomControls.load(file).toJson()
        } else {
            loadActiveLayout(context).toJson()
        }
    }

    fun getAimSensitivity(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_AIM_SENSITIVITY, 1.2f)
    }

    fun setAimSensitivity(context: Context, sensitivity: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_AIM_SENSITIVITY, sensitivity.coerceIn(0.2f, 5.0f))
            .apply()
    }

    fun getAimVertDampening(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_AIM_VERT_DAMPENING, 0.0f)
            .coerceIn(0.0f, 0.8f)
    }

    fun setAimVertDampening(context: Context, dampening: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_AIM_VERT_DAMPENING, dampening.coerceIn(0.0f, 0.8f))
            .apply()
    }

    fun isAimSpeedAccelEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AIM_SPEED_ACCEL_ENABLED, false)
    }

    fun setAimSpeedAccelEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AIM_SPEED_ACCEL_ENABLED, enabled)
            .apply()
    }

    fun getAimSpeedAccelMultiplier(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_AIM_SPEED_ACCEL_MULTIPLIER, 1.5f)
            .coerceIn(1.0f, 3.0f)
    }

    fun setAimSpeedAccelMultiplier(context: Context, multiplier: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_AIM_SPEED_ACCEL_MULTIPLIER, multiplier.coerceIn(1.0f, 3.0f))
            .apply()
    }

    fun getAimAntiRecoil(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AIM_ANTI_RECOIL, 0)
            .coerceIn(0, 20)
    }

    fun setAimAntiRecoil(context: Context, recoil: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_AIM_ANTI_RECOIL, recoil.coerceIn(0, 20))
            .apply()
    }

    fun getGlobalOpacity(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_GLOBAL_OPACITY, 1.0f)
            .coerceIn(0.15f, 1.0f)
    }

    fun setGlobalOpacity(context: Context, opacity: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_GLOBAL_OPACITY, opacity.coerceIn(0.15f, 1.0f))
            .apply()
    }

    fun isTouchRippleEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TOUCH_RIPPLE, true)
    }

    fun setTouchRippleEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TOUCH_RIPPLE, enabled)
            .apply()
    }

    fun isGyroEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GYRO_ENABLED, false)

    fun setGyroEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GYRO_ENABLED, enabled)
            .apply()
    }

    fun getGyroMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_GYRO_MODE, 0)
            .coerceIn(0, 2)

    fun setGyroMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_GYRO_MODE, mode.coerceIn(0, 2))
            .apply()
    }

    fun getGyroSensX(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_GYRO_SENS_X, 2.0f)
            .coerceIn(0.2f, 8.0f)

    fun setGyroSensX(context: Context, sens: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_GYRO_SENS_X, sens.coerceIn(0.2f, 8.0f))
            .apply()
    }

    fun getGyroSensY(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_GYRO_SENS_Y, 2.0f)
            .coerceIn(0.2f, 8.0f)

    fun setGyroSensY(context: Context, sens: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_GYRO_SENS_Y, sens.coerceIn(0.2f, 8.0f))
            .apply()
    }

    fun isGyroInvertX(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GYRO_INVERT_X, false)

    fun setGyroInvertX(context: Context, invert: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GYRO_INVERT_X, invert)
            .apply()
    }

    fun isGyroInvertY(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GYRO_INVERT_Y, false)

    fun setGyroInvertY(context: Context, invert: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GYRO_INVERT_Y, invert)
            .apply()
    }

    fun getGyroSmoothing(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_GYRO_SMOOTHING, 0.25f)
            .coerceIn(0f, 1.0f)

    fun setGyroSmoothing(context: Context, smoothing: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_GYRO_SMOOTHING, smoothing.coerceIn(0f, 1.0f))
            .apply()
    }

    fun isCrosshairEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CROSSHAIR_ENABLED, false)

    fun setCrosshairEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CROSSHAIR_ENABLED, enabled)
            .apply()
    }

    fun getCrosshairStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_CROSSHAIR_STYLE, 0)
            .coerceIn(0, 3)

    fun setCrosshairStyle(context: Context, style: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CROSSHAIR_STYLE, style.coerceIn(0, 3))
            .apply()
    }

    fun isHapticEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAPTIC_ENABLED, true)

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAPTIC_ENABLED, enabled)
            .apply()
    }

    fun isAutoPauseAimOnMenu(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_PAUSE_AIM_ON_MENU, true)

    fun setAutoPauseAimOnMenu(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_PAUSE_AIM_ON_MENU, enabled)
            .apply()
    }

    fun isPositionsLocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCK_POSITIONS, false)

    fun setPositionsLocked(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOCK_POSITIONS, locked)
            .apply()
    }

    fun isAutoLoadLastPreset(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_LOAD_LAST, true)

    fun setAutoLoadLastPreset(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_LOAD_LAST, enabled)
            .apply()
    }

    fun getLastSeenChangelogVersionCode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_CHANGELOG_VERSION, 0)

    fun setLastSeenChangelogVersionCode(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_CHANGELOG_VERSION, versionCode)
            .apply()
    }

    fun createDefaultFpsPreset(): CustomControls {
        val controls = CustomControls().apply {
            gameName = "Warzone / CODM (FPS)"
        }

        controls.mJoystickDataList.add(ControlJoystickData().apply {
            name = "WASD"
            dynamicX = "0.06 * ${'$'}{screen_width}"
            dynamicY = "0.48 * ${'$'}{screen_height}"
            width = 148f
            height = 148f
            forwardLock = true
            absolute = false
        })

        controls.mControlDataList.add(
            ControlData(
                "Shoot",
                intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSEPRI, 0, 0, 0),
                "0.83 * ${'$'}{screen_width}",
                "0.58 * ${'$'}{screen_height}",
                74f,
                74f
            ).apply {
                bgColor = 0x993A3A3A.toInt()
                strokeColor = 0xFFFFFFFF.toInt()
                cornerRadius = 100f
                passThruEnabled = true
            }
        )

        controls.mControlDataList.add(
            ControlData(
                "Scope",
                intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSESEC, 0, 0, 0),
                "0.90 * ${'$'}{screen_width}",
                "0.40 * ${'$'}{screen_height}",
                66f,
                56f
            ).apply {
                bgColor = 0x802C2C2C.toInt()
                strokeColor = 0xCCFFFFFF.toInt()
            }
        )

        controls.mControlDataList.add(
            ControlData(
                "Jump",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_SPACE, 0, 0, 0),
                "0.91 * ${'$'}{screen_width}",
                "0.68 * ${'$'}{screen_height}",
                62f,
                56f
            ).apply { cornerRadius = 100f }
        )

        controls.mControlDataList.add(
            ControlData(
                "Crouch",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, 0, 0, 0),
                "0.75 * ${'$'}{screen_width}",
                "0.76 * ${'$'}{screen_height}",
                66f,
                48f
            ).apply { isToggle = true }
        )

        controls.mControlDataList.add(
            ControlData(
                "Reload",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_R, 0, 0, 0),
                "0.82 * ${'$'}{screen_width}",
                "0.34 * ${'$'}{screen_height}",
                58f,
                48f
            )
        )

        controls.mControlDataList.add(
            ControlData(
                "Use",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_F, 0, 0, 0),
                "0.73 * ${'$'}{screen_width}",
                "0.46 * ${'$'}{screen_height}",
                54f,
                48f
            )
        )

        controls.mControlDataList.add(
            ControlData(
                "Sprint",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, 0, 0, 0),
                "0.04 * ${'$'}{screen_width}",
                "0.30 * ${'$'}{screen_height}",
                64f,
                44f
            ).apply { isToggle = true }
        )

        controls.mControlDataList.add(
            ControlData(
                "Plate",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_4, 0, 0, 0),
                "0.73 * ${'$'}{screen_width}",
                "0.60 * ${'$'}{screen_height}",
                58f,
                46f
            )
        )

        controls.mControlDataList.add(
            ControlData(
                "TAB",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_TAB, 0, 0, 0),
                "0.16 * ${'$'}{screen_width}",
                "0.08 * ${'$'}{screen_height}",
                54f,
                38f
            )
        )
        controls.mControlDataList.add(
            ControlData(
                "ESC",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_ESCAPE, 0, 0, 0),
                "0.04 * ${'$'}{screen_width}",
                "0.08 * ${'$'}{screen_height}",
                54f,
                38f
            )
        )
        controls.mControlDataList.add(
            ControlData(
                "Map",
                intArrayOf(LwjglGlfwKeycode.GLFW_KEY_M, 0, 0, 0),
                "0.24 * ${'$'}{screen_width}",
                "0.08 * ${'$'}{screen_height}",
                52f,
                38f
            )
        )

        val weaponDrawer = ControlDrawerData().apply {
            properties = ControlData(
                "Menu",
                intArrayOf(0, 0, 0, 0),
                "0.90 * ${'$'}{screen_width}",
                "0.10 * ${'$'}{screen_height}",
                70f,
                40f
            ).apply {
                bgColor = 0x802C2C2C.toInt()
                strokeColor = 0xCCFFFFFF.toInt()
            }
            orientation = ControlDrawerData.ORIENTATION_DOWN
            buttonProperties.add(
                ControlData("1", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_1, 0, 0, 0), "0", "0", 54f, 40f)
            )
            buttonProperties.add(
                ControlData("2", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_2, 0, 0, 0), "0", "0", 54f, 40f)
            )
            buttonProperties.add(
                ControlData("3", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_3, 0, 0, 0), "0", "0", 54f, 40f)
            )
            buttonProperties.add(
                ControlData("G", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_G, 0, 0, 0), "0", "0", 54f, 40f)
            )
        }
        controls.mDrawerDataList.add(weaponDrawer)
        controls.ensureValid()
        return controls
    }

    fun createDefaultActionPreset(): CustomControls {
        val controls = CustomControls().apply {
            gameName = "Action / RPG"
        }
        controls.mJoystickDataList.add(ControlJoystickData().apply {
            name = "WASD"
            dynamicX = "0.06 * ${'$'}{screen_width}"
            dynamicY = "0.50 * ${'$'}{screen_height}"
            width = 140f
            height = 140f
        })
        controls.mControlDataList.add(
            ControlData("Q", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_Q, 0, 0, 0), "0.75 * ${'$'}{screen_width}", "0.68 * ${'$'}{screen_height}", 56f, 56f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("E", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_E, 0, 0, 0), "0.82 * ${'$'}{screen_width}", "0.52 * ${'$'}{screen_height}", 56f, 56f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("R", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_R, 0, 0, 0), "0.90 * ${'$'}{screen_width}", "0.40 * ${'$'}{screen_height}", 58f, 58f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("Primary", intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSEPRI, 0, 0, 0), "0.86 * ${'$'}{screen_width}", "0.68 * ${'$'}{screen_height}", 72f, 72f).apply {
                cornerRadius = 100f
                passThruEnabled = true
            }
        )
        controls.ensureValid()
        return controls
    }

    fun createArenaShooterPreset(): CustomControls {
        val controls = CustomControls().apply {
            gameName = "Tactical Arena Shooter"
        }
        controls.mJoystickDataList.add(ControlJoystickData().apply {
            name = "WASD"
            dynamicX = "0.06 * ${'$'}{screen_width}"
            dynamicY = "0.50 * ${'$'}{screen_height}"
            width = 144f
            height = 144f
            forwardLock = false
        })
        controls.mControlDataList.add(
            ControlData("Fire", intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSEPRI, 0, 0, 0), "0.84 * ${'$'}{screen_width}", "0.60 * ${'$'}{screen_height}", 74f, 74f).apply {
                cornerRadius = 100f
                passThruEnabled = true
            }
        )
        controls.mControlDataList.add(
            ControlData("ADS", intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSESEC, 0, 0, 0), "0.90 * ${'$'}{screen_width}", "0.42 * ${'$'}{screen_height}", 60f, 52f)
        )
        controls.mControlDataList.add(
            ControlData("Jump", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_SPACE, 0, 0, 0), "0.90 * ${'$'}{screen_width}", "0.74 * ${'$'}{screen_height}", 58f, 58f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("Walk", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, 0, 0, 0), "0.05 * ${'$'}{screen_width}", "0.32 * ${'$'}{screen_height}", 58f, 44f).apply { isToggle = true }
        )
        controls.mControlDataList.add(
            ControlData("Crouch", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, 0, 0, 0), "0.75 * ${'$'}{screen_width}", "0.76 * ${'$'}{screen_height}", 60f, 46f).apply { isToggle = true }
        )
        controls.mControlDataList.add(
            ControlData("Q", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_Q, 0, 0, 0), "0.73 * ${'$'}{screen_width}", "0.48 * ${'$'}{screen_height}", 52f, 52f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("E", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_E, 0, 0, 0), "0.80 * ${'$'}{screen_width}", "0.38 * ${'$'}{screen_height}", 52f, 52f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("Ult X", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_X, 0, 0, 0), "0.88 * ${'$'}{screen_width}", "0.24 * ${'$'}{screen_height}", 58f, 46f)
        )
        controls.mControlDataList.add(
            ControlData("Reload", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_R, 0, 0, 0), "0.74 * ${'$'}{screen_width}", "0.60 * ${'$'}{screen_height}", 56f, 46f)
        )
        controls.mControlDataList.add(
            ControlData("Buy B", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_B, 0, 0, 0), "0.14 * ${'$'}{screen_width}", "0.08 * ${'$'}{screen_height}", 56f, 38f)
        )
        controls.ensureValid()
        return controls
    }

    fun createMobaArpgPreset(): CustomControls {
        val controls = CustomControls().apply {
            gameName = "MOBA / ARPG"
        }
        controls.mJoystickDataList.add(ControlJoystickData().apply {
            name = "Move"
            dynamicX = "0.06 * ${'$'}{screen_width}"
            dynamicY = "0.52 * ${'$'}{screen_height}"
            width = 144f
            height = 144f
        })
        controls.mControlDataList.add(
            ControlData("Atk", intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSEPRI, 0, 0, 0), "0.86 * ${'$'}{screen_width}", "0.68 * ${'$'}{screen_height}", 74f, 74f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("Q", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_Q, 0, 0, 0), "0.74 * ${'$'}{screen_width}", "0.74 * ${'$'}{screen_height}", 54f, 54f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("W", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_W, 0, 0, 0), "0.77 * ${'$'}{screen_width}", "0.54 * ${'$'}{screen_height}", 54f, 54f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("E", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_E, 0, 0, 0), "0.84 * ${'$'}{screen_width}", "0.42 * ${'$'}{screen_height}", 54f, 54f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("R Ult", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_R, 0, 0, 0), "0.91 * ${'$'}{screen_width}", "0.32 * ${'$'}{screen_height}", 58f, 58f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("D", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_D, 0, 0, 0), "0.67 * ${'$'}{screen_width}", "0.74 * ${'$'}{screen_height}", 46f, 46f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("F", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_F, 0, 0, 0), "0.69 * ${'$'}{screen_width}", "0.58 * ${'$'}{screen_height}", 46f, 46f).apply { cornerRadius = 100f }
        )
        controls.mControlDataList.add(
            ControlData("Shop B", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_B, 0, 0, 0), "0.06 * ${'$'}{screen_width}", "0.10 * ${'$'}{screen_height}", 62f, 38f)
        )
        controls.ensureValid()
        return controls
    }

    fun createMinecraftJavaPreset(): CustomControls {
        val controls = CustomControls().apply {
            gameName = "Minecraft Java"
        }
        controls.mJoystickDataList.add(ControlJoystickData().apply {
            name = "WASD"
            dynamicX = "0.06 * ${'$'}{screen_width}"
            dynamicY = "0.48 * ${'$'}{screen_height}"
            width = 144f
            height = 144f
            forwardLock = true
        })
        controls.mControlDataList.add(
            ControlData("Break", intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSEPRI, 0, 0, 0), "0.83 * ${'$'}{screen_width}", "0.56 * ${'$'}{screen_height}", 70f, 70f).apply {
                cornerRadius = 100f
                passThruEnabled = true
            }
        )
        controls.mControlDataList.add(
            ControlData("Use", intArrayOf(LwjglGlfwKeycode.SPECIALBTN_MOUSESEC, 0, 0, 0), "0.91 * ${'$'}{screen_width}", "0.42 * ${'$'}{screen_height}", 64f, 56f)
        )
        controls.mControlDataList.add(
            ControlData("Jump", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_SPACE, 0, 0, 0), "0.90 * ${'$'}{screen_width}", "0.70 * ${'$'}{screen_height}", 64f, 56f).apply {
                cornerRadius = 100f
            }
        )
        controls.mControlDataList.add(
            ControlData("Sneak", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, 0, 0, 0), "0.74 * ${'$'}{screen_width}", "0.72 * ${'$'}{screen_height}", 64f, 48f).apply {
                isToggle = true
            }
        )
        controls.mControlDataList.add(
            ControlData("Inv E", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_E, 0, 0, 0), "0.14 * ${'$'}{screen_width}", "0.08 * ${'$'}{screen_height}", 56f, 38f)
        )
        controls.mControlDataList.add(
            ControlData("Drop Q", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_Q, 0, 0, 0), "0.73 * ${'$'}{screen_width}", "0.48 * ${'$'}{screen_height}", 54f, 44f)
        )
        controls.mControlDataList.add(
            ControlData("ESC", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_ESCAPE, 0, 0, 0), "0.04 * ${'$'}{screen_width}", "0.08 * ${'$'}{screen_height}", 54f, 38f)
        )
        controls.mControlDataList.add(
            ControlData("F3", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_F3, 0, 0, 0), "0.23 * ${'$'}{screen_width}", "0.08 * ${'$'}{screen_height}", 50f, 38f)
        )
        controls.mControlDataList.add(
            ControlData("F5", intArrayOf(LwjglGlfwKeycode.GLFW_KEY_F5, 0, 0, 0), "0.31 * ${'$'}{screen_width}", "0.08 * ${'$'}{screen_height}", 50f, 38f)
        )
        val hotbar = ControlDrawerData().apply {
            properties = ControlData("Hotbar", intArrayOf(0, 0, 0, 0), "0.88 * ${'$'}{screen_width}", "0.08 * ${'$'}{screen_height}", 74f, 38f)
            orientation = ControlDrawerData.ORIENTATION_DOWN
            for (slot in 1..5) {
                buttonProperties.add(
                    ControlData(slot.toString(), intArrayOf(LwjglGlfwKeycode.GLFW_KEY_0 + slot, 0, 0, 0), "0", "0", 48f, 38f)
                )
            }
        }
        controls.mDrawerDataList.add(hotbar)
        controls.ensureValid()
        return controls
    }

    fun createBlankCanvasPreset(): CustomControls {
        return CustomControls().apply {
            gameName = "Blank Canvas"
            ensureValid()
        }
    }
}
