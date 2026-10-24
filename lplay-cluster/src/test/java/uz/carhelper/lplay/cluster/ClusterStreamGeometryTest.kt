package uz.carhelper.lplay.cluster

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterStreamGeometryTest {
    @Test fun theAndroidAutoSquareShowsTheMiddle720ColumnsOfA1280x720Frame() {
        val g = ClusterStreamGeometry.ANDROID_AUTO_SQUARE
        val m = g.cropMatrix(720, 720)
        // The view stretches the 1280 columns to 720 first; scaling by 1280/720 undoes that, and the translation moves
        // column 280 to the left edge.
        assertEquals(1280f / 720f, m[0], 0.0001f)
        assertEquals(1f, m[1], 0.0001f)
        assertEquals(-280f, m[2], 0.0001f)
        assertEquals(0f, m[3], 0.0001f)
        // A buffer column b lands at b - 280 in the window: the visible 0..720 are the frame's 280..1000.
        val x = { b: Float -> b * (720f / 1280f) * m[0] + m[2] }
        assertEquals(0f, x(280f), 0.001f)
        assertEquals(720f, x(1000f), 0.001f)
    }

    @Test fun aSmallerViewKeepsTheSameProportions() {
        val m = ClusterStreamGeometry.ANDROID_AUTO_SQUARE.cropMatrix(360, 360)
        assertEquals(-140f, m[2], 0.0001f)
        assertEquals(1280f / 720f, m[0], 0.0001f)
    }

    @Test fun aFrameOfTheWindowsSizeNeedsNoCrop() {
        val m = ClusterStreamGeometry(720, 720, 0, 0, 720, 720).cropMatrix(720, 720)
        assertEquals(1f, m[0], 0f)
        assertEquals(1f, m[1], 0f)
        assertEquals(0f, m[2], 0f)
        assertEquals(0f, m[3], 0f)
    }
}
