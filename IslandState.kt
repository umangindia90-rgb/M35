package com.motog35.dynamicisland.model

import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default calibration tailored for Motorola Moto G35 5G:
 * - Display: 6.72-inch FHD+ (1080 x 2400 px), 20:9 aspect ratio, 120Hz LTPS LCD
 * - Cutout: Top-center 16MP punch-hole camera
 */
data class CutoutConfig(
    val offsetXDp: Float = 0f,          // 0dp = dead-center on 1080px width
    val offsetYDp: Float = 6f,          // Aligns top edge with Moto G35 punch-hole
    val collapsedWidthDp: Float = 126f, // Covers punch-hole + left/right indicator icons
    val collapsedHeightDp: Float = 36f, // Matches Moto G35 status bar height on Android 15
    val expandedWidthDp: Float = 364f,  // Full interactive card width
    val expandedHeightDp: Float = 188f, // Expanded card height
    val cornerRadiusDp: Float = 28f,
    val showIdleWidget: Boolean = true,
    val enableHaptics: Boolean = true,
    val autoCollapseSeconds: Int = 5
)

sealed class IslandEvent {
    object Idle : IslandEvent()

    data class NotificationAlert(
        val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val appIcon: Bitmap? = null,
        val contentIntent: PendingIntent? = null,
        val replyAction: ReplyCapableAction? = null,
        val actionButtons: List<IslandActionButton> = emptyList(),
        val isNavigation: Boolean = false
    ) : IslandEvent()

    data class MediaPlayback(
        val packageName: String,
        val trackTitle: String,
        val artist: String,
        val isPlaying: Boolean,
        val progress: Float = 0f, // 0f..1f
        val durationMs: Long = 0L,
        val positionMs: Long = 0L,
        val albumArt: Bitmap? = null
    ) : IslandEvent()

    data class CallActive(
        val callerName: String,
        val callStatus: String, // "Incoming Call" or "Ongoing Call"
        val isIncoming: Boolean,
        val startTimeMs: Long = System.currentTimeMillis(),
        val acceptIntent: PendingIntent? = null,
        val declineIntent: PendingIntent? = null,
        val contentIntent: PendingIntent? = null
    ) : IslandEvent()

    data class SystemStatus(
        val title: String,
        val subtitle: String,
        val accentHex: Long = 0xFF4ADE80,
        val batteryPct: Int? = null,
        val isCharging: Boolean = false
    ) : IslandEvent()
}

data class ReplyCapableAction(
    val actionIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val resultKey: String
)

data class IslandActionButton(
    val label: String,
    val pendingIntent: PendingIntent?
)

enum class ExpansionMode {
    COLLAPSED,
    COMPACT_ACTIVE,
    EXPANDED
}

object IslandRepository {
    private val _cutoutConfig = MutableStateFlow(CutoutConfig())
    val cutoutConfig: StateFlow<CutoutConfig> = _cutoutConfig.asStateFlow()

    private val _currentEvent = MutableStateFlow<IslandEvent>(IslandEvent.Idle)
    val currentEvent: StateFlow<IslandEvent> = _currentEvent.asStateFlow()

    private val _mediaState = MutableStateFlow<IslandEvent.MediaPlayback?>(null)
    val mediaState: StateFlow<IslandEvent.MediaPlayback?> = _mediaState.asStateFlow()

    private val _expansionMode = MutableStateFlow(ExpansionMode.COLLAPSED)
    val expansionMode: StateFlow<ExpansionMode> = _expansionMode.asStateFlow()

    private val _networkSpeedKbps = MutableStateFlow(0L)
    val networkSpeedKbps: StateFlow<Long> = _networkSpeedKbps.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    var onMediaControl: ((String) -> Unit)? = null
    var onToggleTorch: (() -> Unit)? = null

    fun updateConfig(transform: (CutoutConfig) -> CutoutConfig) {
        _cutoutConfig.value = transform(_cutoutConfig.value)
    }

    fun postEvent(event: IslandEvent, expandAutomatically: Boolean = false) {
        _currentEvent.value = event
        _expansionMode.value = when {
            event is IslandEvent.Idle && _mediaState.value == null -> ExpansionMode.COLLAPSED
            expandAutomatically -> ExpansionMode.EXPANDED
            else -> ExpansionMode.COMPACT_ACTIVE
        }
    }

    fun updateMedia(media: IslandEvent.MediaPlayback?) {
        _mediaState.value = media
        if (_currentEvent.value is IslandEvent.Idle || _currentEvent.value is IslandEvent.MediaPlayback) {
            if (media != null && media.isPlaying) {
                _currentEvent.value = media
                if (_expansionMode.value == ExpansionMode.COLLAPSED) {
                    _expansionMode.value = ExpansionMode.COMPACT_ACTIVE
                }
            } else if (_currentEvent.value is IslandEvent.MediaPlayback) {
                _currentEvent.value = IslandEvent.Idle
                _expansionMode.value = ExpansionMode.COLLAPSED
            }
        }
    }

    fun setExpansion(mode: ExpansionMode) {
        _expansionMode.value = mode
    }

    fun dismissCurrent() {
        val activeMedia = _mediaState.value
        if (activeMedia != null && activeMedia.isPlaying && _currentEvent.value !is IslandEvent.MediaPlayback) {
            _currentEvent.value = activeMedia
            _expansionMode.value = ExpansionMode.COMPACT_ACTIVE
        } else {
            _currentEvent.value = IslandEvent.Idle
            _expansionMode.value = ExpansionMode.COLLAPSED
        }
    }

    fun updateDiagnostics(netKbps: Long, battery: Int, torch: Boolean) {
        _networkSpeedKbps.value = netKbps
        _batteryLevel.value = battery
        _isTorchOn.value = torch
    }
}
