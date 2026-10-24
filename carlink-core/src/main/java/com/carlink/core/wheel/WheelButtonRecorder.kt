package com.carlink.core.wheel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.CarPlayHostIntegration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LPlay, a parked-test tool: writes down what the car's steering-wheel buttons (and the voice and phone buttons) send, so
 * the answer to "what does the speak / answer-call button do?" is read from the car instead of guessed. Its findings
 * are what the car-button binding ([WheelBindings]) learns from.
 *
 * It only listens. It never aborts or answers a broadcast, never changes a result, never starts anything and never
 * touches another app's key handling, so it cannot disturb the LP Music app or the car's own apps: a normal broadcast is
 * delivered to every receiver on its own. It switches itself off after [AUTO_STOP_MILLIS].
 *
 * Android cannot subscribe to "every com.leapmotor.* action", so [ACTIONS] lists the ones the car's own apps listen for
 * (read from the car's receiver registrations) plus the wheel channels other Leapmotor fork authors reported. Key events
 * are recorded too, but only those that reach the projection window when it is in front.
 */
object WheelButtonRecorder {
    val ACTIONS: List<String> = listOf(
        // The wheel bridge: the media keys arrive here as an int extra `action`.
        "com.leapmotor.steerwheelcontrol",
        "com.leapmotor.steerwheelcontrol.media",
        "com.leapmotor.steerwheelcontrol.phone",
        // Cluster and voice control channels seen in other apps.
        "com.leapmotor.action.METER.CTRL",
        "com.leapmotor.action.VOICE.CTRL",
        "com.leapmotor.ICU2MMICtrl",
        "car.meter.music.BROADCAST",
        "car.hmi.music.BROADCAST",
        "car.hmi.vice.music.BROADCAST",
        // The voice assistant's state and where it sends what it heard.
        "com.leapmotor.speech.state",
        "com.leapmotor.speech.tophone",
        "com.leapmotor.speech.tomedia",
        "com.leapmotor.speech.toautonavi",
        "com.leapmotor.speech.fromiqiy",
        "com.leapmotor.speech.todriverecord",
        "com.leapmotor.aispeech.visible.say.trigger",
        "com.leapmotor.systemui.dock.XIAOLING_LONG_CLICK",
        // Media state the car keeps about the player.
        "com.leapmotor.action.MEDIA.INFO",
        "com.leapmotor.transfer.MEDIA.INFO",
        "com.leapmotor.multimedia.general.event",
        "com.leapmotor.media_focus_change",
    )

    const val MAX_LINES = 400
    const val AUTO_STOP_MILLIS = 15 * 60_000L
    private const val MAX_VALUE_CHARS = 120
    private const val MAX_BYTES = 32

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val stopTask = Runnable { stop() }
    private var app: Context? = null
    private var receiver: BroadcastReceiver? = null

    @Volatile var running = false
        private set

    fun start(context: Context) {
        synchronized(lock) {
            if (running) return
            val application = context.applicationContext
            val listener = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    record(describe(intent, timeLabel()))
                }
            }
            val filter = IntentFilter().also { f -> ACTIONS.forEach(f::addAction) }
            ContextCompat.registerReceiver(application, listener, filter, ContextCompat.RECEIVER_EXPORTED)
            app = application
            receiver = listener
            running = true
            // The projection window hands key events to this observer before it passes them on unchanged.
            CarPlayHostIntegration.keyObserver = ::onKey
            record("${timeLabel()} recorder started: ${ACTIONS.size} broadcast actions and key events in front of the CarPlay window")
            handler.removeCallbacks(stopTask)
            handler.postDelayed(stopTask, AUTO_STOP_MILLIS)
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!running) return
            handler.removeCallbacks(stopTask)
            runCatching { app?.unregisterReceiver(receiver) }
            receiver = null
            running = false
            if (CarPlayHostIntegration.keyObserver != null) CarPlayHostIntegration.keyObserver = null
            record("${timeLabel()} recorder stopped")
        }
    }

    fun clear() = synchronized(lock) { lines.clear() }

    /** Everything recorded so far, oldest first; empty when nothing has been recorded. */
    fun snapshot(): String = synchronized(lock) { lines.joinToString("\n") }

    fun lineCount(): Int = synchronized(lock) { lines.size }

    private fun onKey(event: KeyEvent) {
        if (!running) return
        record("${timeLabel()} KEY ${when (event.action) { KeyEvent.ACTION_DOWN -> "DOWN"; KeyEvent.ACTION_UP -> "UP"; else -> "ACTION_${event.action}" }} ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}) " +
            "scan=${event.scanCode} device=${event.deviceId} source=0x${Integer.toHexString(event.source)} repeat=${event.repeatCount}")
    }

    internal fun record(line: String) = synchronized(lock) {
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    /** One line: time, action, and every extra with its type, so an int `action` is told apart from a string one. */
    internal fun describe(intent: Intent, timeLabel: String): String = buildString {
        append(timeLabel).append(" ").append(intent.action ?: "(no action)")
        intent.`package`?.let { append(" pkg=").append(it) }
        intent.dataString?.let { append(" data=").append(clip(it)) }
        val extras = intent.extras
        if (extras == null || extras.isEmpty) {
            append(" (no extras)")
        } else {
            for (key in extras.keySet().sorted()) {
                @Suppress("DEPRECATION")
                val value = extras.get(key)
                append(" ").append(key).append("=").append(render(value)).append("<").append(value?.javaClass?.simpleName ?: "null").append(">")
            }
        }
    }

    private fun render(value: Any?): String = when (value) {
        null -> "null"
        is ByteArray -> value.take(MAX_BYTES).joinToString("") { "%02x".format(it) } + if (value.size > MAX_BYTES) "…(${value.size} bytes)" else ""
        is Array<*> -> clip(value.joinToString(",", "[", "]"))
        else -> clip(value.toString())
    }

    private fun clip(text: String): String = if (text.length <= MAX_VALUE_CHARS) text else text.take(MAX_VALUE_CHARS) + "…"

    private fun timeLabel(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
}
