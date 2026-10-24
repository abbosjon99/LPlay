package uz.carhelper.lplay.cluster

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * The map on the Leapmotor instrument cluster: a transparent, untouchable 720x720 window pinned to the top right of the
 * 1920x720 panel. It holds one [ClusterTextureView] whose Surface goes to the engine's decoder through its
 * [ClusterRoute]. When the stream is idle the video is hidden, so the car's own cluster picture shows through instead
 * of a frozen map or a black square. The video sits in a [ClusterEdgeFadeFrame] that fades the window's left edge into
 * that picture.
 *
 * Started only by this app ([ClusterRoute.ensure]); not exported. There is one subclass per engine, because the
 * Android Auto decoder lives in another process and a Surface belongs to the process that owns its TextureView.
 */
abstract class ClusterWindowActivity : Activity() {
    private var surface: Surface? = null
    private var videoTexture: ClusterTextureView? = null
    private var fadeFrame: ClusterEdgeFadeFrame? = null

    /** The route of the engine this window belongs to, in this window's process. */
    protected abstract val route: ClusterRoute

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        if (!route.acceptsToken(token)) { finish(); return }
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val real = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(real)
        val size = ClusterDisplay.windowSize(real.x, real.y)
        // Absolute right, not END: the slot is on the right whatever the app's language direction is.
        window.setGravity(Gravity.TOP or Gravity.RIGHT)
        window.setLayout(size.x, size.y)
        val geometry = route.geometry
        val texture = ClusterTextureView(this, geometry?.bufferWidth ?: size.x, geometry?.bufferHeight ?: size.y, geometry) { next ->
            surface?.let { route.detach(this, it) }
            surface = next
            if (next != null && route.activity.get() === this) route.attach(this, next)
        }.also { videoTexture = it }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val fade = ClusterEdgeFadeFrame(this, route.config(this).fadePercent).also { fadeFrame = it }
        fade.addView(texture, FrameLayout.LayoutParams(-1, -1))
        root.addView(fade, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        updateStream()
        root.post { confirmDisplay(token, root.display?.displayId ?: -1) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** Applies the saved edge fade to the window that is showing now. Main thread. */
    fun applyEdgeFade() {
        fadeFrame?.leftPercent = route.config(this).fadePercent
    }

    fun updateStream() {
        videoTexture?.alpha = if (route.streamActive) 1f else 0f
    }

    private fun confirmDisplay(token: String?, display: Int) {
        if (isFinishing || isDestroyed || !route.confirm(this, token, display)) {
            Log.w(TAG, "cluster window rejected on display $display; closing")
            finish()
            return
        }
        Log.i(TAG, "cluster window confirmed on display $display")
        surface?.let { route.attach(this, it) }
        updateStream()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) route.windowStopped(this)
    }

    // Losing focus must not detach the stream. The texture owns the rendering lifetime.
    override fun onDestroy() {
        videoTexture?.close()
        videoTexture = null
        route.windowDestroyed(this)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TOKEN = "cluster_launch_token"
        private const val TAG = "LPlay-Cluster"
    }
}

/** The CarPlay window, in the main process. */
class CarPlayClusterActivity : ClusterWindowActivity() {
    override val route: ClusterRoute get() = ClusterRoutes.carPlay
}

/** The Android Auto window, in the ":aa" process next to the Android Auto decoder (see the manifest). */
class AaClusterActivity : ClusterWindowActivity() {
    override val route: ClusterRoute get() = ClusterRoutes.androidAuto
}
