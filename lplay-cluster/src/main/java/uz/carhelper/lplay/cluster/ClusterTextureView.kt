package uz.carhelper.lplay.cluster

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView

/**
 * The cluster window's video view. Owns only the Surface wrapper; returning true from the destroyed callback lets
 * TextureView release its texture. It is a TextureView, not a SurfaceView, because only a TextureView takes the
 * left-edge fade ([ClusterEdgeFadeFrame]).
 */
class ClusterTextureView(
    context: Context,
    private val bufferWidth: Int,
    private val bufferHeight: Int,
    private val geometry: ClusterStreamGeometry? = null,
    private val onSurface: (Surface?) -> Unit,
) : TextureView(context), java.io.Closeable {
    private var output: Surface? = null

    init {
        isOpaque = false
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                releaseOutput()
                texture.setDefaultBufferSize(bufferWidth, bufferHeight)
                applyCrop(width, height)
                output = Surface(texture).also(onSurface)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = applyCrop(width, height)
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                releaseOutput()
                return true
            }
        }
    }

    /** Shows only the window's part of a bigger frame (Android Auto); a frame of the window's size needs nothing. */
    private fun applyCrop(width: Int, height: Int) {
        val g = geometry ?: return
        if (width <= 0 || height <= 0) return
        val m = g.cropMatrix(width, height)
        setTransform(Matrix().apply { setScale(m[0], m[1]); postTranslate(m[2], m[3]) })
    }

    private fun releaseOutput() {
        if (output == null) return
        onSurface(null)
        output?.release()
        output = null
    }

    override fun close() {
        surfaceTextureListener = null
        releaseOutput()
    }
}
