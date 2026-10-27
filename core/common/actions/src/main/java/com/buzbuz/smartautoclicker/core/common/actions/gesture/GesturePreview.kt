// SPDX-License-Identifier: GPL-3.0-or-later
// Added for Yuklick on 2026-10-21.
package com.buzbuz.smartautoclicker.core.common.actions.gesture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.buzbuz.smartautoclicker.core.common.actions.model.BuiltGesture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Shows the resolved, dispatched coordinates only while the editor is trying actions. */
object GesturePreview {
    @Volatile var enabled = false

    suspend fun <T> display(service: AccessibilityService, gesture: BuiltGesture, execute: suspend () -> T): T {
        if (!enabled) return execute()
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var overlay: View? = null
        try {
            withContext(Dispatchers.Main.immediate) {
                val view = TraceView(service, gesture)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply { gravity = Gravity.TOP or Gravity.START }
                // Visualization failure must never prevent an otherwise valid action.
                try { manager.addView(view, params); overlay = view } catch (_: RuntimeException) { }
            }
            return execute().also { delay(150) }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                overlay?.let { if (it.isAttachedToWindow) manager.removeView(it) }
            }
        }
    }

    private class TraceView(context: Context, gesture: BuiltGesture) : View(context) {
        private data class Stroke(val path: Path, val start: Long, val duration: Long)
        private val strokes = buildList {
            var offset = 0L
            gesture.chains.flatten().forEach { description ->
                var end = 0L
                for (i in 0 until description.strokeCount) {
                    val stroke = description.getStroke(i)
                    add(Stroke(Path(stroke.path), offset + stroke.startTime, stroke.duration))
                    end = maxOf(end, stroke.startTime + stroke.duration)
                }
                offset += end
            }
        }
        private val started = SystemClock.uptimeMillis()
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 125, 140)
            strokeWidth = 4f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val location = IntArray(2); getLocationOnScreen(location)
            canvas.save(); canvas.translate(-location[0].toFloat(), -location[1].toFloat())
            val elapsed = SystemClock.uptimeMillis() - started
            strokes.filter { elapsed >= it.start }.forEach { stroke ->
                val progress = ((elapsed - stroke.start).toFloat() / stroke.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
                val measure = PathMeasure(stroke.path, false)
                val trail = Path(); measure.getSegment(0f, measure.length * progress, trail, true)
                ink.style = Paint.Style.STROKE; canvas.drawPath(trail, ink)
                val xy = FloatArray(2)
                if (!measure.getPosTan(measure.length * progress, xy, null)) {
                    val bounds = RectF(); stroke.path.computeBounds(bounds, true)
                    xy[0] = bounds.centerX(); xy[1] = bounds.centerY()
                }
                val r = 10f * resources.displayMetrics.density
                canvas.drawCircle(xy[0], xy[1], r, ink)
                val pointer = Path().apply {
                    moveTo(xy[0], xy[1]); lineTo(xy[0] + r * 0.3f, xy[1] + r * 2.1f)
                    lineTo(xy[0] + r, xy[1] + r * 1.3f); lineTo(xy[0] + r * 1.9f, xy[1] + r); close()
                }
                ink.style = Paint.Style.FILL; canvas.drawPath(pointer, ink)
            }
            canvas.restore()
            postInvalidateOnAnimation()
        }
    }
}
