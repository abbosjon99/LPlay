package uz.carhelper.lplay.cluster

import android.graphics.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ClusterDisplayTest {
    private fun panel(id: Int, uniqueId: String? = "local:$id", width: Int = 1920, height: Int = 720,
        valid: Boolean = true, on: Boolean = true) =
        ClusterDisplay.Candidate(id, uniqueId, width, height, valid, on)

    // Display ids as recorded on the C01: 0 main, 2 cluster, 3 passenger (also 1920x720), 4 HDMI.
    private val car = listOf(
        panel(0, "local:0", 2560, 1440),
        panel(2, "local:131"),
        panel(3, "local:132"),
        panel(4, "local:5", 1920, 1080),
    )

    @Test fun theClusterIsFoundByItsRecordedIdentityBeforeItsLogicalId() {
        assertEquals(2, ClusterDisplay.select(car)?.id)
        // Logical ids can move between boots; the physical address still wins.
        val moved = listOf(panel(0, "local:0", 2560, 1440), panel(2, "local:132"), panel(5, "local:131"))
        assertEquals(5, ClusterDisplay.select(moved)?.id)
    }

    @Test fun withoutTheRecordedIdentityDisplayTwoIsUsed() {
        val noIdentity = listOf(panel(0, null, 2560, 1440), panel(2, null), panel(3, null))
        assertEquals(2, ClusterDisplay.select(noIdentity)?.id)
    }

    @Test fun thePassengerDisplayIsNeverGuessed() {
        // Same size as the cluster, but neither the known identity nor the known id.
        assertNull(ClusterDisplay.select(listOf(panel(0, "local:0", 2560, 1440), panel(3, "local:132"))))
    }

    @Test fun onlyAnExact1920x720ValidNonDefaultDisplayQualifies() {
        assertNull(ClusterDisplay.select(listOf(panel(2, "local:131", 1280, 480))))
        assertNull(ClusterDisplay.select(listOf(panel(2, "local:131", valid = false))))
        assertNull(ClusterDisplay.select(listOf(panel(0, "local:131"))))
        assertNull(ClusterDisplay.select(emptyList()))
    }

    @Test fun anAsleepClusterIsStillSelectedSoTheWindowCanWaitForIt() {
        assertEquals(2, ClusterDisplay.select(listOf(panel(2, "local:131", on = false)))?.id)
    }

    @Test fun aChosenDisplayOverridesTheAutomaticChoiceButStaysSubjectToTheGeometryRule() {
        assertEquals(3, ClusterDisplay.select(car, chosenId = 3)?.id)
        assertNull(ClusterDisplay.select(car, chosenId = 4))
        assertNull(ClusterDisplay.select(car, chosenId = 9))
    }
    @Test fun theWindowFollowsPaopaoYouthsRule() {
        // 1920x720 (this car): as tall as the display, as wide as its short side.
        assertEquals(Point(720, 720), ClusterDisplay.windowSize(1920, 720))
        // The shorter panel gets 640 wide, not a square.
        assertEquals(Point(640, 480), ClusterDisplay.windowSize(1920, 480))
        // Unknown height falls back to 720; a narrow display is not exceeded.
        assertEquals(Point(720, 720), ClusterDisplay.windowSize(1920, 0))
        assertEquals(Point(500, 720), ClusterDisplay.windowSize(500, 720))
        assertEquals(Point(720, 720), ClusterDisplay.windowSize(0, 0))
    }

    @Test fun bothClusterPanelsAreKnown() {
        assertTrue(ClusterDisplay.isPanel(1920, 720))
        assertTrue(ClusterDisplay.isPanel(1920, 480))
        assertTrue(!ClusterDisplay.isPanel(1280, 480))
        assertEquals(1, ClusterDisplay.select(listOf(panel(1, "local:131", 1920, 480)))?.id)
    }

    @Test fun onlyTheWindowSizedStreamsAreAdopted() {
        assertTrue(ClusterDisplay.isWindowStream(720 to 720))
        assertTrue(ClusterDisplay.isWindowStream(640 to 480))
        assertTrue(!ClusterDisplay.isWindowStream(1920 to 720))
        assertTrue(!ClusterDisplay.isWindowStream(null))
    }

}
