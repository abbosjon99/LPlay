package uz.carhelper.lplay.cluster

/**
 * How the decoded frame maps onto the cluster window when the frame is bigger than the window.
 *
 * The CarPlay stream is exactly the window's size (no geometry needed). Android Auto only offers fixed codec sizes, so
 * its 720x720 picture comes in a 1280x720 frame and the window shows only the columns [viewLeft, viewLeft + viewWidth).
 *
 * @param bufferWidth width of the decoder's frame, which is also the size of the TextureView's buffer.
 * @param bufferHeight height of that frame.
 * @param viewLeft first column of the frame the window shows.
 * @param viewTop first row of the frame the window shows.
 * @param viewWidth width of the shown part.
 * @param viewHeight height of the shown part.
 */
data class ClusterStreamGeometry(
    val bufferWidth: Int,
    val bufferHeight: Int,
    val viewLeft: Int,
    val viewTop: Int,
    val viewWidth: Int,
    val viewHeight: Int,
) {
    /**
     * Scale and translation for a TextureView of [viewPixelsWidth] x [viewPixelsHeight] whose buffer is the whole frame,
     * so that only the shown part fills the view. (scaleX, scaleY, translateX, translateY) for Matrix.setScale and
     * postTranslate; the view stretches the buffer to its own size first.
     */
    fun cropMatrix(viewPixelsWidth: Int, viewPixelsHeight: Int): FloatArray {
        val scaleX = bufferWidth.toFloat() / viewWidth
        val scaleY = bufferHeight.toFloat() / viewHeight
        val translateX = -viewLeft.toFloat() * viewPixelsWidth / viewWidth
        val translateY = -viewTop.toFloat() * viewPixelsHeight / viewHeight
        return floatArrayOf(scaleX, scaleY, translateX, translateY)
    }

    companion object {
        /** Android Auto's cluster picture: a 1280x720 frame of which the middle 720x720 is the window. */
        val ANDROID_AUTO_SQUARE = ClusterStreamGeometry(1280, 720, 280, 0, 720, 720)
    }
}
