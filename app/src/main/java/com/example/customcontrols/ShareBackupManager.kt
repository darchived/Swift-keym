package com.example.customcontrols

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Base64
import com.example.input.MacroDefinition
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class GameAppLink(
    val packageName: String,
    val appName: String,
    val presetFileName: String
)

data class LaunchableAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

data class SwiftKeyMBackupBundle(
    val formatVersion: Int = 1,
    val appVersion: String = "v1.0.0-Released",
    val activePreset: String = ControlLayoutManager.DEFAULT_FPS_FILE,
    val presets: Map<String, String> = emptyMap(),
    val gameLinks: List<GameAppLink> = emptyList(),
    val macros: List<MacroDefinition> = emptyList(),
    val aimSensX: Float = 1.2f,
    val aimSensY: Float = 1.2f,
    val aimAdsMultiplier: Float = 0.70f,
    val aimStickySlowdown: Float = 0.45f,
    val aimVertDampening: Float = 0f,
    val aimSpeedAccelEnabled: Boolean = false,
    val aimSpeedAccelMultiplier: Float = 1.5f,
    val aimInertia: Float = 0f,
    val aimAdsRampMs: Int = 80,
    val aimZoomLevel: Int = 0,
    val aimEdgeGuardDp: Int = 6,
    val gyroEnabled: Boolean = false,
    val gyroMode: Int = 0,
    val gyroSensX: Float = 2.0f,
    val gyroSensY: Float = 2.0f
)

/**
 * Handles Per-App Game Library links, shareable SKM1:// GZIP+Base64 preset codes,
 * and full .skm JSON bundle backup export/import.
 */
object ShareBackupManager {

    private const val SHARE_PREFIX = "SKM1://"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    fun listLaunchableApps(context: Context): List<LaunchableAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Throwable) {
            emptyList()
        }
        val selfPkg = context.packageName
        return resolveList
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == selfPkg) return@mapNotNull null
                val label = try {
                    info.loadLabel(pm).toString().ifBlank { pkg }
                } catch (_: Throwable) {
                    pkg
                }
                val icon = try {
                    info.loadIcon(pm)
                } catch (_: Throwable) {
                    null
                }
                LaunchableAppInfo(pkg, label, icon)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    fun getGameLinks(context: Context): List<GameAppLink> {
        val raw = ControlLayoutManager.getGameLinksJson(context)
        if (raw.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<GameAppLink>>() {}.type
            GSON.fromJson<List<GameAppLink>>(raw, type) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveGameLinks(context: Context, links: List<GameAppLink>) {
        ControlLayoutManager.setGameLinksJson(context, GSON.toJson(links))
    }

    fun upsertGameLink(context: Context, link: GameAppLink) {
        val current = getGameLinks(context).toMutableList()
        current.removeAll { it.packageName == link.packageName }
        current.add(0, link)
        saveGameLinks(context, current)
    }

    fun removeGameLink(context: Context, packageName: String) {
        val current = getGameLinks(context).filterNot { it.packageName == packageName }
        saveGameLinks(context, current)
    }

    fun getLinkedPresetForPackage(context: Context, packageName: String): String? {
        return getGameLinks(context).firstOrNull { it.packageName == packageName }?.presetFileName
    }

    fun encodeShareCode(controls: CustomControls): String {
        val json = controls.toJson().toByteArray(StandardCharsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gz -> gz.write(json) }
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        return SHARE_PREFIX + b64
    }

    fun decodeShareCode(rawCode: String): CustomControls {
        val trimmed = rawCode.trim()
        val payload = if (trimmed.startsWith(SHARE_PREFIX)) {
            trimmed.removePrefix(SHARE_PREFIX)
        } else if (trimmed.startsWith("{")) {
            return CustomControls.fromJson(trimmed)
        } else {
            throw IllegalArgumentException("Code must start with SKM1:// or JSON")
        }
        val compressed = Base64.decode(payload, Base64.NO_WRAP or Base64.URL_SAFE)
        val json = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(StandardCharsets.UTF_8).use {
            it.readText()
        }
        return CustomControls.fromJson(json)
    }

    fun exportFullBackupJson(context: Context): String {
        val profiles = ControlLayoutManager.listLayouts(context)
        val presetMap = linkedMapOf<String, String>()
        profiles.forEach { info ->
            presetMap[info.fileName] = ControlLayoutManager.exportLayoutJson(context, info.fileName)
        }
        val bundle = SwiftKeyMBackupBundle(
            activePreset = ControlLayoutManager.getActiveLayoutName(context),
            presets = presetMap,
            gameLinks = getGameLinks(context),
            macros = ControlLayoutManager.getMacros(context),
            aimSensX = ControlLayoutManager.getAimSensX(context),
            aimSensY = ControlLayoutManager.getAimSensY(context),
            aimAdsMultiplier = ControlLayoutManager.getAimAdsMultiplier(context),
            aimStickySlowdown = ControlLayoutManager.getAimStickySlowdown(context),
            aimVertDampening = ControlLayoutManager.getAimVertDampening(context),
            aimSpeedAccelEnabled = ControlLayoutManager.isAimSpeedAccelEnabled(context),
            aimSpeedAccelMultiplier = ControlLayoutManager.getAimSpeedAccelMultiplier(context),
            aimInertia = ControlLayoutManager.getAimInertia(context),
            aimAdsRampMs = ControlLayoutManager.getAimAdsRampMs(context),
            aimZoomLevel = ControlLayoutManager.getAimZoomLevel(context),
            aimEdgeGuardDp = ControlLayoutManager.getAimEdgeGuardDp(context),
            gyroEnabled = ControlLayoutManager.isGyroEnabled(context),
            gyroMode = ControlLayoutManager.getGyroMode(context),
            gyroSensX = ControlLayoutManager.getGyroSensX(context),
            gyroSensY = ControlLayoutManager.getGyroSensY(context)
        )
        return GSON.toJson(bundle)
    }

    fun importFullBackupJson(context: Context, json: String): Int {
        val bundle = GSON.fromJson(json, SwiftKeyMBackupBundle::class.java)
            ?: throw IllegalArgumentException("Invalid .skm backup file")
        var count = 0
        bundle.presets.forEach { (fileName, presetJson) ->
            ControlLayoutManager.importLayoutJson(context, fileName, presetJson)
            count++
        }
        if (bundle.activePreset.isNotBlank()) {
            ControlLayoutManager.setActiveLayoutName(context, bundle.activePreset)
        }
        if (bundle.gameLinks.isNotEmpty()) {
            saveGameLinks(context, bundle.gameLinks)
        }
        if (bundle.macros.isNotEmpty()) {
            ControlLayoutManager.saveMacros(context, bundle.macros)
        }
        ControlLayoutManager.setAimSensX(context, bundle.aimSensX)
        ControlLayoutManager.setAimSensY(context, bundle.aimSensY)
        ControlLayoutManager.setAimAdsMultiplier(context, bundle.aimAdsMultiplier)
        ControlLayoutManager.setAimStickySlowdown(context, bundle.aimStickySlowdown)
        ControlLayoutManager.setAimVertDampening(context, bundle.aimVertDampening)
        ControlLayoutManager.setAimSpeedAccelEnabled(context, bundle.aimSpeedAccelEnabled)
        ControlLayoutManager.setAimSpeedAccelMultiplier(context, bundle.aimSpeedAccelMultiplier)
        ControlLayoutManager.setAimInertia(context, bundle.aimInertia)
        ControlLayoutManager.setAimAdsRampMs(context, bundle.aimAdsRampMs)
        ControlLayoutManager.setAimZoomLevel(context, bundle.aimZoomLevel)
        ControlLayoutManager.setAimEdgeGuardDp(context, bundle.aimEdgeGuardDp)
        ControlLayoutManager.setGyroEnabled(context, bundle.gyroEnabled)
        ControlLayoutManager.setGyroMode(context, bundle.gyroMode)
        ControlLayoutManager.setGyroSensX(context, bundle.gyroSensX)
        ControlLayoutManager.setGyroSensY(context, bundle.gyroSensY)
        return count
    }
}
