package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.customcontrols.ControlLayoutManager
import com.example.input.LwjglGlfwKeycode
import com.example.input.MacroCondition
import com.example.input.MacroDefinition
import com.example.input.MacroEngine
import com.example.input.MacroStep
import com.example.input.MacroStepType
import com.example.ui.theme.BgDarkGrey
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SurfaceElevatedGrey
import com.example.ui.theme.SurfaceGrey

@Composable
fun MacroBuilderDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var macros by remember { mutableStateOf(ControlLayoutManager.getMacros(context).toList()) }
    var selectedMacroId by remember { mutableStateOf(macros.firstOrNull()?.id) }
    var editingReleasePhase by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(MacroEngine.isRecording) }
    var showImportDialog by remember { mutableStateOf(false) }
    val testLogs = remember { mutableStateListOf<String>() }

    // Editable fields synchronized when switching selectedMacroId
    val selected = macros.firstOrNull { it.id == selectedMacroId }
    var macroName by remember(selected?.id) { mutableStateOf(selected?.name ?: "") }
    var condition by remember(selected?.id) { mutableStateOf(selected?.condition ?: MacroCondition.ALWAYS) }
    var loopEnabled by remember(selected?.id) { mutableStateOf(selected?.loopEnabled ?: false) }
    var cps by remember(selected?.id) { mutableIntStateOf(selected?.cps ?: 8) }
    var jitterMs by remember(selected?.id) { mutableIntStateOf(selected?.humanizerJitterMs ?: 6) }
    var cooldownMs by remember(selected?.id) { mutableIntStateOf((selected?.cooldownMs ?: 0L).toInt()) }
    var cancelOnInterrupt by remember(selected?.id) { mutableStateOf(selected?.cancelOnInterrupt ?: true) }
    var pressSteps by remember(selected?.id) { mutableStateOf(selected?.onPressSteps?.toList() ?: emptyList()) }
    var releaseSteps by remember(selected?.id) { mutableStateOf(selected?.onReleaseSteps?.toList() ?: emptyList()) }

    fun persistCurrentEdits() {
        val currentId = selectedMacroId ?: return
        val updatedList = macros.map { m ->
            if (m.id == currentId) {
                m.copy(
                    name = macroName.ifBlank { "Macro" },
                    onPressSteps = pressSteps.toMutableList(),
                    onReleaseSteps = releaseSteps.toMutableList(),
                    condition = condition,
                    loopEnabled = loopEnabled,
                    cps = cps.coerceIn(1, 20),
                    humanizerJitterMs = jitterMs.coerceIn(0, 15),
                    cooldownMs = cooldownMs.toLong().coerceAtLeast(0L),
                    cancelOnInterrupt = cancelOnInterrupt
                )
            } else m
        }
        macros = updatedList
        ControlLayoutManager.saveMacros(context, updatedList)
    }

    AlertDialog(
        onDismissRequest = {
            if (isRecording) {
                MacroEngine.stopRecording()
            }
            persistCurrentEdits()
            onDismiss()
        },
        containerColor = SurfaceGrey,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pro Macro & Combo Builder",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Assign saved macros to any HUD button via Edit Control",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedGrey
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Macro Management Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            persistCurrentEdits()
                            val newMacro = MacroDefinition(
                                id = "macro_${System.currentTimeMillis()}",
                                name = "Combo ${macros.size + 1}",
                                onPressSteps = mutableListOf(
                                    MacroStep(MacroStepType.KEY_TAP, code = LwjglGlfwKeycode.GLFW_KEY_R),
                                    MacroStep(MacroStepType.WAIT_MS, delayMs = 60L)
                                )
                            )
                            val next = macros + newMacro
                            macros = next
                            ControlLayoutManager.saveMacros(context, next)
                            selectedMacroId = newMacro.id
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New Macro", modifier = Modifier.size(16.dp), tint = PureWhite)
                        Spacer(Modifier.width(4.dp))
                        Text("New", color = PureWhite, style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderGrey)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Import Macro", modifier = Modifier.size(16.dp), tint = SoftWhite)
                        Spacer(Modifier.width(4.dp))
                        Text("Import", color = SoftWhite, style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Macro Selector List
                if (macros.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = BgDarkGrey,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderGrey)
                    ) {
                        Text(
                            text = "No macros created yet. Tap '+ New' to build a rapid-fire loop, drop-shot combo, or quick-switch macro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGrey,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        macros.forEach { m ->
                            val active = m.id == selectedMacroId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        persistCurrentEdits()
                                        selectedMacroId = m.id
                                    },
                                color = if (active) SurfaceElevatedGrey else BgDarkGrey,
                                border = BorderStroke(1.dp, if (active) PureWhite else BorderGrey),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (active && macroName.isNotBlank()) macroName else m.name,
                                            color = PureWhite,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${m.onPressSteps.size} press / ${m.onReleaseSteps.size} rel · ${m.condition.label}",
                                            color = MutedGrey,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            persistCurrentEdits()
                                            val updated = macros.firstOrNull { it.id == m.id } ?: m
                                            val json = MacroDefinition.toJson(updated)
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText(updated.name, json))
                                            Toast.makeText(context, "Copied macro JSON", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Export Macro JSON", tint = SoftWhite, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = {
                                            val next = macros.filterNot { it.id == m.id }
                                            macros = next
                                            ControlLayoutManager.saveMacros(context, next)
                                            if (selectedMacroId == m.id) {
                                                selectedMacroId = next.firstOrNull()?.id
                                            }
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Macro", tint = MutedGrey, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Macro Detail Editor
                if (selected != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = BgDarkGrey,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderGrey)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = macroName,
                                onValueChange = { macroName = it },
                                label = { Text("Macro Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Condition Chips
                            Text("Condition", style = MaterialTheme.typography.labelMedium, color = MutedGrey)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MacroCondition.entries.forEach { cond ->
                                    val sel = cond == condition
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { condition = cond },
                                        color = if (sel) PureWhite else SurfaceElevatedGrey,
                                        border = BorderStroke(1.dp, if (sel) PureWhite else BorderGrey),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = when (cond) {
                                                MacroCondition.ALWAYS -> "Always"
                                                MacroCondition.ADS_ONLY -> "ADS Only"
                                                MacroCondition.SPRINT_ONLY -> "Sprint"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (sel) BgDarkGrey else PureWhite,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Rapid Fire & Humanizer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Loop / Rapid-Fire", color = PureWhite, style = MaterialTheme.typography.bodyMedium)
                                    Text("Repeat while held with ±${jitterMs}ms humanizer jitter", color = MutedGrey, style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(
                                    checked = loopEnabled,
                                    onCheckedChange = { loopEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = BgDarkGrey, checkedTrackColor = PureWhite)
                                )
                            }
                            if (loopEnabled) {
                                Text("Rate: $cps CPS  ·  Humanizer Jitter: ±${jitterMs}ms", color = SoftWhite, style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = cps.toFloat(),
                                    onValueChange = { cps = it.toInt().coerceIn(1, 20) },
                                    valueRange = 1f..20f,
                                    colors = SliderDefaults.colors(thumbColor = PureWhite, activeTrackColor = PureWhite, inactiveTrackColor = BorderGrey)
                                )
                                Slider(
                                    value = jitterMs.toFloat(),
                                    onValueChange = { jitterMs = it.toInt().coerceIn(0, 15) },
                                    valueRange = 0f..15f,
                                    colors = SliderDefaults.colors(thumbColor = PureWhite, activeTrackColor = PureWhite, inactiveTrackColor = BorderGrey)
                                )
                            }

                            Text("Cooldown: ${cooldownMs}ms", color = SoftWhite, style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = cooldownMs.toFloat(),
                                onValueChange = { cooldownMs = (it.toInt() / 50) * 50 },
                                valueRange = 0f..3000f,
                                colors = SliderDefaults.colors(thumbColor = PureWhite, activeTrackColor = PureWhite, inactiveTrackColor = BorderGrey)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Cancel on Jump/Sprint/ESC", color = PureWhite, style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = cancelOnInterrupt,
                                    onCheckedChange = { cancelOnInterrupt = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = BgDarkGrey, checkedTrackColor = PureWhite)
                                )
                            }

                            // On-Press vs On-Release Phase Tabs + Live Recorder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(false to "On Press (${pressSteps.size})", true to "On Release (${releaseSteps.size})").forEach { (isRel, lbl) ->
                                    val sel = editingReleasePhase == isRel
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { editingReleasePhase = isRel },
                                        color = if (sel) PureWhite else SurfaceElevatedGrey,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = if (sel) BgDarkGrey else PureWhite,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Live Recorder + Quick Step Builders
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isRecording) {
                                            MacroEngine.startRecording()
                                            isRecording = true
                                        } else {
                                            val captured = MacroEngine.stopRecording()
                                            isRecording = false
                                            if (editingReleasePhase) {
                                                releaseSteps = releaseSteps + captured
                                            } else {
                                                pressSteps = pressSteps + captured
                                            }
                                            Toast.makeText(context, "Captured ${captured.size} steps", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isRecording) PureWhite else SurfaceElevatedGrey,
                                        contentColor = if (isRecording) BgDarkGrey else PureWhite
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                        contentDescription = "Record Macro",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isRecording) "Stop Rec" else "Record", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val nextStep = MacroStep(MacroStepType.WAIT_MS, delayMs = 45L)
                                        if (editingReleasePhase) releaseSteps = releaseSteps + nextStep else pressSteps = pressSteps + nextStep
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+ Wait 45ms", color = PureWhite, style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "MB1 Tap" to MacroStep(MacroStepType.MOUSE_BTN_TAP, code = 1),
                                    "Crouch C" to MacroStep(MacroStepType.KEY_TAP, code = LwjglGlfwKeycode.GLFW_KEY_C),
                                    "Space" to MacroStep(MacroStepType.KEY_TAP, code = LwjglGlfwKeycode.GLFW_KEY_SPACE),
                                    "Turn 180" to MacroStep(MacroStepType.MOVE_MOUSE, dx = 620f, dy = 0f)
                                ).forEach { (lbl, step) ->
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                if (editingReleasePhase) releaseSteps = releaseSteps + step else pressSteps = pressSteps + step
                                            },
                                        color = SurfaceElevatedGrey,
                                        border = BorderStroke(1.dp, BorderGrey),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "+$lbl",
                                            color = SoftWhite,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Step Sequence List
                            val activeSteps = if (editingReleasePhase) releaseSteps else pressSteps
                            activeSteps.forEachIndexed { idx, step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceGrey)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${step.describe()}",
                                        color = SoftWhite,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFamily
                                    )
                                    TextButton(onClick = {
                                        val mutable = activeSteps.toMutableList()
                                        mutable.removeAt(idx)
                                        if (editingReleasePhase) releaseSteps = mutable else pressSteps = mutable
                                    }) {
                                        Text("Remove", color = MutedGrey, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Test Macro Step-by-Step Debugger
                            OutlinedButton(
                                onClick = {
                                    persistCurrentEdits()
                                    testLogs.clear()
                                    val targetMacro = macros.firstOrNull { it.id == selectedMacroId } ?: return@OutlinedButton
                                    MacroEngine.testMacro(targetMacro, dryRunOnly = true) { logLine ->
                                        testLogs.add(logLine)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.65f))
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Test Macro", tint = PureWhite, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Test Macro Sequence (Dry-Run)", color = PureWhite, style = MaterialTheme.typography.labelMedium)
                            }

                            if (testLogs.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceGrey)
                                        .padding(8.dp)
                                ) {
                                    testLogs.forEach { line ->
                                        Text(
                                            text = line,
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SoftWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isRecording) {
                    MacroEngine.stopRecording()
                }
                persistCurrentEdits()
                onDismiss()
            }) {
                Text("Save & Close", color = PureWhite, fontWeight = FontWeight.Bold)
            }
        }
    )

    if (showImportDialog) {
        var importJson by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = SurfaceGrey,
            title = { Text("Import Macro JSON", color = PureWhite) },
            text = {
                OutlinedTextField(
                    value = importJson,
                    onValueChange = { importJson = it },
                    label = { Text("Paste Macro JSON") },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = MacroDefinition.fromJson(importJson)
                    if (parsed != null) {
                        val copy = parsed.copy(id = "macro_${System.currentTimeMillis()}")
                        val next = macros + copy
                        macros = next
                        ControlLayoutManager.saveMacros(context, next)
                        selectedMacroId = copy.id
                        showImportDialog = false
                        Toast.makeText(context, "Imported ${copy.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid macro JSON", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Import", color = PureWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = MutedGrey)
                }
            }
        )
    }
}
