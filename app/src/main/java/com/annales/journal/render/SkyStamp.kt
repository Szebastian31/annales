package com.annales.journal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

/** The day's sky, for days without a photo: the weather you pick, that night's moon, and hills in the day's mood colour. */
object SkyStamp {
    enum class Weather(val label: String, val glyph: String, val top: Int, val bottom: Int) {
        CLEAR("Clear", "☀", 0xFF8DB4D8.toInt(), 0xFFF1DCC0.toInt()),
        PARTLY("Partly cloudy", "⛅", 0xFFA2BAD2.toInt(), 0xFFEDDCC6.toInt()),
        CLOUDY("Overcast", "☁", 0xFFA8ADB3.toInt(), 0xFFDCD8D0.toInt()),
        RAIN("Rain", "☂", 0xFF808A97.toInt(), 0xFFBCC0C5.toInt()),
        FOG("Fog", "≋", 0xFFC6C5BF.toInt(), 0xFFE8E4DA.toInt()),
    }

    /** Drawn on the mockup's 100 × 122 grid, then scaled to the stamp size. [moodColor] is ARGB, or null for no mood yet. */
    fun draw(date: LocalDate, wx: Weather, moodColor: Int?, seed: Int): Bitmap {
        val out = Bitmap.createBitmap(StampRenderer.W, StampRenderer.H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val sx = StampRenderer.W / 100f
        c.scale(sx, StampRenderer.H / 122f)
        val r = Random(seed)
        val mc = moodColor ?: 0xFF9A948A.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(0f, 0f, 0f, 122f, wx.top, wx.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 100f, 122f, p)
        p.shader = null

        val x = 20 + r.nextFloat() * 60
        if (wx == Weather.CLEAR || wx == Weather.PARTLY) {
            p.color = 0x59FFF4D6; c.drawCircle(x, 28 + r.nextFloat() * 12, 14f, p)
            p.color = 0xFFFFF6DE.toInt(); c.drawCircle(x, 32f, 9.5f, p)
        } else {
            p.color = 0x73E9E6DF; c.drawCircle(x, 30f, 9f, p)
            p.color = 0xE6FFFAF0.toInt(); c.drawPath(moon(x, 30f, 9f, moonAge(date)), p)
        }
        if (wx != Weather.CLEAR) {
            val n = if (wx == Weather.CLOUDY || wx == Weather.RAIN) 4 else 2
            repeat(n) {
                val cx = r.nextFloat() * 80; val cy = 18 + r.nextFloat() * 36
                p.color = 0xFFFFFFFF.toInt(); p.alpha = ((.5f + r.nextFloat() * .3f) * 255).toInt()
                c.drawOval(RectF(cx - 14 - r.nextFloat() * 8, cy - 5, cx + 14 + r.nextFloat() * 8, cy + 5), p)
                c.drawOval(RectF(cx + 6 - 8, cy - 4 - 5, cx + 6 + 8, cy - 4 + 5), p)
            }
            p.alpha = 255
        }
        if (wx == Weather.RAIN) {
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3EEF2F6.toInt(); strokeWidth = .8f }
            repeat(18) { val lx = r.nextFloat() * 100; val ly = 40 + r.nextFloat() * 40; c.drawLine(lx, ly, lx - 2, ly + 6, line) }
        }
        if (wx == Weather.FOG) {
            p.color = 0x73FFFFFF
            for (i in 0 until 4) c.drawRoundRect(RectF(-5f, 55f + i * 9, 105f, 59f + i * 9), 2f, 2f, p)
        }

        // two hills in the mood colour and a little tree
        val h1 = 70 + r.nextFloat() * 8; val h2 = 78 + r.nextFloat() * 8; val tx = 12 + r.nextFloat() * 60
        p.color = mc; p.alpha = 128
        c.drawPath(Path().apply {
            moveTo(0f, h1); cubicTo(25f, h1 - 14, 45f, h1 + 6, 70f, h1 - 8); cubicTo(85f, h1 - 13, 100f, h1 - 2, 100f, h1)
            lineTo(100f, 122f); lineTo(0f, 122f); close()
        }, p)
        p.alpha = 235
        c.drawPath(Path().apply {
            moveTo(0f, h2 + 8); cubicTo(30f, h2 - 4, 55f, h2 + 12, 100f, h2); lineTo(100f, 122f); lineTo(0f, 122f); close()
        }, p)
        p.alpha = 255
        c.drawRect(tx, h2 - 6, tx + 1.6f, h2 + 3, p)
        c.drawCircle(tx + .8f, h2 - 8, 4.5f, p)
        return out
    }

    private fun moonAge(d: LocalDate): Double {
        // same formula as Insights.moonLight, kept as the phase rather than the light
        val jd = d.toEpochDay() + 2440587.5
        var age = ((jd - 2451550.1) / 29.530588853) % 1.0
        if (age < 0) age += 1
        return age
    }

    /** The lit part of the moon: a half disc closed by an ellipse whose width follows the phase. */
    private fun moon(cx: Float, cy: Float, r: Float, age: Double): Path {
        val k = cos(2 * PI * age).toFloat()
        val waxing = age < .5
        val rx = abs(k) * r
        return Path().apply {
            moveTo(cx, cy - r)
            // outer edge: the right half while waxing, the left half while waning
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, if (waxing) 180f else -180f)
            // terminator back to the top
            val inner = RectF(cx - rx, cy - r, cx + rx, cy + r)
            val bulgeOut = (k < 0) // gibbous: the terminator bulges past the middle
            arcTo(inner, 90f, if (waxing == bulgeOut) 180f else -180f)
            close()
        }
    }

}
