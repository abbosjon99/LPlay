package com.shilapi.xcertplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Build
import com.shilapi.xcertplay.host.R

/**
 * LPlay: the one notification CarPlay shows on the Leapmotor C01, the iPhone's now-playing track as a media notification.
 *
 * It is also the notification of the foreground service that keeps CarPlay alive, so Android requires it while a
 * session runs. While nothing plays it only says that CarPlay is connected. The media session that LP Music (or the
 * system media UI) controls is the one in [CarPlayMediaKeys]; the buttons here send the same transport commands.
 * Used only when [CarPlayHostIntegration.transportOnlyMedia] is on.
 */
internal object NowPlayingNotification {
    const val NOTIFICATION_ID = 1
    const val ACTION_PREVIOUS = "uz.carhelper.lplay.action.PREVIOUS"
    const val ACTION_PLAY_PAUSE = "uz.carhelper.lplay.action.PLAY_PAUSE"
    const val ACTION_NEXT = "uz.carhelper.lplay.action.NEXT"
    private const val CHANNEL = "diplay_connection"

    /** What the notification shows. Immutable, so it is compared to skip identical updates. */
    data class State(
        val token: MediaSession.Token? = null,
        val title: String? = null,
        val artist: String? = null,
        val artwork: Bitmap? = null,
        val playing: Boolean = false,
    )

    @Volatile private var state = State()
    @Volatile private var running = false

    /** The foreground service is up; updates are only posted while it is. */
    fun serviceStarted() { running = true }

    fun serviceStopped() {
        running = false
        state = State()
    }

    /** Called by the media session when the track, artwork or play state changes. */
    fun update(context: Context, next: State) {
        if (next == state) return
        state = next
        if (!running) return
        val app = context.applicationContext
        runCatching { app.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(app)) }
    }

    fun build(context: Context): Notification {
        val current = state
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.lplay_notification_channel_now_playing),
                    NotificationManager.IMPORTANCE_LOW),
            )
            Notification.Builder(context, CHANNEL)
        } else {
            // Android 7.x has no notification channels; the priority stands in for the channel importance.
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_LOW)
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, CarPlayHostActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hasTrack = !current.title.isNullOrBlank() || !current.artist.isNullOrBlank()
        builder
            .setSmallIcon(R.drawable.ic_diplay_notification)
            .setContentTitle(if (hasTrack) current.title ?: current.artist else CarPlayHostIntegration.appName)
            .setContentText(
                if (hasTrack) current.artist.takeIf { current.title != null }
                else context.getString(R.string.lplay_notification_carplay_connected),
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        current.artwork?.let { builder.setLargeIcon(it) }
        if (hasTrack) {
            builder.addAction(action(context, android.R.drawable.ic_media_previous, R.string.lplay_notification_previous, ACTION_PREVIOUS))
            builder.addAction(
                if (current.playing) action(context, android.R.drawable.ic_media_pause, R.string.lplay_notification_pause, ACTION_PLAY_PAUSE)
                else action(context, android.R.drawable.ic_media_play, R.string.lplay_notification_play, ACTION_PLAY_PAUSE),
            )
            builder.addAction(action(context, android.R.drawable.ic_media_next, R.string.lplay_notification_next, ACTION_NEXT))
        }
        builder.addAction(
            action(context, android.R.drawable.ic_menu_close_clear_cancel, R.string.lplay_notification_disconnect,
                DiPlaySessionService.ACTION_STOP),
        )
        val style = Notification.MediaStyle()
        current.token?.let { style.setMediaSession(it) }
        style.setShowActionsInCompactView(*if (hasTrack) intArrayOf(0, 1, 2) else intArrayOf(0))
        builder.setStyle(style)
        return builder.build()
    }

    private fun action(context: Context, icon: Int, title: Int, action: String): Notification.Action {
        val intent = PendingIntent.getService(
            context, action.hashCode(), Intent(context, DiPlaySessionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, icon), context.getString(title), intent).build()
    }
}
