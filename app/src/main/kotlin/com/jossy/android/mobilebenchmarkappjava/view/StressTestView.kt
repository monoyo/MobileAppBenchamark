package com.jossy.android.mobilebenchmarkappjava.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom View optimized for batch rendering of simple objects.
 * Used for Stress Test to measure rendering performance.
 */
class StressTestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val objects = mutableListOf<RectF>()
    private val colors = mutableListOf<Int>()
    private val paint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = false // Disable AA for raw throughput
    }

    fun setObjects(newObjects: List<RectF>, newColors: List<Int>) {
        objects.clear()
        objects.addAll(newObjects)
        colors.clear()
        colors.addAll(newColors)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw all objects as fast as possible
        for (i in objects.indices) {
            paint.color = colors[i]
            canvas.drawRect(objects[i], paint)
        }
    }

    fun getObjectCount(): Int = objects.size
}
