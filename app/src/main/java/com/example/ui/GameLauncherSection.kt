package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.LayoutProfileInfo
import com.example.customcontrols.ShareBackupManager
import com.example.customcontrols.GameAppLink
import com.example.customcontrols.LaunchableAppInfo
import com.example.ui.theme.BgDarkGrey
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SurfaceElevatedGrey
import com.example.ui.theme.SurfaceGrey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GameLauncherSection(
    layouts: List<LayoutProfileInfo>,
    autoHideOutsideGame: Boolean,
    onToggleAutoHideOutsideGame: (Boolean) -> Unit,
    onLaunchLinkedGame: (GameAppLink) -> Unit
) {
    val context = LocalContext.current
    var gameLinks by remember { mutableStateOf(ShareBackupManager.getGameLinks(context)) }
    var showAddGameDialog by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = "Game Library Hub",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Game Library & Auto-Switch",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                }
                TextButton(onClick = { showAddGameDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Link Game", tint = PureWhite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Link Game", color = PureWhite)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Hide Outside Game",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhite
                    )
                    Text(
                        text = "Automatically hides overlay when switching away from launched game",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedGrey
                    )
                }
                Switch(
                    checked = autoHideOutsideGame,
                    onCheckedChange = onToggleAutoHideOutsideGame,
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDarkGrey, checkedTrackColor = PureWhite)
                )
            }

            if (gameLinks.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BgDarkGrey,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderGrey)
                ) {
                    Text(
                        text = "Link installed games to auto-load their control preset and start the overlay with one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    gameLinks.forEach { link ->
                        val appIcon = remember(link.packageName) {
                            try {
                                context.packageManager.getApplicationIcon(link.packageName).toBitmap(72, 72).asImageBitmap()
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        val presetDisplay = layouts.firstOrNull { it.fileName == link.presetFileName }?.displayName
                            ?: link.presetFileName.removeSuffix(".json")

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = BgDarkGrey,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderGrey)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (appIcon != null) {
                                        Image(
                                            bitmap = appIcon,
                                            contentDescription = link.appName,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SurfaceElevatedGrey),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = PureWhite, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = link.appName,
                                            color = PureWhite,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Preset: $presetDisplay",
                                            color = MutedGrey,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { onLaunchLinkedGame(link) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PureWhite,
                                            contentColor = BgDarkGrey
                                        )
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Launch", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Play", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(onClick = {
                                        ShareBackupManager.removeGameLink(context, link.packageName)
                                        gameLinks = ShareBackupManager.getGameLinks(context)
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Unlink Game", tint = MutedGrey, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddGameDialog) {
        var installedApps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
        var searchQuery by remember { mutableStateOf("") }
        var selectedApp by remember { mutableStateOf<LaunchableAppInfo?>(null) }
        var selectedPresetFile by remember { mutableStateOf(ControlLayoutManager.getActiveLayoutName(context)) }

        LaunchedEffect(Unit) {
            installedApps = withContext(Dispatchers.IO) {
                ShareBackupManager.listLaunchableApps(context)
            }
        }

        val filtered = remember(installedApps, searchQuery) {
            if (searchQuery.isBlank()) installedApps
            else installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showAddGameDialog = false },
            containerColor = SurfaceGrey,
            title = { Text("Link Installed Game", color = PureWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Filter apps...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("1. Choose App:", style = MaterialTheme.typography.labelMedium, color = MutedGrey)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filtered.take(40).forEach { app ->
                            val isSel = selectedApp?.packageName == app.packageName
                            val bmp = remember(app.packageName) {
                                try {
                                    app.icon?.toBitmap(48, 48)?.asImageBitmap()
                                } catch (_: Throwable) {
                                    null
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) SurfaceElevatedGrey else BgDarkGrey)
                                    .clickable { selectedApp = app }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (bmp != null) {
                                    Image(bitmap = bmp, contentDescription = app.appName, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = app.appName,
                                    color = if (isSel) PureWhite else SoftWhite,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Text("2. Link Preset:", style = MaterialTheme.typography.labelMedium, color = MutedGrey)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 110.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        layouts.forEach { p ->
                            val isSel = selectedPresetFile == p.fileName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) PureWhite else BgDarkGrey)
                                    .clickable { selectedPresetFile = p.fileName }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = p.displayName,
                                    color = if (isSel) BgDarkGrey else SoftWhite,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = selectedApp
                    if (chosen == null) {
                        Toast.makeText(context, "Select an app first", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    ShareBackupManager.upsertGameLink(
                        context,
                        GameAppLink(chosen.packageName, chosen.appName, selectedPresetFile)
                    )
                    gameLinks = ShareBackupManager.getGameLinks(context)
                    showAddGameDialog = false
                }) {
                    Text("Save Link", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGameDialog = false }) {
                    Text("Cancel", color = MutedGrey)
                }
            }
        )
    }
}
