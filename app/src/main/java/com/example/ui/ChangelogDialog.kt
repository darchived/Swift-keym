package com.example.ui

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.theme.BgDarkGrey
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SurfaceElevatedGrey
import com.example.ui.theme.SurfaceGrey

private data class ChangelogRelease(
    val version: String,
    val tag: String,
    val items: List<String>
)

private val CHANGELOG_RELEASES = listOf(
    ChangelogRelease(
        version = "v1.3.0",
        tag = "Latest",
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
                            text = "v${BuildConfig.VERSION_NAME}",
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
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
