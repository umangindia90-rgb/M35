package com.motog35.dynamicisland.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.motog35.dynamicisland.model.IslandActionButton
import com.motog35.dynamicisland.model.IslandEvent
import com.motog35.dynamicisland.model.IslandRepository
import com.motog35.dynamicisland.model.ReplyCapableAction

class IslandNotificationListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            syncMediaController()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            syncMediaController()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        setupMediaSessions()
        IslandRepository.onMediaControl = { cmd ->
            activeController?.transportControls?.let { controls ->
                when (cmd) {
                    "PLAY_PAUSE" -> {
                        val playing = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
                        if (playing) controls.pause() else controls.play()
                    }
                    "NEXT" -> controls.skipToNext()
                    "PREV" -> controls.skipToPrevious()
                }
            }
        }
    }

    private fun setupMediaSessions() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, IslandNotificationListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener({ controllers ->
                bindControllers(controllers)
            }, component)
            bindControllers(mediaSessionManager?.getActiveSessions(component))
        } catch (_: SecurityException) {
        }
    }

    private fun bindControllers(controllers: List<MediaController>?) {
        activeController?.unregisterCallback(mediaCallback)
        activeController = controllers?.firstOrNull()
        activeController?.registerCallback(mediaCallback)
        syncMediaController()
    }

    private fun syncMediaController() {
        val controller = activeController ?: run {
            IslandRepository.updateMedia(null)
            return
        }
        val metadata = controller.metadata
        val state = controller.playbackState
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Playing Media"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: controller.packageName
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val pos = state?.position ?: 0L
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val progress = if (duration > 0) (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0.35f
        IslandRepository.updateMedia(
            IslandEvent.MediaPlayback(
                packageName = controller.packageName,
                trackTitle = title,
                artist = artist,
                isPlaying = isPlaying,
                progress = progress,
                durationMs = duration,
                positionMs = pos,
                albumArt = art
            )
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName == packageName) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        if (title.isEmpty() && text.isEmpty()) return

        // Handle Incoming or Ongoing Calls
        if (notification.category == Notification.CATEGORY_CALL) {
            val actions = notification.actions.orEmpty()
            val accept = actions.firstOrNull {
                it.title.toString().contains("Answer", true) || it.title.toString().contains("Accept", true)
            }?.actionIntent
            val decline = actions.firstOrNull {
                it.title.toString().contains("Decline", true) || it.title.toString().contains("Hang", true) ||
                    it.title.toString().contains("Reject", true)
            }?.actionIntent

            IslandRepository.postEvent(
                IslandEvent.CallActive(
                    callerName = title.ifEmpty { "Unknown Caller" },
                    callStatus = text.ifEmpty { "Incoming Call" },
                    isIncoming = accept != null,
                    acceptIntent = accept,
                    declineIntent = decline,
                    contentIntent = notification.contentIntent
                ),
                expandAutomatically = accept != null
            )
            return
        }

        // Ignore silent ongoing background pings unless navigation
        val isNavigation = notification.category == Notification.CATEGORY_NAVIGATION
        if (sbn.isOngoing && !isNavigation) return

        // Extract Inline Reply (RemoteInput) & Action Buttons
        var replyCapable: ReplyCapableAction? = null
        val buttons = mutableListOf<IslandActionButton>()

        notification.actions?.forEach { action ->
            val remoteInput = action.remoteInputs?.firstOrNull()
            if (remoteInput != null && action.actionIntent != null && replyCapable == null) {
                replyCapable = ReplyCapableAction(
                    actionIntent = action.actionIntent,
                    remoteInput = remoteInput,
                    resultKey = remoteInput.resultKey
                )
            } else if (action.title != null) {
                buttons.add(IslandActionButton(action.title.toString(), action.actionIntent))
            }
        }

        val appLabel = try {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val iconBitmap = try {
            drawableToBitmap(packageManager.getApplicationIcon(sbn.packageName))
        } catch (_: Exception) {
            null
        }

        IslandRepository.postEvent(
            IslandEvent.NotificationAlert(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appLabel,
                title = title,
                message = text,
                appIcon = iconBitmap,
                contentIntent = notification.contentIntent,
                replyAction = replyCapable,
                actionButtons = buttons.take(3),
                isNavigation = isNavigation
            ),
            expandAutomatically = false
        )
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(48),
            drawable.intrinsicHeight.coerceAtLeast(48),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
