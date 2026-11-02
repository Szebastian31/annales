package com.annales.journal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The mockup's five photo filters, built from the same CSS steps (contrast, saturate, sepia, brightness, hue-rotate, grayscale). */
object Filters {
    val all = listOf("natural" to "Natural", "print" to "Print", "faded" to "Faded", "dusk" to "Dusk", "mono" to "Mono")

    /** A 4 × 5 colour matrix, ready for Android's ColorMatrix or Compose's. */
    fun matrix(key: String): FloatArray {
        val m = ColorMatrix()
        fun then(x: ColorMatrix) = m.postConcat(x)
        when (key) {
            "print" -> { then(contrast(1.1f)); then(saturate(.88f)); then(sepia(.14f)) }
            "faded" -> { then(contrast(.9f)); then(saturate(.72f)); then(brightness(1.06f)); then(sepia(.12f)) }
            "dusk" -> { then(saturate(1.2f)); then(contrast(1.05f)); then(hue(-8f)) }
            "mono" -> { then(saturate(0f)); then(contrast(1.15f)) }
        }
        return m.array
    }

    fun apply(src: Bitmap, key: String): Bitmap {
        if (key == "natural") return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix(key)) })
        return out
    }

    private fun saturate(s: Float) = ColorMatrix().apply { setSaturation(s) }
    private fun contrast(c: Float): ColorMatrix {
        val t = (1 - c) * 127.5f // CSS contrast pivots round mid-grey
        return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }
    private fun brightness(b: Float) = ColorMatrix().apply { setScale(b, b, b, 1f) }
    private fun sepia(a: Float): ColorMatrix {
        val i = 1 - a
        return ColorMatrix(floatArrayOf(
            .393f + .607f * i, .769f - .769f * i, .189f - .189f * i, 0f, 0f,
            .349f - .349f * i, .686f + .314f * i, .168f - .168f * i, 0f, 0f,
            .272f - .272f * i, .534f - .534f * i, .131f + .869f * i, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
    private fun hue(deg: Float): ColorMatrix {
        val r = (deg * PI / 180).toFloat(); val c = cos(r); val s = sin(r)
        return ColorMatrix(floatArrayOf(
            .213f + c * .787f - s * .213f, .715f - c * .715f - s * .715f, .072f - c * .072f + s * .928f, 0f, 0f,
            .213f - c * .213f + s * .143f, .715f + c * .285f + s * .140f, .072f - c * .072f - s * .283f, 0f, 0f,
            .213f - c * .213f - s * .787f, .715f - c * .715f + s * .715f, .072f + c * .928f + s * .072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
}
