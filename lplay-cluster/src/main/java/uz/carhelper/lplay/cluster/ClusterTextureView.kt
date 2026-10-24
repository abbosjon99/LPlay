package uz.carhelper.lplay.cluster

import android.content.Context
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
    private val onSurface: (Surface?) -> Unit,
) : TextureView(context), java.io.Closeable {
    private var output: Surface? = null

    init {
        isOpaque = false
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                releaseOutput()
                texture.setDefaultBufferSize(bufferWidth, bufferHeight)
                output = Surface(texture).also(onSurface)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                releaseOutput()
                return true
            }
        }
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
