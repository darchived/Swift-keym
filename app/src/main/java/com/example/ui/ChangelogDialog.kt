package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.input.ShizukuHelper
import com.example.ui.theme.BgDarkGrey
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SurfaceElevatedGrey
import com.example.ui.theme.SurfaceGrey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class ChangelogRelease(
    val version: String,
    val tag: String,
    val items: List<String>
)

private val CHANGELOG_RELEASES = listOf(
    ChangelogRelease(
        version = "v1.0.1-fixed",
        tag = "Hotfix Release",
        items = listOf(
            "AI Screen Vision Aim Assist (Macro) & Live Terminal: Real-time screen vision with Off / Low / Med / High presets (0.5–5.0), Warzone teammate squad-marker exclusion, crosshair proximity activation gate, bundled offline ML Kit nametag OCR, and live telemetry terminal.",
            "Full-Time Virtual Mouse with 3D Look + Cursor Mode: Seamless switching between 3D Aim Look Mode and Cursor / Menu Mode, plus Floating Eye (👁) View.GONE / View.VISIBLE toggling that preserves all control positions.",
            "Zero-Conflict Touch + Gyro Additive Blending: Touch swipe deltas and hardware gyroscope deltas merge additively into the unified 120Hz (8ms) float remainder accumulator with 3px touch slop and zero camera jumps on ACTION_DOWN.",
            "Clean Play Protect Release Signing: Non-debuggable release build with verified /dev/uinput teardown in OverlayService.onDestroy() so the system soft keyboard always restores cleanly."
        )
    ),
    ChangelogRelease(
        version = "v1.0.0-Released",
        tag = "Official Release",
        items = listOf(
            "Play Protect & Restricted Settings Hardening: Non-debuggable production release build, zero internet/storage/install permissions, Privacy & Safety Transparency sheet, and Shizuku self-update installer (pm install -i com.android.vending).",
            "Per-App Game Library & Auto-Switch Hub: Link installed games to specific .json HUD presets, launch with 1 tap, and optionally auto-hide the overlay outside the game.",
            "SKM1:// Share Codes & Full .skm Backup Bundles: Export or import all presets, macros, game links, and aim/gyro settings cleanly.",
            "Advanced Controls & Live HUD Pill: Snap-to-grid + alignment guides (Snap All / Center H & V), Layer Shift (Fn), Dual-Action Tap/Hold, 8-Way Radial Quick Wheel, Scroll Wheel Strip, Free-Look button, Quick 90°/180° turns, Auto-Sprint badge, Edge-Guard bezel rejection, and Live Input HUD Pill with ⚡ Release All Keys.",
            "Pro Aiming & 3-Stage Anti-Recoil Engine: Per-axis Sens X & Y, Scope Zoom levels (1x / 2x–4x / 6x+), smooth ADS transition ramp, optional Inertia Glide, Touch-to-Gyro Hybrid Blending, Dual Weapon Slots (G1/G2 auto-switched by 1/2), 3-Stage Timeline Recoil (Early/Mid/Late + Drift X), and Interactive Live Recoil Test Pad.",
            "Pro Macro & Combo Builder: Live recorder, On-Press & On-Release sequence editor, ADS/Sprint conditions, rapid-fire loops with ±humanizer jitter, cooldowns, interrupt cancellation, dry-run step debugger, and macro JSON export/import."
        )
    ),
    ChangelogRelease(
        version = "v1.8.0",
        tag = "Legacy",
        items = listOf(
            "Fixed Aim Zone Camera Jump Bug: Zero movement on ACTION_DOWN/ACTION_POINTER_DOWN, 3px touch slop filter (stationary taps move camera by 0), full accumulator & pointer reset on lift, and pure EV_REL mouse verification.",
            "Draggable Floating QWERTY Keyboard: Added Top Drag Header Bar (⠿ Drag Keyboard / ✕ Close) so you can move the virtual keyboard anywhere on screen with saved position persistence.",
            "Pass-Through Button Stability: Added 3px touch slop filtering on pass-through buttons (e.g. Shoot) so quick taps never nudge the camera."
        )
    ),
    ChangelogRelease(
        version = "v1.7.0",
        tag = "Stable",
        items = listOf(
            "Ultra-Fine Sub-Pixel Anti-Recoil (0.00–3.00 in 0.05 steps): Micro-decimal downward pull routed through the 120Hz sub-pixel float accumulator—eliminates harsh over-pulling at low settings.",
            "1-Tap Recoil Presets: Instant switching between Off (0.00), Low / SMG (0.15), Med / AR (0.35), and High / LMG (0.65).",
            "Horizontal Drift Compensation (-1.50 to +1.50): Counter left or right barrel drift while spraying full-auto.",
            "Initial Kick Boost (0%–100% for first 350ms): Extra downward compensation for the initial 3–5 bullet muzzle climb.",
            "Smart Thumb Override: Automatically subtracts manual downward thumb drag from auto-recoil pull to prevent over-pulling.",
            "CODM-Style Sticky Aim Velocity Curve: Tunable micro-tracking friction (0.30x–0.85x) on micro thumb adjustments for precision target tracking.",
            "Separate ADS / Scope Sensitivity Multiplier (0.30x–1.20x): Hipfire stays fast for turning while scoped aiming slows down for pinpoint accuracy."
        )
    ),
    ChangelogRelease(
        version = "v1.6.2",
        tag = "Stable",
        items = listOf(
            "120Hz (8ms) Aim Coalescing & Sub-Pixel Accumulator: Eliminates swipe micro-stutters and AIDL IPC flooding while preserving fractional precision.",
            "Elevated Shizuku input thread priority (THREAD_PRIORITY_URGENT_DISPLAY) for zero-latency camera look.",
            "Head-Level Horizontal Stabilizer (0%–80%): Dampens accidental vertical wobble during fast turns to keep crosshairs locked at head height.",
            "CODM Speed Acceleration (1.0x–3.0x): Dynamic flick curve allowing 1:1 micro-precision on slow drags and instant 180° turns on fast flicks.",
            "Anti-Recoil Pull-Down (0–20): Automatic 16ms downward compensation while holding Shoot (MB1) for steady full-auto spray control."
        )
    ),
    ChangelogRelease(
        version = "v1.6.1",
        tag = "Stable",
        items = listOf(
            "CRITICAL FIX: Fully restored physical and on-screen soft keyboard usability immediately upon stopping the overlay service.",
            "Enforced immediate uinput device teardown and secure settings synchronization (show_ime_with_hard_keyboard = 1) on service shutdown.",
            "Brand new Floating QWERTY Typing Keyboard (⌨) window and quick toggle buttons."
        )
    ),
    ChangelogRelease(
        version = "v1.6.0",
        tag = "Stable",
        items = listOf(
            "Brand new Floating QWERTY Typing Keyboard (⌨) window for in-game text input, chat, and shortcuts.",
            "Quick keyboard toggle button added directly to both the top floating bar and the Cogwheel menu.",
            "Robust soft keyboard restoration and uinput cleanup when stopping the overlay service."
        )
    ),
    ChangelogRelease(
        version = "v1.5.0",
        tag = "Stable",
        items = listOf(
            "Split /dev/uinput into two separate virtual devices (SwiftKeyM-VirtualMouse and SwiftKeyM-VirtualKeyboard) to fix Android EventHub classification bug where mouse axes suppressed keyboard events.",
            "All WASD joystick movement, keyboard keys, and function shortcuts now inject perfectly into GeForce NOW without being ignored.",
            "Retained permanent swiftkeym-fixed.jks signing configuration for seamless in-place updates."
        )
    ),
    ChangelogRelease(
        version = "v1.4.0",
        tag = "Stable",
        items = listOf(
            "Configured fixed static signing key (swiftkeym-fixed.jks) and debug assemble targets for flawless in-place updates without install errors.",
            "Registered full Linux keyboard keycodes (1..248) in /dev/uinput so all keyboard buttons and WASD joysticks inject correctly.",
            "Fixed Eye Show/Hide button to retain button view states in WindowManager without permanent removal.",
            "Embedded custom button icons as portable Base64 strings in JSON controlmap exports so images never break across installs.",
            "Auto-fit & scale custom button images using FIT_XY scaling to perfectly match button dimensions."
        )
    ),
    ChangelogRelease(
        version = "v1.3.0",
        tag = "Gear Menu & uinput",
        items = listOf(
            "Replaced overlay AlertDialogs with a native WindowManager Floating Settings Drawer so tapping the top Gear (⚙) menu never crashes or resets the HUD.",
            "Added FLAG_SPLIT_TOUCH across every floating window and multi-pointer tracking on Shoot/Scope/Joystick/Aim Zone so holding fire or ADS never freezes camera aiming.",
            "Added kernel /dev/uinput virtual USB Mouse & Keyboard driver (BTN_LEFT 0x110, BTN_RIGHT 0x111, REL_X/REL_Y, KEY_*) with SOURCE_MOUSE_RELATIVE fallback for GeForce NOW MB1/MB2 & 3D look."
        )
    ),
    ChangelogRelease(
        version = "v1.2.0",
        tag = "Gyro & Crosshair",
        items = listOf(
            "Hardware Gyroscope aiming engine (Always On, ADS Only, Firing Only, X/Y sensitivity, axis inversion, anti-shake smoothing).",
            "Quick-Aim and Quick-Gyro floating toggles plus bindable Toggle Gyro HUD key.",
            "Pass-through Custom Center Crosshair overlay with 4 reticle styles.",
            "Sequential 4-slot Multi-Action button combos, 12ms tactile haptics, and position locking.",
            "Auto-pause Aim Swipe Zone when opening menus via ESC, Tab, or M."
        )
    ),
    ChangelogRelease(
        version = "v1.1.0",
        tag = "Live Overlay",
        items = listOf(
            "Zero screen-freeze detached window architecture with individual control windows.",
            "Live in-game Settings Cogwheel menu with preset switching and + Create New Preset.",
            "Minecraft Java and Blank Canvas built-in presets plus Turn Off Service confirmation."
        )
    ),
    ChangelogRelease(
        version = "v1.0.0",
        tag = "Foundation",
        items = listOf(
            "Shizuku privileged input bridge for non-root key, mouse, scroll, and delta look injection.",
            "Global HUD opacity slider, visual touch ripple feedback, and restricted-settings Shizuku unlock."
        )
    )
)

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<String?>(null) }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!ShizukuHelper.isShizukuAvailable() || !ShizukuHelper.hasPermission()) {
            Toast.makeText(context, "Authorize Shizuku first to self-update.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        updateStatus = "Staging APK & running pm install via Shizuku..."
        scope.launch {
            val outFile = withContext(Dispatchers.IO) {
                val baseDir = context.externalCacheDir ?: context.cacheDir
                val target = File(baseDir, "swiftkeym_update.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
            ShizukuHelper.executeShellCommandAsync(
                context,
                "pm install -i com.android.vending -r \"${outFile.absolutePath}\""
            ) { result ->
                updateStatus = if (result.isSuccess) {
                    "✓ Installed with Play-store attribution (restricted settings bypassed)."
                } else {
                    "Install failed (${result.exitCode}): ${result.output}"
                }
            }
        }
    }

    val versionLabel = if (BuildConfig.VERSION_NAME.startsWith("v")) {
        BuildConfig.VERSION_NAME
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceGrey,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "What's New in Swift KeyM",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = SurfaceElevatedGrey,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = versionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Created by St3rlight • Team StarDevs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedGrey
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BgDarkGrey,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "Self-Update via Shizuku (Bypass Restricted Settings)",
                                color = PureWhite,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!updateStatus.isNullOrBlank()) {
                            Text(
                                text = updateStatus ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftWhite
                            )
                        }
                    }
                }

                CHANGELOG_RELEASES.forEach { release ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgDarkGrey)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = release.version,
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = SurfaceElevatedGrey,
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(1.dp, BorderGrey)
                            ) {
                                Text(
                                    text = release.tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftWhite,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        release.items.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp, end = 8.dp)
                                        .size(5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(PureWhite)
                                )
                                Text(
                                    text = entry,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SoftWhite
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = PureWhite, fontWeight = FontWeight.Bold)
            }
        }
    )
}
