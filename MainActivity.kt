package com.motog35.dynamicisland

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motog35.dynamicisland.model.IslandEvent
import com.motog35.dynamicisland.model.IslandRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SettingsDashboard(
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenNotificationAccess = {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    },
                    onOpenOverlayPermission = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    onDisableBatteryOpt = {
                        val pm = getSystemService(POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsDashboard(
    onOpenAccessibility: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onDisableBatteryOpt: () -> Unit
) {
    val config by IslandRepository.cutoutConfig.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Moto G35 5G Dynamic Island",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Android 15 • 6.72" FHD+ (1080×2400) 120Hz Center Cutout Engine",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )

        // Step 1: Android 15 Permissions Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Android 15 Permissions Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Note: On Android 15, go to Settings > Apps > Moto G35 Dynamic Island > ⋮ (top right) > Allow restricted settings before enabling Accessibility.",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp
                )
                Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable Accessibility Cutout Overlay")
                }
                Button(onClick = onOpenNotificationAccess, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable Notification & Media Listener")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onOpenOverlayPermission, modifier = Modifier.weight(1f)) {
                        Text("Overlay Perm")
                    }
                    Button(onClick = onDisableBatteryOpt, modifier = Modifier.weight(1f)) {
                        Text("Unrestricted Battery")
                    }
                }
            }
        }

        // Step 2: Moto G35 Cutout Calibration Sliders
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Moto G35 Punch-Hole Calibration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Text("Horizontal Offset X: ${config.offsetXDp.toInt()} dp", color = Color(0xFFD4D4D8), fontSize = 13.sp)
                Slider(
                    value = config.offsetXDp,
                    onValueChange = { v -> IslandRepository.updateConfig { it.copy(offsetXDp = v) } },
                    valueRange = -80f..80f
                )

                Text("Vertical Offset Y: ${config.offsetYDp.toInt()} dp", color = Color(0xFFD4D4D8), fontSize = 13.sp)
                Slider(
                    value = config.offsetYDp,
                    onValueChange = { v -> IslandRepository.updateConfig { it.copy(offsetYDp = v) } },
                    valueRange = -10f..45f
                )

                Text("Collapsed Pill Width: ${config.collapsedWidthDp.toInt()} dp", color = Color(0xFFD4D4D8), fontSize = 13.sp)
                Slider(
                    value = config.collapsedWidthDp,
                    onValueChange = { v -> IslandRepository.updateConfig { it.copy(collapsedWidthDp = v) } },
                    valueRange = 90f..220f
                )

                Text("Collapsed Pill Height: ${config.collapsedHeightDp.toInt()} dp", color = Color(0xFFD4D4D8), fontSize = 13.sp)
                Slider(
                    value = config.collapsedHeightDp,
                    onValueChange = { v -> IslandRepository.updateConfig { it.copy(collapsedHeightDp = v) } },
                    valueRange = 26f..54f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Idle Clock / Net Speed / Battery", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = config.showIdleWidget,
                        onCheckedChange = { v -> IslandRepository.updateConfig { it.copy(showIdleWidget = v) } }
                    )
                }
            }
        }

        // Step 3: Live Test Triggers
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Test Island Events Live", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            IslandRepository.postEvent(
                                IslandEvent.NotificationAlert(
                                    key = "test_msg",
                                    packageName = "com.whatsapp",
                                    appName = "WhatsApp",
                                    title = "Aarav Sharma",
                                    message = "Hey! Are we still meeting at 7 PM tonight?"
                                ),
                                expandAutomatically = true
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test Message")
                    }
                    Button(
                        onClick = {
                            IslandRepository.updateMedia(
                                IslandEvent.MediaPlayback(
                                    packageName = "com.spotify.music",
                                    trackTitle = "Midnight City (120Hz Mix)",
                                    artist = "M83 • Moto G35 Dolby Atmos",
                                    isPlaying = true,
                                    progress = 0.45f
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test Music")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            IslandRepository.postEvent(
                                IslandEvent.SystemStatus(
                                    title = "18W TurboPower Connected",
                                    subtitle = "84% • Fast Charging",
                                    accentHex = 0xFF4ADE80,
                                    batteryPct = 84,
                                    isCharging = true
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test Charging")
                    }
                    Button(
                        onClick = { IslandRepository.dismissCurrent() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset to Idle")
                    }
                }
            }
        }
    }
}
