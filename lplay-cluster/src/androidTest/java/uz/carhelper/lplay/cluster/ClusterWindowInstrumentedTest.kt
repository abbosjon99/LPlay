package uz.carhelper.lplay.cluster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Runs on an emulator with a simulated 1920x720 second display (`settings put global overlay_display_devices 1920x720/213`).
 * The route starts the real window on that display, the window gives its Surface, and this test plays the engine's decoder:
 * it draws frames into the Surface for `holdSeconds` (an instrumentation argument), so a screenshot of the display shows the
 * window, its 720x720 size, its place at the top right and the fade on its left edge.
 */
@RunWith(AndroidJUnit4::class)
class ClusterWindowInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun <T> readOnMain(block: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun waitFor(seconds: Long, what: String, check: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < end) {
            if (readOnMain(check)) return
            Thread.sleep(100)
        }
        throw AssertionError("timed out waiting for $what")
    }

    @Test fun theWindowOpensOnTheClusterAndTakesFramesAndClosesAgain() {
        assumeTrue("needs a simulated 1920x720 second display", ClusterDisplay.find(context) != null)
        val hold = InstrumentationRegistry.getArguments().getString("holdSeconds")?.toLongOrNull() ?: 3L
        val route = ClusterRoutes.carPlay
        val surfaces = LinkedBlockingQueue<Surface>()
        route.config = { ClusterConfig(enabled = true, fadePercent = 20) }
        onMain {
            route.bind(context) { surface -> if (surface != null) surfaces.offer(surface) }
            route.ensure(context)
            route.setStreamActive(true)
        }
        waitFor(20, "the window to confirm its display") { route.hasConfirmedRoute() }
        val surface = surfaces.poll(10, TimeUnit.SECONDS)
        assertNotNull("the window should hand over a Surface", surface)

        // Play the decoder: bright magenta with a white frame, so the fade and the window edges show in a screenshot.
        val fill = Paint().apply { color = Color.MAGENTA }
        val frame = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 6f }
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(hold)
        var frames = 0
        while (System.nanoTime() < end) {
            val canvas: Canvas = surface!!.lockCanvas(null)
            try {
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), fill)
                canvas.drawRect(3f, 3f, canvas.width - 3f, canvas.height - 3f, frame)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
            frames++
            Thread.sleep(100)
        }
        assertTrue("frames were drawn", frames > 0)
        assertTrue(readOnMain { route.hasConfirmedRoute() })

        onMain { route.stop(context) }
        waitFor(10, "the window to close") { !route.hasConfirmedRoute() }
        assertFalse(readOnMain { route.routeSettling() })
        assertEquals(null, readOnMain { route.activity.get() })
    }
}
