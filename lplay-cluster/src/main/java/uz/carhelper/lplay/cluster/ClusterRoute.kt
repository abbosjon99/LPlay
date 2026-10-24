package uz.carhelper.lplay.cluster

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Owner of one engine's cluster window on the Leapmotor instrument cluster. Main thread only.
 *
 * The engine's host [bind]s itself, asks for the window with [ensure], and receives the window's [Surface] through the
 * callback it bound. Every launch carries a token and an expected display id; a window that is not on that display, or
 * that carries a stale token, is closed instead of being given the stream. Identity checks keep a stale window from
 * clearing a newer surface.
 *
 * There is no ADB, no car-mode switch and no stock-app hold: the window is a plain Activity started with
 * `ActivityOptions.setLaunchDisplayId` (see [ClusterDisplay]). One route exists per engine process ([ClusterRoutes]).
 */
class ClusterRoute(
    private val tag: String,
    private val windowClass: Class<out ClusterWindowActivity>,
) {
    /** The driver's choices, read when the route needs them. The engine's adapter sets this. */
    @Volatile var config: (Context) -> ClusterConfig = { ClusterConfig.OFF }

    var activity = WeakReference<ClusterWindowActivity>(null)
    var launchPending = false
        private set
    var streamActive = false
        private set
    var surface: Surface? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var launchToken: String? = null
    private var expectedDisplay = -1
    private var launchHost = WeakReference<Context>(null)
    private var hostOwner: Any? = null
    private var surfaceOwner: Any? = null
    private var onSurface: ((Surface?) -> Unit)? = null
    private val reclaims = ArrayDeque<Long>()
    private var absentLogged = false

    private val retryTick = Runnable {
        launchHost.get()?.let(::ensure)
        scheduleRetry()
    }

    fun enabled(context: Context): Boolean = config(context).enabled

    fun acceptsToken(token: String?): Boolean = token != null && token == launchToken && hostOwner != null

    fun hasConfirmedRoute(): Boolean = isAlive(activity.get()) && expectedDisplay > 0

    /** A launch is running, or the system accepted it and the window has not confirmed its display yet. */
    fun routeSettling(): Boolean = !hasConfirmedRoute() && (launchPending || (launchToken != null && expectedDisplay > 0))

    private fun isAlive(window: Activity?): Boolean = window != null && !window.isFinishing && !window.isDestroyed

    /** The host owns the route until it is stopped or another host binds. */
    fun bind(owner: Any, callback: (Surface?) -> Unit) {
        if (hostOwner !== owner && !hasConfirmedRoute()) {
            ++generation
            launchToken = null
            expectedDisplay = -1
            launchPending = false
        }
        hostOwner = owner
        if (owner is Context) launchHost = WeakReference(owner)
        onSurface = callback
        callback(surface)
        scheduleRetry()
    }

    /** Starts the window on the cluster unless one is already there or being started. */
    fun ensure(host: Context) {
        if (!enabled(host)) return
        if (host is Activity && (host.isFinishing || host.isDestroyed)) return
        if (hostOwner !== host) return
        launchHost = WeakReference(host)
        if (isAlive(activity.get()) || launchPending) return
        val display = ClusterDisplay.find(host, config(host).displayId)
        if (display == null) {
            if (!absentLogged) Log.i(tag, "no cluster display among ${ClusterDisplay.describe(host)}")
            absentLogged = true
            return
        }
        absentLogged = false
        if (display.state != Display.STATE_ON) {
            Log.i(tag, "cluster display ${display.displayId} is not on (state ${display.state}); waiting")
            return
        }
        launchPending = true
        val epoch = ++generation
        val token = UUID.randomUUID().toString()
        launchToken = token
        expectedDisplay = display.displayId
        if (!start(host, display.displayId, token, reorder = false)) {
            launchPending = false
            launchToken = null
            expectedDisplay = -1
            return
        }
        main.postDelayed({
            if (generation == epoch && !hasConfirmedRoute()) {
                Log.w(tag, "cluster window on display ${display.displayId} was not confirmed; invalidating the launch")
                launchPending = false
                launchToken = null
                expectedDisplay = -1
            }
        }, CONFIRM_TIMEOUT_MILLIS)
    }

    private fun start(context: Context, displayId: Int, token: String, reorder: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(tag, "launching on a display needs Android 8")
            return false
        }
        return try {
            val intent = Intent(context, windowClass)
                .putExtra(ClusterWindowActivity.EXTRA_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (reorder) Intent.FLAG_ACTIVITY_REORDER_TO_FRONT else 0)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            context.startActivity(intent, options.toBundle())
            Log.i(tag, "cluster window requested on display $displayId reorder=$reorder")
            true
        } catch (error: Exception) {
            Log.w(tag, "cluster window launch failed: ${error.javaClass.simpleName}: ${error.message}")
            false
        }
    }

    /** The window confirmed it sits on [display]. */
    fun confirm(window: ClusterWindowActivity, token: String?, display: Int): Boolean {
        if (!acceptsToken(token) || display <= 0 || display != expectedDisplay || !enabled(window)) return false
        activity.get()?.takeIf { it !== window }?.finish()
        activity = WeakReference(window)
        launchPending = false
        return true
    }

    /**
     * The car's own app can cover the window (the map task of `com.leapmotor.autonavi` shares the display). Bring ours
     * back, at most [RECLAIMS_PER_WINDOW] times a minute, so the two apps never fight.
     */
    fun windowStopped(window: ClusterWindowActivity) {
        if (activity.get() !== window || window.isFinishing) return
        val token = launchToken ?: return
        val displayId = expectedDisplay.takeIf { it > 0 } ?: return
        val now = SystemClock.elapsedRealtime()
        while (reclaims.isNotEmpty() && now - reclaims.first() > RECLAIM_WINDOW_MILLIS) reclaims.removeFirst()
        if (reclaims.size >= RECLAIMS_PER_WINDOW) {
            Log.w(tag, "cluster window covered again; not reclaiming more than $RECLAIMS_PER_WINDOW times a minute")
            return
        }
        reclaims.addLast(now)
        main.postDelayed({
            val host = launchHost.get()
            if (activity.get() === window && !window.isFinishing && hostOwner != null &&
                token == launchToken && displayId == expectedDisplay) {
                start(host?.takeIf { it !is Activity || isAlive(it) } ?: window, displayId, token, reorder = true)
            }
        }, RECLAIM_DELAY_MILLIS)
    }

    /** The window was destroyed; the retry tick opens a new one while the host exists. */
    fun windowDestroyed(window: ClusterWindowActivity) {
        if (activity.get() !== window) return
        activity.clear()
        launchPending = false
        launchToken = null
        expectedDisplay = -1
        scheduleRetry()
    }

    fun attach(owner: Any, next: Surface) {
        surfaceOwner = owner
        surface = next
        onSurface?.invoke(next)
    }

    fun detach(owner: Any, old: Surface) {
        if (surfaceOwner !== owner || surface !== old) return
        surfaceOwner = null
        surface = null
        onSurface?.invoke(null)
    }

    /** Whether the engine's stream is on the window now; the window hides its video while it is not. */
    fun setStreamActive(active: Boolean) {
        streamActive = active
        activity.get()?.updateStream()
    }

    /** Settings changed while the host is paused: close the window now. */
    fun stopForSettings() = closeRoute()

    fun stop(owner: Any) {
        if (hostOwner !== owner) return
        closeRoute()
    }

    private fun scheduleRetry() {
        main.removeCallbacks(retryTick)
        if (hostOwner != null) main.postDelayed(retryTick, RETRY_MILLIS)
    }

    private fun closeRoute() {
        ++generation
        launchToken = null
        expectedDisplay = -1
        launchHost.clear()
        main.removeCallbacks(retryTick)
        onSurface?.invoke(null)
        onSurface = null
        hostOwner = null
        surface = null
        surfaceOwner = null
        launchPending = false
        absentLogged = false
        reclaims.clear()
        setStreamActive(false)
        val old = activity.get()
        activity.clear()
        old?.finish()
    }

    companion object {
        private const val CONFIRM_TIMEOUT_MILLIS = 6_000L
        private const val RETRY_MILLIS = 5_000L
        private const val RECLAIM_DELAY_MILLIS = 600L
        private const val RECLAIM_WINDOW_MILLIS = 60_000L
        private const val RECLAIMS_PER_WINDOW = 3
    }
}

/** The two routes. Each engine process touches only its own: CarPlay runs in the main process, Android Auto in ":aa". */
object ClusterRoutes {
    val carPlay: ClusterRoute by lazy { ClusterRoute("LPlay-ClusterCP", CarPlayClusterActivity::class.java) }
    val androidAuto: ClusterRoute by lazy { ClusterRoute("LPlay-ClusterAA", AaClusterActivity::class.java) }
}
