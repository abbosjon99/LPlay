package uz.carhelper.lplay.cluster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.widget.FrameLayout

/**
 * The fade on the map's left edge.
 *
 * The map is a square that sits on the car's own cluster picture. Its right and bottom edges are the panel's
 * edges, but the left edge is a hard line against the car's gauges. Fading that edge out lets the car's picture come
 * through, so the square reads as part of the cluster instead of a pasted rectangle.
 *
 * The idea, and the way to do it (take alpha away from the video itself instead of painting a colour over it, so it suits
 * the day theme, the night theme and anything the firmware draws), come from the C11 fork by w012w012
 * (github.com/w012w012/DiPlay-Leapmotor, GPL-3.0). Only the left edge is used here, because on the C01 the window is
 * pinned to the top right and its other three edges are panel edges.
 */
object ClusterEdgeFade {
    /** The choices offered in Settings, in percent of the window width. 0 switches the fade off. */
    val presets: List<Int> = listOf(0, 10, 20, 27, 35)
    const val DEFAULT_PERCENT = 20
    private const val MAX_PERCENT = 50

    /** Where the fade ends, in pixels from the left edge; 0 when there is no fade. */
    fun stopPixels(width: Float, percent: Int): Float =
        if (percent <= 0 || width <= 0f) 0f else width * percent.coerceIn(0, MAX_PERCENT) / 100f

    /** A stored value that is not one of the presets falls back to the default. */
    fun sanitize(percent: Int): Int = if (percent in presets) percent else DEFAULT_PERCENT
}

/**
 * Holds the video and multiplies its alpha by a ramp: transparent at the left edge, fully visible again at the stop.
 *
 * The frame keeps its children in an offscreen layer and applies the ramp with [PorterDuff.Mode.DST_IN]. That works
 * only around a [android.view.TextureView], which is drawn in the window's own layer; a SurfaceView sits under the
 * window and no window drawing can give it alpha. The gradient is built once per size, because this frame redraws with
 * every video frame.
 */
class ClusterEdgeFadeFrame(context: Context, percent: Int) : FrameLayout(context) {
    private val paint = Paint()
    private var shader: LinearGradient? = null
    private var shaderStop = -1f

    /** How often the gradient was built; visible for the unit test. */
    internal var shaderRebuilds = 0
        private set

    var leftPercent: Int = percent
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun dispatchDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val stop = ClusterEdgeFade.stopPixels(width, leftPercent)
        if (stop <= 0f) {
            super.dispatchDraw(canvas)
            return
        }
        if (shader == null || stop != shaderStop) {
            shaderStop = stop
            shaderRebuilds++
            shader = LinearGradient(0f, 0f, stop, 0f, Color.argb(0, 0, 0, 0), Color.argb(255, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        // The layer keeps the ramp inside this frame; siblings drawn later keep their own alpha.
        val checkpoint = canvas.saveLayer(0f, 0f, width, height, null)
        super.dispatchDraw(canvas)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        paint.shader = shader
        canvas.drawRect(0f, 0f, stop, height, paint)
        paint.shader = null
        paint.xfermode = null
        canvas.restoreToCount(checkpoint)
    }
}
