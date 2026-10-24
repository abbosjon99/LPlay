package uz.carhelper.lplay.cluster

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * A coordinate grid on the cluster, for the first parked test: it shows which display is the cluster and
 * which part of it is visible. It takes no input, closes itself, and needs no phone session.
 *
 * The PaopaoYouth projection puts a plain test card on a chosen display for the same purpose.
 */
class ClusterTestCardActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val square = intent.getBooleanExtra(EXTRA_SQUARE, true)
        val seconds = intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS).coerceIn(5, 60)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val real = Point()
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(real)
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val target = ClusterDisplay.windowSize(real.x, real.y)
        val width = if (square) target.x else real.x
        val height = if (square) target.y else real.y
        window.setGravity(Gravity.TOP or Gravity.RIGHT)
        window.setLayout(width, height)
        val displayId = windowManager.defaultDisplay.displayId
        val lines = listOf(
            "LPlay cluster test card",
            "display $displayId  real ${real.x}x${real.y}  dpi ${metrics.densityDpi}",
            "window ${width}x$height  ${if (square) "cluster window, top right" else "whole display"}",
            "closes in $seconds s",
        )
        setContentView(CardView(this, lines))
        handler.postDelayed({ finish() }, seconds * 1000L)
        Log.i(TAG, "test card on display $displayId real=${real.x}x${real.y} dpi=${metrics.densityDpi} window=${width}x$height")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private class CardView(context: Context, private val lines: List<String>) : View(context) {
        private val fill = Paint().apply { color = Color.rgb(8, 36, 104) }
        private val grid = Paint().apply { color = Color.argb(150, 255, 255, 255); strokeWidth = 1f }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; textSize = 30f }
        private val corner = Paint()
        private val corners = listOf(Color.RED, Color.GREEN, Color.CYAN, Color.MAGENTA)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, fill)
            var x = 0
            while (x <= width) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h, grid)
                canvas.drawText(x.toString(), x + 4f, 24f, label)
                x += 100
            }
            var y = 0
            while (y <= height) {
                canvas.drawLine(0f, y.toFloat(), w, y.toFloat(), grid)
                if (y > 0) canvas.drawText(y.toString(), 4f, y - 4f, label)
                y += 100
            }
            val mark = 60f
            val spots = listOf(0f to 0f, w - mark to 0f, 0f to h - mark, w - mark to h - mark)
            spots.forEachIndexed { index, (left, top) ->
                corner.color = corners[index]
                canvas.drawRect(left, top, left + mark, top + mark, corner)
            }
            var baseline = h / 2 - 40f
            lines.forEachIndexed { index, text ->
                canvas.drawText(text, 80f, baseline, if (index == 0) title else label)
                baseline += 34f
            }
        }
    }

    companion object {
        private const val TAG = "LPlay-Cluster"
        private const val EXTRA_SQUARE = "square"
        private const val EXTRA_SECONDS = "seconds"
        const val DEFAULT_SECONDS = 20

        fun sizeOf(display: android.view.Display): Point = Point().also {
            @Suppress("DEPRECATION")
            display.getRealSize(it)
        }

        /** Shows the card on the cluster; false when there is no cluster display to show it on. */
        fun show(context: Context, square: Boolean, seconds: Int = DEFAULT_SECONDS, chosenId: Int = ClusterDisplay.AUTO): Boolean {
            val display = ClusterDisplay.find(context, chosenId) ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return try {
                val intent = Intent(context, ClusterTestCardActivity::class.java)
                    .putExtra(EXTRA_SQUARE, square)
                    .putExtra(EXTRA_SECONDS, seconds)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                context.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId).toBundle())
                true
            } catch (error: Exception) {
                Log.w(TAG, "test card launch failed: ${error.javaClass.simpleName}: ${error.message}")
                false
            }
        }
    }
}
