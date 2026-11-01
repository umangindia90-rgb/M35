package com.motog35.dynamicisland.ui

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motog35.dynamicisland.model.ExpansionMode
import com.motog35.dynamicisland.model.IslandEvent
import com.motog35.dynamicisland.model.IslandRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DynamicIslandOverlay() {
    val config by IslandRepository.cutoutConfig.collectAsState()
    val event by IslandRepository.currentEvent.collectAsState()
    val mode by IslandRepository.expansionMode.collectAsState()
    val netSpeed by IslandRepository.networkSpeedKbps.collectAsState()
    val battery by IslandRepository.batteryLevel.collectAsState()
    val isTorchOn by IslandRepository.isTorchOn.collectAsState()
    val view = LocalView.current

    // Auto-collapse SystemStatus & compact alerts after timeout
    LaunchedEffect(event, mode) {
        if (event is IslandEvent.SystemStatus && mode == ExpansionMode.COMPACT_ACTIVE) {
            delay(config.autoCollapseSeconds * 1000L)
            IslandRepository.dismissCurrent()
        }
    }

    val targetWidth = when (mode) {
        ExpansionMode.COLLAPSED -> config.collapsedWidthDp.dp
        ExpansionMode.COMPACT_ACTIVE -> (config.collapsedWidthDp + 92f).dp
        ExpansionMode.EXPANDED -> config.expandedWidthDp.dp
    }

    val targetHeight = when (mode) {
        ExpansionMode.COLLAPSED -> config.collapsedHeightDp.dp
        ExpansionMode.COMPACT_ACTIVE -> (config.collapsedHeightDp + 4f).dp
        ExpansionMode.EXPANDED -> config.expandedHeightDp.dp
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "islandWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "islandHeight"
    )

    Box(
        modifier = Modifier
            .offset(x = config.offsetXDp.dp, y = config.offsetYDp.dp)
            .size(width = animatedWidth, height = animatedHeight)
            .clip(RoundedCornerShape(config.cornerRadiusDp.dp))
            .background(Color(0xFF09090B))
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(config.cornerRadiusDp.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 36f || dragAmount < -36f) {
                        if (config.enableHaptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        IslandRepository.dismissCurrent()
                    }
                }
            }
            .clickable {
                if (config.enableHaptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                when (mode) {
                    ExpansionMode.COLLAPSED -> IslandRepository.setExpansion(ExpansionMode.EXPANDED)
                    ExpansionMode.COMPACT_ACTIVE -> IslandRepository.setExpansion(ExpansionMode.EXPANDED)
                    ExpansionMode.EXPANDED -> IslandRepository.setExpansion(
                        if (event is IslandEvent.Idle) ExpansionMode.COLLAPSED else ExpansionMode.COMPACT_ACTIVE
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        AnimatedContent(targetState = mode to event, label = "islandContent") { (currentMode, currentEvent) ->
            when (currentMode) {
                ExpansionMode.COLLAPSED -> IdleCollapsedPill(netSpeed, battery, isTorchOn, config.showIdleWidget)
                ExpansionMode.COMPACT_ACTIVE -> CompactActivePill(currentEvent)
                ExpansionMode.EXPANDED -> ExpandedIslandCard(currentEvent, netSpeed, battery, isTorchOn)
            }
        }
    }
}

@Composable
private fun IdleCollapsedPill(netKbps: Long, battery: Int, isTorchOn: Boolean, showWidgets: Boolean) {
    val timeText = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (showWidgets) {
            Text(
                text = if (netKbps > 8) "${netKbps}K/s" else timeText,
                color = Color(0xFFA1A1AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            // Center gap reserved for Moto G35 16MP physical punch-hole camera
            Spacer(modifier = Modifier.width(28.dp))
            Text(
                text = if (isTorchOn) "TORCH" else "$battery%",
                color = if (isTorchOn) Color(0xFFFACC15) else Color(0xFF4ADE80),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactActivePill(event: IslandEvent) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (event) {
            is IslandEvent.MediaPlayback -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.albumArt != null) {
                        Image(
                            bitmap = event.albumArt.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(Icons.Default.MusicNote, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = event.trackTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(64.dp)
                    )
                }
                MiniWaveformVisualizer(isPlaying = event.isPlaying)
            }
            is IslandEvent.NotificationAlert -> {
                Icon(Icons.Default.Notifications, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                Text(
                    text = "${event.title}: ${event.message}",
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            is IslandEvent.CallActive -> {
                Icon(Icons.Default.Call, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                Text(
                    text = event.callerName,
                    color = Color(0xFF4ADE80),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            is IslandEvent.SystemStatus -> {
                Text(
                    text = event.title,
                    color = Color(event.accentHex),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.subtitle,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
            IslandEvent.Idle -> IdleCollapsedPill(0, 100, false, true)
        }
    }
}

@Composable
private fun ExpandedIslandCard(
    event: IslandEvent,
    netSpeed: Long,
    battery: Int,
    isTorchOn: Boolean
) {
    val context = LocalContext.current
    var replyText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 18.dp, bottom = 4.dp, start = 6.dp, end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        when (event) {
            is IslandEvent.MediaPlayback -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (event.albumArt != null) {
                            Image(
                                bitmap = event.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, null, tint = Color(0xFF38BDF8))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(event.trackTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(event.artist, color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    MiniWaveformVisualizer(isPlaying = event.isPlaying)
                }

                LinearProgressIndicator(
                    progress = { event.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0xFF27272A)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { IslandRepository.onMediaControl?.invoke("PREV") }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                    }
                    IconButton(onClick = { IslandRepository.onMediaControl?.invoke("PLAY_PAUSE") }) {
                        Icon(
                            if (event.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(onClick = { IslandRepository.onMediaControl?.invoke("NEXT") }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                    }
                }
            }

            is IslandEvent.NotificationAlert -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${event.appName} • Now",
                            color = Color(0xFF60A5FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = event.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = event.message,
                            color = Color(0xFFD4D4D8),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { IslandRepository.dismissCurrent() }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF71717A))
                    }
                }

                if (event.replyAction != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF18181B))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(Color(0xFF60A5FA)),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (replyText.isEmpty()) {
                                    Text("Reply inline...", color = Color(0xFF71717A), fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        )
                        IconButton(
                            modifier = Modifier.size(28.dp),
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    val fillIn = Intent()
                                    val bundle = Bundle().apply {
                                        putCharSequence(event.replyAction.resultKey, replyText)
                                    }
                                    RemoteInput.addResultsToIntent(arrayOf(event.replyAction.remoteInput), fillIn, bundle)
                                    try {
                                        event.replyAction.actionIntent.send(context, 0, fillIn)
                                    } catch (_: Exception) {
                                    }
                                    replyText = ""
                                    IslandRepository.dismissCurrent()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send Reply", tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            is IslandEvent.CallActive -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(event.callStatus, color = Color(0xFF4ADE80), fontSize = 12.sp)
                        Text(event.callerName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = {
                                try { event.declineIntent?.send() } catch (_: Exception) {}
                                IslandRepository.dismissCurrent()
                            },
                            modifier = Modifier.clip(CircleShape).background(Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White)
                        }
                        if (event.isIncoming && event.acceptIntent != null) {
                            IconButton(
                                onClick = {
                                    try { event.acceptIntent.send() } catch (_: Exception) {}
                                },
                                modifier = Modifier.clip(CircleShape).background(Color(0xFF22C55E))
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White)
                            }
                        }
                    }
                }
            }

            else -> {
                // Quick Hub when user taps idle cutout on Moto G35
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Moto G35 5G • Dynamic Hub", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Battery $battery% • Net ${netSpeed} KB/s", color = Color(0xFFA1A1AA), fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = { IslandRepository.onToggleTorch?.invoke() },
                        modifier = Modifier.clip(CircleShape).background(if (isTorchOn) Color(0xFFFACC15) else Color(0xFF27272A))
                    ) {
                        Icon(
                            Icons.Default.FlashlightOn,
                            contentDescription = "Toggle Flashlight",
                            tint = if (isTorchOn) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniWaveformVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(18.dp)
    ) {
        repeat(4) { idx ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = if (isPlaying) 1f else 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 320 + idx * 90, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$idx"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16 * scale).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF38BDF8))
            )
        }
    }
}
