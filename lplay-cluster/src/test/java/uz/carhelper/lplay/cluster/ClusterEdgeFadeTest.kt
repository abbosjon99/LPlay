package uz.carhelper.lplay.cluster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ClusterEdgeFadeTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun theFadeEndsAtAShareOfTheWindowWidth() {
        assertEquals(144f, ClusterEdgeFade.stopPixels(720f, 20), 0.001f)
        assertEquals(194.4f, ClusterEdgeFade.stopPixels(720f, 27), 0.001f)
        assertEquals(0f, ClusterEdgeFade.stopPixels(720f, 0), 0f)
        assertEquals(0f, ClusterEdgeFade.stopPixels(0f, 20), 0f)
        // Never more than half the window, whatever is stored.
        assertEquals(360f, ClusterEdgeFade.stopPixels(720f, 90), 0.001f)
    }

    @Test fun onlyThePresetsAreKept() {
        assertTrue(ClusterEdgeFade.presets.contains(0))
        assertEquals(27, ClusterEdgeFade.sanitize(27))
        assertEquals(ClusterEdgeFade.DEFAULT_PERCENT, ClusterEdgeFade.sanitize(13))
        assertEquals(ClusterEdgeFade.DEFAULT_PERCENT, ClusterEdgeFade.sanitize(-5))
    }

    @Test fun theSettingDefaultsToTheFadeAndSurvivesARoundTrip() {
        context.getSharedPreferences("lplay_cluster", 0).edit().clear().commit()
        assertEquals(ClusterEdgeFade.DEFAULT_PERCENT, ClusterPrefs.load(context).fadePercent)
        ClusterPrefs.save(context, ClusterConfig(fadePercent = 0))
        assertEquals(0, ClusterPrefs.load(context).fadePercent)
        ClusterPrefs.save(context, ClusterConfig(fadePercent = 999))
        assertEquals(ClusterEdgeFade.DEFAULT_PERCENT, ClusterPrefs.load(context).fadePercent)
    }

    @Test fun theGradientIsBuiltOncePerSizeNotOncePerFrame() {
        val frame = ClusterEdgeFadeFrame(context, 20)
        frame.addView(View(context))
        frame.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, 720, 720)
        val canvas = Canvas(Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888))
        repeat(5) { frame.draw(canvas) }
        assertEquals(1, frame.shaderRebuilds)
        frame.leftPercent = 0
        frame.draw(canvas)
        assertEquals(1, frame.shaderRebuilds)
    }
}