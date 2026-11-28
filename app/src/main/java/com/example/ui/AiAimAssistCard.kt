package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.customcontrols.ControlLayoutManager
import com.example.ui.theme.BgDarkGrey
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SubtleGrey
import com.example.ui.theme.SurfaceElevatedGrey
import com.example.ui.theme.SurfaceGrey
import com.example.vision.AiVisionTerminalBus
import com.example.vision.ScreenAssistService
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun strengthToSliderStep(strength: Float): Int {
    if (strength < 0.25f) return 0
    val clamped = strength.coerceIn(0.5f, 5.0f)
    return (1 + ((clamped - 0.5f) * 10f).roundToInt()).coerceIn(1, 46)
}

private fun sliderStepToStrength(step: Int): Float {
    if (step <= 0) return 0.0f
    return (0.5f + ((step - 1) * 0.1f)).coerceIn(0.5f, 5.0f)
}

private fun createCaptureIntent(context: Context): Intent {
    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
    } else {
        mpm.createScreenCaptureIntent()
    }
}

@Composable
fun AiAimAssistCard(
    onSyncOverlay: () -> Unit
) {
    val context = LocalContext.current

    var strength by remember { mutableFloatStateOf(ControlLayoutManager.getAiAimStrength(context)) }
    var gatePct by remember { mutableFloatStateOf(ControlLayoutManager.getAiCrosshairGatePct(context)) }
    var teamFilterEnabled by remember { mutableStateOf(ControlLayoutManager.isAiTeamFilterEnabled(context)) }
    var ocrNamesEnabled by remember { mutableStateOf(ControlLayoutManager.isAiOcrNamesEnabled(context)) }

    val isScreenVisionActive by AiVisionTerminalBus.isScreenVisionActive.collectAsStateWithLifecycle()
    val statusHeadline by AiVisionTerminalBus.statusHeadline.collectAsStateWithLifecycle()
    val activeTarget by AiVisionTerminalBus.activeTarget.collectAsStateWithLifecycle()
    val terminalLogs by AiVisionTerminalBus.terminalLogs.collectAsStateWithLifecycle()

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            if (strength < 0.5f) {
                strength = 1.2f
                ControlLayoutManager.setAiAimStrength(context, 1.2f)
            }
            val serviceIntent = Intent(context, ScreenAssistService::class.java).apply {
                action = ScreenAssistService.ACTION_START_CAPTURE
                putExtra(ScreenAssistService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenAssistService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            onSyncOverlay()
        } else {
            AiVisionTerminalBus.appendLog("SYSTEM: Screen capture permission declined.")
            Toast.makeText(context, "Screen capture permission required for AI Vision", Toast.LENGTH_SHORT).show()
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        projectionLauncher.launch(createCaptureIntent(context))
    }

    fun startOrStopVision() {
        if (isScreenVisionActive) {
            context.startService(
                Intent(context, ScreenAssistService::class.java).apply {
                    action = ScreenAssistService.ACTION_STOP_CAPTURE
                }
            )
            onSyncOverlay()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        projectionLauncher.launch(createCaptureIntent(context))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceGrey),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "AI Vision Aim Assist",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Vision Aim Assist (Macro)",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isScreenVisionActive && strength >= 0.5f) PureWhite else SurfaceElevatedGrey,
                    border = BorderStroke(1.dp, if (isScreenVisionActive) PureWhite else BorderGrey)
                ) {
                    Text(
                        text = when {
                            isScreenVisionActive && strength >= 0.5f -> "LIVE"
                            isScreenVisionActive -> "ARMED"
                            else -> "OFF"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isScreenVisionActive && strength >= 0.5f) BgDarkGrey else SoftWhite,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "Real-time screen vision macro that locks only onto hostile red nametags near your crosshair while skipping Warzone/FPS squad teammates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGrey
            )

            // Start / Stop Screen Vision Macro Button
            Button(
                onClick = { startOrStopVision() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("ai_vision_toggle_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScreenVisionActive) SurfaceElevatedGrey else PureWhite,
                    contentColor = if (isScreenVisionActive) PureWhite else BgDarkGrey
                ),
                border = if (isScreenVisionActive) BorderStroke(1.dp, PureWhite) else null
            ) {
                Icon(
                    imageVector = if (isScreenVisionActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isScreenVisionActive) "Stop AI Screen Vision" else "Start AI Screen Vision"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScreenVisionActive) "Stop Real-Time Screen Vision" else "Start Real-Time Screen Vision",
                    fontWeight = FontWeight.Bold
                )
            }

            // Preset Chips: Off / Low / Med / High
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    "Off" to 0.0f,
                    "Low (1.2)" to 1.2f,
                    "Med (2.5)" to 2.5f,
                    "High (4.2)" to 4.2f
                )
                presets.forEach { (label, presetVal) ->
                    val isSelected = if (presetVal == 0.0f) {
                        strength < 0.25f
                    } else {
                        abs(strength - presetVal) < 0.15f
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .testTag("ai_preset_${label.lowercase(Locale.US).substringBefore(" ")}"),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) PureWhite else SurfaceElevatedGrey,
                        border = BorderStroke(1.dp, if (isSelected) PureWhite else BorderGrey),
                        onClick = {
                            strength = presetVal
                            ControlLayoutManager.setAiAimStrength(context, presetVal)
                            onSyncOverlay()
                            AiVisionTerminalBus.appendLog(
                                "PRESET: Strength set to ${if (presetVal < 0.5f) "OFF" else String.format(Locale.US, "%.1f", presetVal)} ($label)"
                            )
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) BgDarkGrey else PureWhite,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Fine Strength Slider: Off (0.0) -> 0.5 .. 5.0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Aim Assist Strength",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Text(
                    text = if (strength < 0.5f) "OFF" else String.format(Locale.US, "%.1f / 5.0", strength),
                    style = MaterialTheme.typography.labelMedium,
                    color = PureWhite
                )
            }
            val currentStep = strengthToSliderStep(strength)
            Slider(
                value = currentStep.toFloat(),
                onValueChange = { raw ->
                    val next = sliderStepToStrength(raw.roundToInt())
                    strength = next
                    ControlLayoutManager.setAiAimStrength(context, next)
                    onSyncOverlay()
                },
                valueRange = 0f..46f,
                steps = 45,
                modifier = Modifier.testTag("ai_strength_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = PureWhite,
                    activeTrackColor = PureWhite,
                    inactiveTrackColor = BorderGrey
                )
            )

            // Crosshair Proximity Gate Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Crosshair Activation Gate",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Text(
                    text = "${(gatePct * 100f).roundToInt()}% screen",
                    style = MaterialTheme.typography.labelMedium,
                    color = PureWhite
                )
            }
            Slider(
                value = gatePct,
                onValueChange = { next ->
                    gatePct = next
                    ControlLayoutManager.setAiCrosshairGatePct(context, next)
                },
                valueRange = 0.10f..0.40f,
                steps = 14,
                modifier = Modifier.testTag("ai_gate_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = PureWhite,
                    activeTrackColor = PureWhite,
                    inactiveTrackColor = BorderGrey
                )
            )
            Text(
                text = "Only steers when your crosshair is already near an enemy red nametag (outside this radius = 0 steer).",
                style = MaterialTheme.typography.labelMedium,
                color = MutedGrey
            )

            // Teammate Guide Filter Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Teammate Guide Exclusion",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                    Text(
                        text = "Skips targets with Warzone blue/cyan/green/purple/yellow squad markers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedGrey
                    )
                }
                Switch(
                    checked = teamFilterEnabled,
                    onCheckedChange = { checked ->
                        teamFilterEnabled = checked
                        ControlLayoutManager.setAiTeamFilterEnabled(context, checked)
                    },
                    modifier = Modifier.testTag("ai_team_filter_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDarkGrey,
                        checkedTrackColor = PureWhite,
                        uncheckedThumbColor = MutedGrey,
                        uncheckedTrackColor = BgDarkGrey
                    )
                )
            }

            // Live OCR Name Reader Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Read Enemy Player Names (OCR)",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                    Text(
                        text = "Reads visible red nametag text onto the live terminal below",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedGrey
                    )
                }
                Switch(
                    checked = ocrNamesEnabled,
                    onCheckedChange = { checked ->
                        ocrNamesEnabled = checked
                        ControlLayoutManager.setAiOcrNamesEnabled(context, checked)
                    },
                    modifier = Modifier.testTag("ai_ocr_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDarkGrey,
                        checkedTrackColor = PureWhite,
                        uncheckedThumbColor = MutedGrey,
                        uncheckedTrackColor = BgDarkGrey
                    )
                )
            }

            // Live AI Vision Terminal Directly Below Aim Assist
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_vision_terminal"),
                color = Color(0xFF121212),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderGrey)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            activeTarget?.isLocked == true -> Color(0xFFFF5252)
                                            isScreenVisionActive -> Color(0xFF69F0AE)
                                            else -> SubtleGrey
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = "AI Vision Terminal",
                                tint = SoftWhite,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI VISION TERMINAL",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = PureWhite
                            )
                        }
                        IconButton(
                            onClick = { AiVisionTerminalBus.clearLogs() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("ai_terminal_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear AI Terminal Logs",
                                tint = MutedGrey,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Live Telemetry Status Line
                    Text(
                        text = statusHeadline,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (activeTarget?.isLocked == true) PureWhite else SoftWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val tgt = activeTarget
                    Text(
                        text = if (tgt != null) {
                            "TARGET: ${tgt.targetName} | TAG: ${tgt.tagType} | OFF: (${tgt.dxPx}, ${tgt.dyPx})px | TEAM SKIPPED: ${tgt.teammatesSkipped}"
                        } else {
                            "TARGET: -- | TAG: NONE | OFF: (0, 0)px | TEAM SKIPPED: 0"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = MutedGrey,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    HorizontalDivider(color = BorderGrey.copy(alpha = 0.6f))

                    val logScrollState = rememberScrollState()
                    LaunchedEffect(terminalLogs.size) {
                        logScrollState.animateScrollTo(logScrollState.maxValue)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 160.dp)
                            .verticalScroll(logScrollState),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        terminalLogs.forEach { entry ->
                            Text(
                                text = entry,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = when {
                                    entry.contains("TARGET READ:") -> Color(0xFFFF8A80)
                                    entry.contains("TEAMMATE SKIPPED:") -> Color(0xFF80D8FF)
                                    entry.contains("GUARD:") -> Color(0xFFFFD180)
                                    else -> SoftWhite
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
