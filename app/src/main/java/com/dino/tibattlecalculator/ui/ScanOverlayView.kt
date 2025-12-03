package com.dino.tibattlecalculator.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circlePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
        isAntiAlias = true
        color = 0x80FFFFFF.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val cx = width / 2f
        val cy = height / 2f

        val radius = min(width, height) * 0.45f

        canvas.drawCircle(cx, cy, radius, circlePaint)
    }

    private fun dpToPx(dp: Float): Float {
        val density = resources.displayMetrics.density
        return dp * density
    }
}
