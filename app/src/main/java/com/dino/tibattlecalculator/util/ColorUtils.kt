package com.dino.tibattlecalculator.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.dino.tibattlecalculator.domain.PlayerColor
import kotlin.math.max
import kotlin.math.min

object ColorUtils {

    // New: work directly on a region of the original bitmap
    fun guessPlayerColorFromRegion(
        src: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): PlayerColor {
        if (width <= 0 || height <= 0) return PlayerColor.Unknown

        val target = 96
        val scale = min(
            target.toFloat() / width.toFloat(),
            target.toFloat() / height.toFloat()
        )

        val sampleW = max(1, (width * scale).toInt())
        val sampleH = max(1, (height * scale).toInt())

        // Clamp region into bitmap bounds
        val clampedX = x.coerceIn(0, src.width - 1)
        val clampedY = y.coerceIn(0, src.height - 1)
        val clampedW = min(sampleW, src.width - clampedX)
        val clampedH = min(sampleH, src.height - clampedY)

        val px = IntArray(clampedW * clampedH)
        src.getPixels(px, 0, clampedW, clampedX, clampedY, clampedW, clampedH)

        val hsv = FloatArray(3)
        val hist = FloatArray(360) { 0f }

        var kept = 0
        var darkOrGray = 0

        val minS = 0.20f
        val minV = 0.20f

        for (p in px) {
            Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
            val hDeg = hsv[0].toInt().coerceIn(0, 359)
            val s = hsv[1]
            val v = hsv[2]

            if (s < minS || v < minV) {
                darkOrGray++
                continue
            }
            kept++
            hist[hDeg] += (s * v)
        }

        if (kept < 30) return PlayerColor.Unknown

        fun smoothedPeak(): Int {
            var best = 0
            var bestVal = -1f
            for (i in 0 until 360) {
                val v = hist[i] +
                        hist[(i + 1) % 360] + hist[(i + 2) % 360] +
                        hist[(i + 359) % 360] + hist[(i + 358) % 360]
                if (v > bestVal) {
                    bestVal = v
                    best = i
                }
            }
            return best
        }

        val peak = smoothedPeak()

        return when {
            peak >= 345 || peak < 20 -> PlayerColor.Red
            peak in 20..38            -> PlayerColor.Orange
            peak in 39..65            -> PlayerColor.Yellow
            peak in 190..255          -> PlayerColor.Blue
            peak in 260..345          -> PlayerColor.Purple
            darkOrGray > kept         -> PlayerColor.Black
            else                      -> PlayerColor.Unknown
        }
    }
}


