package com.motog35.dynamicisland.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.motog35.dynamicisland.model.ExpansionMode
import com.motog35.dynamicisland.model.IslandEvent
import com.motog35.dynamicisland.model.IslandRepository
import com.motog35.dynamicisland.ui.DynamicIslandOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DynamicIslandAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var isTorchEnabled = false
    private var currentBatteryPct = 100

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            isTorchEnabled = enabled
            IslandRepository.updateDiagnostics(
                IslandRepository.networkSpeedKbps.value,
                currentBatteryPct,
                isTorchEnabled
            )
        }
    }

    private val systemBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        currentBatteryPct = (level * 100) / scale
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    IslandRepository.postEvent(
                        IslandEvent.SystemStatus(
                            title = "TurboPower Charging",
                            subtitle = "$currentBatteryPct% • Moto G35 5G",
                            accentHex = 0xFF4ADE80,
                            batteryPct = currentBatteryPct,
                            isCharging = true
                        ),
                        expandAutomatically = false
                    )
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val (label, color) = when (audio.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "Silent Mode" to 0xFFF87171
                        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate Mode" to 0xFFFBBF24
                        else -> "Ring Mode Active" to 0xFF60A5FA
                    }
                    IslandRepository.postEvent(
                        IslandEvent.SystemStatus(
                            title = label,
                            subtitle = "Sound Profile Updated",
                            accentHex = color
                        )
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        setupTorchController()
        registerHardwareReceivers()
        attachOverlayWindow()
        startTelemetryLoop()
    }

    private fun setupTorchController() {
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchCameraId = cameraManager?.cameraIdList?.firstOrNull()
            cameraManager?.registerTorchCallback(torchCallback, null)
            IslandRepository.onToggleTorch = {
                torchCameraId?.let { id ->
                    try {
                        cameraManager?.setTorchMode(id, !isTorchEnabled)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun registerHardwareReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        registerReceiver(systemBroadcastReceiver, filter)
    }

    private fun attachOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 18
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DynamicIslandAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@DynamicIslandAccessibilityService)
            setContent {
                DynamicIslandOverlay()
            }
        }
        composeView = view
        windowManager?.addView(view, params)

        // Toggle focusability dynamically when Expanded so Inline Reply keyboard works on Android 15
        serviceScope.launch {
            IslandRepository.expansionMode.collectLatest { mode ->
                val lp = layoutParams ?: return@collectLatest
                lp.flags = if (mode == ExpansionMode.EXPANDED) {
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                } else {
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                }
                composeView?.let { windowManager?.updateViewLayout(it, lp) }
            }
        }
    }

    private fun startTelemetryLoop() {
        serviceScope.launch(Dispatchers.Default) {
            var lastRxBytes = TrafficStats.getTotalRxBytes()
            while (true) {
                delay(1500)
                val nowRx = TrafficStats.getTotalRxBytes()
                val deltaKbps = (((nowRx - lastRxBytes).coerceAtLeast(0L)) * 1000L / 1500L) / 1024L
                lastRxBytes = nowRx
                IslandRepository.updateDiagnostics(deltaKbps, currentBatteryPct, isTorchEnabled)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        serviceScope.cancel()
        try {
            unregisterReceiver(systemBroadcastReceiver)
            cameraManager?.unregisterTorchCallback(torchCallback)
            composeView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
