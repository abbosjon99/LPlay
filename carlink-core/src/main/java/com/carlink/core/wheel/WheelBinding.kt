package com.carlink.core.wheel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App
import com.carlink.core.connection.CarLinkCore
import com.shilapi.xcertplay.CarPlayHostIntegration
import java.io.File

/** What a car button asks of the phone session. */
enum class WheelCommand(val storageKey: String) {
    ANSWER("answer"),
    HANG_UP("hangup"),
    ASSISTANT("assistant"),
    ;

    companion object {
        fun of(storageKey: String): WheelCommand? = entries.firstOrNull { it.storageKey == storageKey }
    }
}

/**
 * What one car button looks like when it arrives: the broadcast's action and its small int extras (the wheel bridge puts
 * the button in an int extra named `action`). Learned from the car, never guessed.
 */
data class WheelSignature(val action: String, val ints: Map<String, Int>) {
    /** True when [intent] is the same button: same action, and every stored int extra has the stored value. */
    fun matches(intent: Intent): Boolean =
        intent.action == action && ints.all { (key, value) -> intent.hasExtra(key) && intent.getIntExtra(key, Int.MIN_VALUE) == value }

    /** `action|key=value|key=value`, keys in order. */
    fun serialize(): String = (listOf(action) + ints.toSortedMap().map { (k, v) -> "$k=$v" }).joinToString("|")

    /** For the settings screen: the action's last words and the values. */
    fun label(): String = action.substringAfterLast('.') + ints.toSortedMap().entries.joinToString("") { " ${it.key}=${it.value}" }

    companion object {
        /** Int extras that look like a button code, not a counter or a size. */
        private const val MAX_CODE = 1_000

        fun of(intent: Intent): WheelSignature? {
            val action = intent.action ?: return null
            val extras = intent.extras
            val ints = if (extras == null) emptyMap() else extras.keySet().sorted().mapNotNull { key ->
                @Suppress("DEPRECATION")
                (extras.get(key) as? Int)?.takeIf { it in -MAX_CODE..MAX_CODE }?.let { key to it }
            }.toMap()
            return WheelSignature(action, ints)
        }

        fun parse(text: String): WheelSignature? {
            val parts = text.split('|')
            val action = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
            val ints = parts.drop(1).mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to (part.substring(i + 1).toIntOrNull() ?: return@mapNotNull null)
            }.toMap()
            return WheelSignature(action, ints)
        }
    }
}

/**
 * The learned car buttons, in a small file of the app's own (not preferences: the engine process reads them too, and
 * preferences are cached per process). One line per command: `answer=<signature>`.
 */
object WheelBindings {
    private const val FILE = "lplay_wheel_bindings.txt"
    private const val ENABLED_FILE = "lplay_wheel_enabled"

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    fun load(context: Context): Map<WheelCommand, WheelSignature> = runCatching {
        file(context).takeIf { it.isFile }?.readLines().orEmpty().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val command = WheelCommand.of(line.substring(0, i)) ?: return@mapNotNull null
            val signature = WheelSignature.parse(line.substring(i + 1)) ?: return@mapNotNull null
            command to signature
        }.toMap()
    }.getOrDefault(emptyMap())

    fun save(context: Context, bindings: Map<WheelCommand, WheelSignature>) {
        runCatching {
            file(context).writeText(bindings.entries.sortedBy { it.key.ordinal }
                .joinToString("\n") { "${it.key.storageKey}=${it.value.serialize()}" })
        }
    }

    /**
     * Whether the learned buttons act at all. Off until the driver turns it on: a button can be learned and recorded first,
     * and nothing reaches the phone session until this is switched on. A marker file, so the engine process sees it too.
     */
    fun isEnabled(context: Context): Boolean = runCatching { enabledFile(context).isFile }.getOrDefault(false)

    fun setEnabled(context: Context, enabled: Boolean) {
        runCatching {
            val marker = enabledFile(context)
            if (enabled) marker.writeText("on") else marker.delete()
        }
    }

    private fun enabledFile(context: Context) = File(context.applicationContext.filesDir, ENABLED_FILE)

    fun set(context: Context, command: WheelCommand, signature: WheelSignature?) {
        val next = load(context).toMutableMap()
        if (signature == null) next.remove(command) else next[command] = signature
        save(context, next)
    }

    /** The commands whose learned button is [intent]; empty when it is none of them. */
    fun commandsFor(bindings: Map<WheelCommand, WheelSignature>, intent: Intent): List<WheelCommand> =
        bindings.filter { it.value.matches(intent) }.keys.toList()
}

/**
 * "Learn" for one car button: listens for the next broadcast the recorder knows ([WheelButtonRecorder.ACTIONS]) and stores
 * it as the signature of [WheelCommand]. Listening only, like the recorder.
 */
object WheelLearner {
    private const val TIMEOUT_MILLIS = 20_000L
    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var app: Context? = null
    private var timeout: Runnable? = null

    @Volatile var learning: WheelCommand? = null
        private set

    /** [done] gets the learned signature, or null when nothing came in time or [cancel] was called. Main thread. */
    fun start(context: Context, command: WheelCommand, done: (WheelSignature?) -> Unit) {
        cancel()
        val application = context.applicationContext
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val signature = WheelSignature.of(intent) ?: return
                WheelBindings.set(application, command, signature)
                finish(done, signature)
            }
        }
        val filter = IntentFilter().also { f -> WheelButtonRecorder.ACTIONS.forEach(f::addAction) }
        ContextCompat.registerReceiver(application, listener, filter, ContextCompat.RECEIVER_EXPORTED)
        app = application
        receiver = listener
        learning = command
        timeout = Runnable { finish(done, null) }.also { handler.postDelayed(it, TIMEOUT_MILLIS) }
    }

    fun cancel() {
        if (learning != null) finish({}, null)
    }

    private fun finish(done: (WheelSignature?) -> Unit, signature: WheelSignature?) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        runCatching { app?.unregisterReceiver(receiver) }
        receiver = null
        learning = null
        done(signature)
    }
}

/**
 * Performs the learned car buttons on the running phone session. Registered in every process of the app (CarPlay runs in
 * the main process, Android Auto in ":aa"); each acts only when its own engine has a session, and an engine session is the
 * only one at a time. Like the recorder it only listens: it never aborts a broadcast, so the car's own apps and LP Music
 * see every button as before.
 */
object WheelBindingReceiver {
    private const val TAG = "LPlay-Wheel"
    private const val DEBOUNCE_MILLIS = 700L
    private val lastPerformed = HashMap<WheelCommand, Long>()
    private var installed = false

    /** What runs a command; the engine in this process in the app, a recorder in the unit tests. */
    @Volatile internal var performer: (Context, WheelCommand) -> Boolean = ::perform

    /** Forgets the last presses, for the unit tests. */
    internal fun resetDebounce() = synchronized(lastPerformed) { lastPerformed.clear() }

    fun install(context: Context) {
        synchronized(this) {
            if (installed) return
            val app = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = onBroadcast(context, intent)
            }
            val filter = IntentFilter().also { f -> WheelButtonRecorder.ACTIONS.forEach(f::addAction) }
            runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
                .onSuccess { installed = true }
                .onFailure { Log.w(TAG, "car buttons could not be listened to", it) }
        }
    }

    internal fun onBroadcast(context: Context, intent: Intent) {
        if (!WheelBindings.isEnabled(context)) return // learned buttons do nothing until the driver turns them on
        val commands = WheelBindings.commandsFor(WheelBindings.load(context), intent)
        if (commands.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        for (command in commands) {
            synchronized(lastPerformed) {
                if (now - (lastPerformed[command] ?: -DEBOUNCE_MILLIS) < DEBOUNCE_MILLIS) return@synchronized
                lastPerformed[command] = now
                val done = performer(context, command)
                Log.i(TAG, "car button ${command.storageKey} (${intent.action}): performed=$done")
            }
        }
    }

    /** Runs [command] on the engine of this process. False when there is no session to run it on. */
    internal fun perform(context: Context, command: WheelCommand): Boolean {
        if (CarLinkCore.isAndroidAutoProcess(context)) {
            val comm = App.provide(context).commManager
            if (!comm.isConnected) return false
            val keyCode = when (command) {
                WheelCommand.ANSWER -> KeyEvent.KEYCODE_CALL
                WheelCommand.HANG_UP -> KeyEvent.KEYCODE_ENDCALL
                WheelCommand.ASSISTANT -> KeyEvent.KEYCODE_VOICE_ASSIST
            }
            comm.sendKey(keyCode, true, null, "lplay-wheel")
            comm.sendKey(keyCode, false, null, "lplay-wheel")
            return true
        }
        return CarPlayHostIntegration.performWheelCommand(when (command) {
            WheelCommand.ANSWER -> CarPlayHostIntegration.WheelCommand.ANSWER
            WheelCommand.HANG_UP -> CarPlayHostIntegration.WheelCommand.HANG_UP
            WheelCommand.ASSISTANT -> CarPlayHostIntegration.WheelCommand.ASSISTANT
        })
    }
}
