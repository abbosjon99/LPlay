package uz.carhelper.lplay.cluster

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ClusterConfigTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun nothingStoredMeansOffOnTheKnownClusterWithTheDefaultFade() {
        context.getSharedPreferences("lplay_cluster", 0).edit().clear().commit()
        val config = ClusterPrefs.load(context)
        assertFalse(config.enabled)
        assertEquals(ClusterDisplay.AUTO, config.displayId)
        assertEquals(ClusterEdgeFade.DEFAULT_PERCENT, config.fadePercent)
        assertEquals(ClusterConfig.OFF, config)
    }

    @Test fun theChoicesSurviveARoundTrip() {
        ClusterPrefs.save(context, ClusterConfig(enabled = true, displayId = 3, fadePercent = 35))
        assertEquals(ClusterConfig(enabled = true, displayId = 3, fadePercent = 35), ClusterPrefs.load(context))
        assertTrue(ClusterPrefs.load(context).enabled)
    }
}
