package uz.carhelper.lplay.cluster

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * The instrument cluster of the 2024-2026 Leapmotor C01 (Snapdragon SA8155, Android 12).
 *
 * The cluster is an ordinary physical display, id 2 on the user's car: 1920x720, trusted, the same flags as the
 * passenger display 3 (which is also 1920x720). Any app can start an Activity on it with
 * `ActivityOptions.setLaunchDisplayId`; no ADB and no car-mode switch are involved. The window size is the rule the
 * PaopaoYouth projection uses (re-derived from its decompile, see [windowSize]): pinned to the top right, as tall as
 * the display, and as wide as the display's short side, so 720 x 720 on this 1920 x 720 panel. It is still to be
 * confirmed with the test card on the car.
 */
object ClusterDisplay {
    /** The setting value that means "pick the cluster by its known identity". */
    const val AUTO = -1

    /** Logical id of the cluster on the user's car. Logical ids can change between boots, so [KNOWN_UNIQUE_ID] wins. */
    const val DEFAULT_DISPLAY_ID = 2

    /** The physical address the user's `dumpsys display` recorded for the cluster. */
    const val KNOWN_UNIQUE_ID = "local:131"

    const val PANEL_WIDTH = 1920
    const val PANEL_HEIGHT = 720

    /** The shorter 1920 x 480 panel PaopaoYouth also knows. Not seen on the user's car; its window is 640 wide. */
    const val SHORT_PANEL_HEIGHT = 480
    private const val SHORT_PANEL_WINDOW_WIDTH = 640
    private const val FALLBACK_SIDE = 720

    private val UNIQUE_ID = Regex("""uniqueId "([^"]*)"""")

    data class Candidate(
        val id: Int,
        val uniqueId: String?,
        val width: Int,
        val height: Int,
        val valid: Boolean,
        val on: Boolean,
    )

    /** Whether a display of this real size is one of the two cluster panels. */
    fun isPanel(width: Int, height: Int): Boolean =
        width == PANEL_WIDTH && (height == PANEL_HEIGHT || height == SHORT_PANEL_HEIGHT)

    /**
     * Picks the cluster. Exact geometry only: the passenger display is the same size, so a 1920x720 panel alone does
     * not identify the cluster, and the default display is never a candidate.
     */
    fun select(candidates: List<Candidate>, chosenId: Int = AUTO): Candidate? {
        val usable = candidates.filter {
            it.id != Display.DEFAULT_DISPLAY && it.valid && isPanel(it.width, it.height)
        }
        if (chosenId != AUTO) return usable.firstOrNull { it.id == chosenId }
        return usable.firstOrNull { it.uniqueId == KNOWN_UNIQUE_ID }
            ?: usable.firstOrNull { it.id == DEFAULT_DISPLAY_ID }
    }

    /**
     * The window (and stream) size for a display of this real size: PaopaoYouth's rule. Height is the display's height
     * (720 when unknown). Width is the display's short side, except that a 1920 x 480 panel gets 640. So 1920 x 720
     * gives a 720 x 720 square.
     */
    fun windowSize(realWidth: Int, realHeight: Int): Point {
        val height = if (realHeight > 0) realHeight else FALLBACK_SIDE
        val width = if (realWidth >= PANEL_WIDTH && height == SHORT_PANEL_HEIGHT) {
            minOf(realWidth, SHORT_PANEL_WINDOW_WIDTH)
        } else {
            minOf(realWidth, height).takeIf { it > 0 } ?: FALLBACK_SIDE
        }
        return Point(width, height)
    }

    /** Whether a stream of this size is one the cluster window asked for (see [windowSize]). */
    fun isWindowStream(size: Pair<Int, Int>?): Boolean =
        size != null && (size == windowSize(PANEL_WIDTH, PANEL_HEIGHT).let { it.x to it.y } ||
            size == windowSize(PANEL_WIDTH, SHORT_PANEL_HEIGHT).let { it.x to it.y })

    fun uniqueIdOf(display: Display): String? = UNIQUE_ID.find(display.toString())?.groupValues?.get(1)

    private fun candidateOf(display: Display): Candidate {
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        return Candidate(display.displayId, uniqueIdOf(display), size.x, size.y, display.isValid,
            display.state == Display.STATE_ON)
    }

    /** Every display this device has, as [Candidate]s; the order is the system's. */
    fun candidates(context: Context): List<Candidate> =
        context.getSystemService(DisplayManager::class.java)?.displays?.map(::candidateOf).orEmpty()

    /** The chosen cluster display, or null when it is not present. It may be asleep: see [Candidate.on]. */
    fun find(context: Context, chosenId: Int = AUTO): Display? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        val picked = select(manager.displays.map(::candidateOf), chosenId) ?: return null
        return manager.getDisplay(picked.id)
    }

    /** The window size the cluster route will use for the chosen display, or null when there is none. */
    fun targetSize(context: Context, chosenId: Int = AUTO): Point? = find(context, chosenId)?.let { display ->
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        windowSize(size.x, size.y)
    }

    /** One line per display, for the diagnostic report and the settings page. */
    fun describe(context: Context): String = candidates(context).joinToString("; ") {
        "${it.id}:${it.uniqueId ?: "?"} ${it.width}x${it.height} valid=${it.valid} on=${it.on}"
    }
}
