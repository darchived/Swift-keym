package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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

@Composable
fun PrivacySafetyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceGrey,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = "Privacy and Safety",
                    tint = PureWhite,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Privacy & Safety Transparency",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Swift KeyM ${BuildConfig.VERSION_NAME} • StarDevs",
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
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SafetySectionCard(
                    title = "100% Offline & Zero Telemetry",
                    body = "Swift KeyM contains no internet permission, zero analytics SDKs, zero ads, and never transmits keystrokes, presets, macros, or usage logs off your device."
                )
                SafetySectionCard(
                    title = "Local /dev/uinput & Shizuku Bridge",
                    body = "Touch-to-keyboard/mouse remapping uses Android's local Shizuku UserService binder solely when you start the overlay, and cleanly tears down virtual devices on stop."
                )
                SafetySectionCard(
                    title = "Least-Privilege Permissions",
                    body = "Uses only Display Over Other Apps (for floating controls), Optional Notifications (foreground service status), Optional Screen Capture (100% local in-RAM AI Vision nametag detection—never recorded or sent), and the zero-permission System Photo Picker for custom icons."
                )
                SafetySectionCard(
                    title = "Play Protect & Restricted Settings",
                    body = "This official release is built non-debuggable with the permanent StarDevs release signature. On Android 13+, sideloaded APKs can unlock overlay permission via App Info ⋮ -> Allow restricted settings, or install with Shizuku Play-source attribution."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Understood", color = PureWhite, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SafetySectionCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BgDarkGrey,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = PureWhite,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = SoftWhite
            )
        }
    }
}
