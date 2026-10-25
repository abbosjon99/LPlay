package uz.carhelper.lplay.cluster

import android.app.Application
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Looper
import android.view.Surface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import java.util.concurrent.TimeUnit

/** The cluster route's launch, confirm, reclaim and close rules, against a simulated 1920x720 second display. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ClusterRouteTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val route get() = ClusterRoutes.carPlay
    private var clusterId = -1
    private var surfaces = mutableListOf<Surface?>()

    @Before fun setUp() {
        clusterId = ShadowDisplayManager.addDisplay("w1920dp-h720dp")
        route.config = { ClusterConfig(enabled = true, displayId = clusterId) }
        surfaces = mutableListOf()
        route.bind(app) { surfaces += it }
    }

    @After fun tearDown() {
        route.stop(app)
        route.config = { ClusterConfig.OFF }
        while (shadowOf(app).nextStartedActivity != null) Unit
    }

    private fun idle(seconds: Long = 0) {
        if (seconds == 0L) shadowOf(Looper.getMainLooper()).idle()
        else shadowOf(Looper.getMainLooper()).idleFor(seconds, TimeUnit.SECONDS)
    }

    private fun startedToken(): String {
        val started = shadowOf(app).nextStartedActivity
        assertNotNull("a window should have been started", started)
        assertEquals(CarPlayClusterActivity::class.java.name, started.component?.className)
        return started.getStringExtra(ClusterWindowActivity.EXTRA_TOKEN)!!
    }

    private fun window(token: String): ClusterWindowActivity =
        Robolectric.buildActivity(CarPlayClusterActivity::class.java,
            Intent(app, CarPlayClusterActivity::class.java).putExtra(ClusterWindowActivity.EXTRA_TOKEN, token)).create().get()

    @Test fun theSimulatedDisplayLooksLikeTheCluster() {
        val picked = ClusterDisplay.find(app, clusterId)
        assertNotNull(picked)
        assertEquals(clusterId, picked!!.displayId)
        assertEquals(android.graphics.Point(720, 720), ClusterDisplay.targetSize(app, clusterId))
    }

    @Test fun ensureStartsOneWindowWithATokenAndWaitsForItsConfirmation() {
        route.ensure(app)
        startedToken()
        assertTrue(route.routeSettling())
        assertFalse(route.hasConfirmedRoute())
        // A second ask while the first is pending starts nothing.
        route.ensure(app)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun nothingStartsWhenTheDriverTurnedTheMapOff() {
        route.config = { ClusterConfig(enabled = false, displayId = clusterId) }
        route.ensure(app)
        assertNull(shadowOf(app).nextStartedActivity)
        assertFalse(route.routeSettling())
    }

    @Test fun nothingStartsWhenThereIsNoSuchDisplay() {
        route.config = { ClusterConfig(enabled = true, displayId = clusterId + 50) }
        route.ensure(app)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun nothingStartsForAHostThatDoesNotOwnTheRoute() {
        route.ensure(app.applicationContext.createDisplayContext(app.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(clusterId)))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun aWindowIsConfirmedOnlyWithItsTokenOnTheExpectedDisplay() {
        route.ensure(app)
        val token = startedToken()
        val window = window(token)
        assertFalse(route.confirm(window, "not-the-token", clusterId))
        assertFalse(route.confirm(window, token, clusterId + 1))
        assertFalse(route.confirm(window, token, 0))
        assertTrue(route.confirm(window, token, clusterId))
        assertTrue(route.hasConfirmedRoute())
        assertFalse(route.routeSettling())
    }

    @Test fun aWindowWithAStaleTokenClosesAtOnce() {
        val window = Robolectric.buildActivity(CarPlayClusterActivity::class.java,
            Intent(app, CarPlayClusterActivity::class.java).putExtra(ClusterWindowActivity.EXTRA_TOKEN, "stale")).create().get()
        assertTrue(window.isFinishing)
    }

    @Test fun aWindowThatLandsOnTheWrongDisplayIsClosed() {
        route.ensure(app)
        val token = startedToken()
        // Shown (started, resumed, attached), so the window's posted confirmation runs; this test activity sits on the
        // default display, not on the cluster.
        val window = Robolectric.buildActivity(CarPlayClusterActivity::class.java,
            Intent(app, CarPlayClusterActivity::class.java).putExtra(ClusterWindowActivity.EXTRA_TOKEN, token)).setup().get()
        idle()
        assertTrue(window.isFinishing)
        assertFalse(route.hasConfirmedRoute())
    }

    @Test fun theWindowIsBroughtBackAtMostThreeTimesAMinute() {
        route.ensure(app)
        val token = startedToken()
        val window = window(token)
        assertTrue(route.confirm(window, token, clusterId))
        repeat(5) { route.windowStopped(window) }
        idle(2)
        var reclaims = 0
        while (shadowOf(app).nextStartedActivity != null) reclaims++
        assertEquals(3, reclaims)
        // A minute later it may do so again.
        idle(61)
        route.windowStopped(window)
        idle(2)
        assertNotNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun theSurfaceGoesOnlyToTheBoundHostAndOnlyFromItsOwner() {
        route.ensure(app)
        val token = startedToken()
        val window = window(token)
        assertTrue(route.confirm(window, token, clusterId))
        val surface = Surface(SurfaceTexture(0))
        route.attach(window, surface)
        assertEquals(surface, surfaces.last())
        // Another window's late detach must not clear the current surface.
        route.detach(Any(), surface)
        assertEquals(surface, surfaces.last())
        route.detach(window, surface)
        assertNull(surfaces.last())
    }

    @Test fun stoppingClosesTheWindowAndTellsTheHostThereIsNoSurface() {
        route.ensure(app)
        val token = startedToken()
        val window = window(token)
        assertTrue(route.confirm(window, token, clusterId))
        route.attach(window, Surface(SurfaceTexture(0)))
        route.stop(app)
        assertTrue(window.isFinishing)
        assertNull(surfaces.last())
        assertFalse(route.hasConfirmedRoute())
        assertFalse(route.streamActive)
    }

    @Test fun theVideoShowsOnlyWhileTheStreamIsActive() {
        route.ensure(app)
        val token = startedToken()
        val window = window(token)
        assertTrue(route.confirm(window, token, clusterId))
        route.setStreamActive(true)
        assertTrue(route.streamActive)
        route.setStreamActive(false)
        assertFalse(route.streamActive)
    }
}
