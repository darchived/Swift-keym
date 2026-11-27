package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.customcontrols.ControlLayoutManager
import com.example.customcontrols.LayoutProfileInfo
import com.example.input.InputBridge
import com.example.overlay.OverlayService
import com.example.ui.theme.BgDarkGrey
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SubtleGrey
import com.example.ui.theme.SurfaceElevatedGrey
import com.example.ui.theme.SurfaceGrey
import com.example.ui.theme.SwiftKeyMTheme
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQ_CODE_SHIZUKU = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        InputBridge.init()
        ControlLayoutManager.ensureDefaultLayouts(this)

        setContent {
            SwiftKeyMTheme {
                SwiftKeyMHomeScreen()
            }
        }
    }

    @Composable
    private fun SwiftKeyMHomeScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        val isShizukuAlive by InputBridge.isShizukuAlive.collectAsStateWithLifecycle()
        val hasShizukuPerm by InputBridge.hasShizukuPermission.collectAsStateWithLifecycle()
        val isServiceConnected by InputBridge.isServiceConnected.collectAsStateWithLifecycle()
        val isOverlayRunning by OverlayService.isRunning.collectAsStateWithLifecycle()

        var layouts by remember { mutableStateOf(ControlLayoutManager.listLayouts(context)) }
        var activeLayoutName by remember { mutableStateOf(ControlLayoutManager.getActiveLayoutName(context)) }
        var aimSensitivity by remember { mutableFloatStateOf(ControlLayoutManager.getAimSensitivity(context)) }
        var previewJson by remember { mutableStateOf<String?>(null) }
        var showFolderDialog by remember { mutableStateOf(false) }

        val controlMapDirPath = remember {
            ControlLayoutManager.getControlMapDir(context).absolutePath
        }

        fun refreshAll() {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            InputBridge.refreshShizukuState()
            if (InputBridge.hasShizukuPermission.value) {
                InputBridge.bindService(context)
            }
            layouts = ControlLayoutManager.listLayouts(context)
            activeLayoutName = ControlLayoutManager.getActiveLayoutName(context)
            aimSensitivity = ControlLayoutManager.getAimSensitivity(context)
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshAll()
                }
            }
            val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == REQ_CODE_SHIZUKU) {
                    InputBridge.refreshShizukuState()
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        InputBridge.bindService(context)
                        Toast.makeText(context, "Shizuku authorized", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            Shizuku.addRequestPermissionResultListener(shizukuPermListener)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                Shizuku.removeRequestPermissionResultListener(shizukuPermListener)
            }
        }

        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            startOrToggleOverlay(context, isOverlayRunning)
        }

        val importJsonLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!content.isNullOrBlank()) {
                        val fileName = queryDisplayName(uri) ?: "imported_layout.json"
                        val importedName = ControlLayoutManager.importLayoutJson(context, fileName, content)
                        layouts = ControlLayoutManager.listLayouts(context)
                        activeLayoutName = importedName
                        if (isOverlayRunning) {
                            context.startService(
                                Intent(context, OverlayService::class.java).apply {
                                    action = OverlayService.ACTION_RELOAD_LAYOUT
                                }
                            )
                        }
                        Toast.makeText(context, "Imported $importedName", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    Toast.makeText(context, "Invalid JSON layout: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val exportJsonLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val json = ControlLayoutManager.exportLayoutJson(context, activeLayoutName)
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    Toast.makeText(context, "Exported $activeLayoutName", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    Toast.makeText(context, "Export failed: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BgDarkGrey,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF232323),
                                BgDarkGrey,
                                Color(0xFF171717)
                            )
                        )
                    )
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceElevatedGrey),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Gamepad,
                                    contentDescription = "Swift KeyM Emblem",
                                    tint = PureWhite,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Swift KeyM",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = PureWhite
                                )
                                Text(
                                    text = "Non-Root Shizuku Keymapper",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MutedGrey
                                )
                            }
                        }

                        IconButton(onClick = { refreshAll() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh status",
                                tint = SoftWhite
                            )
                        }
                    }

                    // Status Readiness Strip
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceGrey),
                        border = BorderStroke(1.dp, BorderGrey),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatusPill(
                                label = "Overlay",
                                ready = hasOverlayPermission,
                                statusText = if (hasOverlayPermission) "Granted" else "Needed",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill(
                                label = "Shizuku",
                                ready = isShizukuAlive && hasShizukuPerm,
                                statusText = when {
                                    isServiceConnected -> "Bound"
                                    isShizukuAlive && hasShizukuPerm -> "Ready"
                                    isShizukuAlive -> "Unauth"
                                    else -> "Offline"
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill(
                                label = "Floating",
                                ready = isOverlayRunning,
                                statusText = if (isOverlayRunning) "Active" else "Standby",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Primary Actions: CUSTOM CONTROLS & START FLOATING OVERLAY
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceGrey),
                        border = BorderStroke(1.dp, BorderGrey),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    startActivity(Intent(context, CustomControlsActivity::class.java))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("custom_controls_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceElevatedGrey,
                                    contentColor = PureWhite
                                ),
                                border = BorderStroke(1.dp, BorderGrey)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Custom Controls"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "CUSTOM CONTROLS",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (!Settings.canDrawOverlays(context)) {
                                        Toast.makeText(
                                            context,
                                            "Grant 'Display over other apps' first.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Button
                                    }
                                    if (!isOverlayRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                        != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        startOrToggleOverlay(context, isOverlayRunning)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("toggle_overlay_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOverlayRunning) SurfaceElevatedGrey else PureWhite,
                                    contentColor = if (isOverlayRunning) PureWhite else BgDarkGrey
                                ),
                                border = BorderStroke(1.dp, PureWhite)
                            ) {
                                Icon(
                                    imageVector = if (isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = "Start Floating Overlay"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isOverlayRunning) {
                                        "STOP FLOATING OVERLAY"
                                    } else {
                                        "START FLOATING OVERLAY"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Setup & Permissions Checklist
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
                            Text(
                                text = "Setup & Permissions",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite
                            )

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                    startActivity(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("overlay_permission_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (hasOverlayPermission) PureWhite else BorderGrey),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = SurfaceElevatedGrey,
                                    contentColor = PureWhite
                                )
                            ) {
                                Icon(
                                    imageVector = if (hasOverlayPermission) Icons.Filled.CheckCircle else Icons.Filled.Layers,
                                    contentDescription = "Display Over Other Apps"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (hasOverlayPermission) {
                                        "Display Over Other Apps (Granted)"
                                    } else {
                                        "Grant Display Over Other Apps"
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    InputBridge.refreshShizukuState()
                                    if (!InputBridge.isShizukuAlive.value) {
                                        Toast.makeText(
                                            context,
                                            "Start Shizuku first, then tap Authorize Shizuku.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else if (!InputBridge.hasShizukuPermission.value) {
                                        Shizuku.requestPermission(REQ_CODE_SHIZUKU)
                                    } else {
                                        InputBridge.bindService(context)
                                        Toast.makeText(context, "Shizuku InputService bound", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("shizuku_permission_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (hasShizukuPerm) PureWhite else BorderGrey),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = SurfaceElevatedGrey,
                                    contentColor = PureWhite
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "Authorize Shizuku"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = when {
                                        isServiceConnected -> "Shizuku Service Connected"
                                        hasShizukuPerm -> "Shizuku Authorized (Tap to Bind)"
                                        else -> "Authorize Shizuku"
                                    }
                                )
                            }
                        }
                    }

                    // Swipe-to-Aim Sensitivity Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceGrey),
                        border = BorderStroke(1.dp, BorderGrey),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.TouchApp,
                                        contentDescription = "Aim Sensitivity",
                                        tint = PureWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Swipe-to-Aim Sensitivity",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PureWhite
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.1fx", aimSensitivity),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PureWhite
                                )
                            }

                            Slider(
                                value = aimSensitivity,
                                onValueChange = { next ->
                                    aimSensitivity = next
                                    ControlLayoutManager.setAimSensitivity(context, next)
                                    if (isOverlayRunning) {
                                        context.startService(
                                            Intent(context, OverlayService::class.java).apply {
                                                action = OverlayService.ACTION_UPDATE_SENSITIVITY
                                            }
                                        )
                                    }
                                },
                                valueRange = 0.2f..5.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = PureWhite,
                                    activeTrackColor = PureWhite,
                                    inactiveTrackColor = BorderGrey
                                )
                            )
                            Text(
                                text = "Swiping empty background space injects relative mouse deltas (AXIS_RELATIVE_X/Y) for 360° FPS camera turning alongside WASD & MB1/MB2.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedGrey
                            )
                        }
                    }

                    // My Saved Layouts (controlmap/)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceGrey),
                        border = BorderStroke(1.dp, BorderGrey),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "My Saved Layouts (controlmap/)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureWhite
                                )
                                Text(
                                    text = "${layouts.size} .json",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MutedGrey
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showFolderDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("open_controlmap_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderGrey),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = "Folder",
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("controlmap/", maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("import_json_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Import JSON",
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import", maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { exportJsonLauncher.launch(activeLayoutName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("export_json_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderGrey),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Upload,
                                        contentDescription = "Export JSON",
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export", maxLines = 1)
                                }
                            }

                            layouts.forEach { profile ->
                                LayoutProfileCard(
                                    profile = profile,
                                    isSelected = profile.fileName == activeLayoutName,
                                    onSelect = {
                                        ControlLayoutManager.setActiveLayoutName(context, profile.fileName)
                                        activeLayoutName = profile.fileName
                                        if (isOverlayRunning) {
                                            context.startService(
                                                Intent(context, OverlayService::class.java).apply {
                                                    action = OverlayService.ACTION_RELOAD_LAYOUT
                                                }
                                            )
                                        }
                                    },
                                    onPreview = {
                                        previewJson = ControlLayoutManager.exportLayoutJson(context, profile.fileName)
                                    }
                                )
                            }
                        }
                    }

                    // Credits Footer
                    Text(
                        text = getString(R.string.app_credits),
                        style = MaterialTheme.typography.labelMedium,
                        color = SubtleGrey,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

        if (showFolderDialog) {
            AlertDialog(
                onDismissRequest = { showFolderDialog = false },
                containerColor = SurfaceGrey,
                title = {
                    Text(
                        text = "controlmap/ Directory",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Drop MojoLauncher/Pojav .json controlmaps directly into:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedGrey
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgDarkGrey)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = controlMapDirPath,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelMedium,
                                color = PureWhite
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showFolderDialog = false
                        refreshAll()
                    }) {
                        Text("Refresh List", color = PureWhite)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFolderDialog = false }) {
                        Text("Close", color = MutedGrey)
                    }
                }
            )
        }

        if (previewJson != null) {
            AlertDialog(
                onDismissRequest = { previewJson = null },
                containerColor = SurfaceGrey,
                title = {
                    Text(
                        text = activeLayoutName,
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .verticalScroll(rememberScrollState())
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgDarkGrey)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = previewJson ?: "",
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = SoftWhite
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { previewJson = null }) {
                        Text("Close", color = PureWhite)
                    }
                }
            )
        }
    }

    private fun startOrToggleOverlay(context: android.content.Context, currentlyRunning: Boolean) {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = if (currentlyRunning) OverlayService.ACTION_STOP else OverlayService.ACTION_START
        }
        if (currentlyRunning) {
            context.startService(intent)
        } else {
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}

@Composable
private fun StatusPill(
    label: String,
    ready: Boolean,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val dotColor = if (ready) PureWhite else SubtleGrey
    Surface(
        modifier = modifier,
        color = SurfaceElevatedGrey,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (ready) PureWhite.copy(alpha = 0.55f) else BorderGrey)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedGrey
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (ready) PureWhite else SoftWhite
            )
        }
    }
}

@Composable
private fun LayoutProfileCard(
    profile: LayoutProfileInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect),
        color = if (isSelected) SurfaceElevatedGrey else BgDarkGrey,
        border = BorderStroke(1.dp, if (isSelected) PureWhite else BorderGrey),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${profile.buttonCount} buttons · ${profile.joystickCount} stick · ${profile.drawerCount} drawer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedGrey
                )
            }
            IconButton(onClick = onPreview) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = "Inspect JSON",
                    tint = MutedGrey
                )
            }
        }
    }
}
