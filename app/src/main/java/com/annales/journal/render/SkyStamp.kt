package com.annales.journal.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The day's sky, for days without a photo, drawn like a small print: a graded sky, the sun or a veiled light, soft
 * clouds, layered ridges that fade into the distance in the day's mood colour, and a quiet caption with that night's
 * moon. Drawn in units on a 100 × 125 grid (the portrait stamp), in real pixels so the blurs stay soft at any size.
 */
object SkyStamp {
    /** [top], [mid] and [bottom] are the sky's gradient; [light] is the sun's (or the veiled light's) colour. */
    enum class Weather(val label: String, val glyph: String, val top: Int, val bottom: Int, val mid: Int, val light: Int) {
        CLEAR("Clear", "☀", 0xFF5E8FC4.toInt(), 0xFFF6D9B4.toInt(), 0xFFA9C6E0.toInt(), 0xFFFFF1D2.toInt()),
        PARTLY("Partly cloudy", "⛅", 0xFF7D9EC2.toInt(), 0xFFF0DCC4.toInt(), 0xFFB9CCDD.toInt(), 0xFFFFF0D6.toInt()),
        CLOUDY("Overcast", "☁", 0xFF8C939D.toInt(), 0xFFE2DDD3.toInt(), 0xFFB9BCBF.toInt(), 0xFFF4F0E8.toInt()),
        RAIN("Rain", "☂", 0xFF5D6774.toInt(), 0xFFB9BEC3.toInt(), 0xFF8A939D.toInt(), 0xFFDDE2E7.toInt()),
        FOG("Fog", "≋", 0xFFB9B9B4.toInt(), 0xFFEDE9E0.toInt(), 0xFFD5D3CC.toInt(), 0xFFFFFCF4.toInt()),
    }

    private const val GW = 100f
    private const val GH = 125f

    /** [moodColor] is ARGB, or null when no mood is set yet (then the hills are a warm grey). */
    fun draw(date: LocalDate, wx: Weather, moodColor: Int?, seed: Int): Bitmap {
        val w = StampRenderer.W; val h = StampRenderer.H
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val u = w / GW
        val r = Random(seed)
        val mood = moodColor ?: 0xFF8F887E.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

        // sky: three stops, the lightest near the horizon
        p.shader = LinearGradient(0f, 0f, 0f, h * .72f, intArrayOf(wx.top, wx.mid, wx.bottom), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // the light
        val lx = (24 + r.nextFloat() * 52) * u
        val ly = (when (wx) { Weather.CLEAR -> 30f; Weather.PARTLY -> 36f; else -> 40f } + r.nextFloat() * 8) * u
        when (wx) {
            Weather.CLEAR, Weather.PARTLY -> {
                glow(c, lx, ly, 46 * u, wx.light, 0x66)
                glow(c, lx, ly, 20 * u, wx.light, 0x99)
                p.shader = RadialGradient(lx - 2 * u, ly - 2 * u, 9 * u, intArrayOf(0xFFFFFFFF.toInt(), wx.light), null, Shader.TileMode.CLAMP)
                c.drawCircle(lx, ly, 8.5f * u, p); p.shader = null
            }
            Weather.CLOUDY, Weather.FOG -> {
                glow(c, lx, ly, 34 * u, wx.light, 0x55)
                p.color = wx.light; p.alpha = 150; p.maskFilter = BlurMaskFilter(1.2f * u, BlurMaskFilter.Blur.NORMAL)
                c.drawCircle(lx, ly, 8f * u, p); p.maskFilter = null; p.alpha = 255
            }
            Weather.RAIN -> glow(c, lx, ly, 40 * u, wx.light, 0x33)
        }
        if (wx == Weather.CLEAR) birds(c, r, u, lx, ly)

        // clouds, far ones first
        val clouds = when (wx) { Weather.CLEAR -> 0; Weather.PARTLY -> 3; Weather.CLOUDY -> 5; Weather.RAIN -> 6; Weather.FOG -> 2 }
        repeat(clouds) { i ->
            val cx = (r.nextFloat() * 110 - 5) * u
            val cy = (14 + i * (if (wx == Weather.PARTLY) 13 else 8) + r.nextFloat() * 6) * u
            val size = (if (wx == Weather.PARTLY) 1f else 1.25f) * (.75f + r.nextFloat() * .5f)
            cloud(c, r, u, cx, cy, size, rain = wx == Weather.RAIN, near = i == clouds - 1)
        }
        if (wx == Weather.RAIN || wx == Weather.CLOUDY) {
            // a low ceiling, darker at the top
            p.shader = LinearGradient(0f, 0f, 0f, 34 * u, if (wx == Weather.RAIN) 0x80454D57.toInt() else 0x5566707A, 0x00000000, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), 34 * u, p); p.shader = null
        }

        // ridges, far to near: each a little more of the mood colour, a little less of the sky
        val ridges = listOf(
            Triple(66f, 5f, .72f), Triple(76f, 6f, .52f), Triple(87f, 7f, .28f), Triple(99f, 6f, .0f),
        )
        ridges.forEachIndexed { i, (base, amp, haze) ->
            val col = mix(mood, wx.bottom, haze)
            val top = mix(col, 0xFFFFFFFF.toInt(), if (i == ridges.lastIndex) .06f else .12f)
            val bot = mix(col, 0xFF000000.toInt(), if (i == ridges.lastIndex) .22f else .08f)
            p.shader = LinearGradient(0f, (base - amp) * u, 0f, GH * u, top, bot, Shader.TileMode.CLAMP)
            c.drawPath(ridge(r, u, base, amp, smooth = i < 2), p); p.shader = null
            // mist sitting in the valley behind the next ridge
            if (i < ridges.lastIndex) {
                val my = (ridges[i + 1].first - ridges[i + 1].second - 2) * u
                p.shader = LinearGradient(0f, my - 7 * u, 0f, my + 3 * u, 0x00FFFFFF, if (wx == Weather.FOG) 0xCCF6F3EC.toInt() else 0x55F6F1E8, Shader.TileMode.CLAMP)
                c.drawRect(0f, my - 7 * u, w.toFloat(), my + 3 * u, p); p.shader = null
            }
            if (i == 2) tree(c, r, u, ridges[3].first - 7, mix(mood, 0xFF000000.toInt(), .3f))
        }

        if (wx == Weather.RAIN) rain(c, r, u, w, h)
        if (wx == Weather.FOG) {
            repeat(4) { i ->
                p.color = 0xFFF8F5EE.toInt(); p.alpha = 120 - i * 18
                p.maskFilter = BlurMaskFilter(3f * u, BlurMaskFilter.Blur.NORMAL)
                val y = (54 + i * 13 + r.nextFloat() * 4) * u
                c.drawRoundRect(RectF(-10 * u, y, (GW + 10) * u, y + (5 + r.nextFloat() * 3) * u), 4 * u, 4 * u, p)
            }
            p.maskFilter = null; p.alpha = 255
        }

        caption(c, u, date, wx)
        grain(c, r, w, h)
        // a soft vignette, like ink settling at the edges of a print
        p.shader = RadialGradient(w / 2f, h * .45f, h * .78f, intArrayOf(0x00000000, 0x00000000, 0x33201812), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
        return out
    }

    private fun glow(c: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Int) {
        val a = (color and 0x00FFFFFF) or (alpha shl 24)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = RadialGradient(x, y, radius, intArrayOf(a, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, radius, p)
    }

    /** A cloud: a few soft puffs over a flat base, with a faint shadow underneath. */
    private fun cloud(c: Canvas, r: Random, u: Float, cx: Float, cy: Float, size: Float, rain: Boolean, near: Boolean) {
        val puffs = List(5 + r.nextInt(3)) {
            val dx = (it - 3) * 6.5f * size * u + (r.nextFloat() - .5f) * 3 * u
            val rad = (4.5f + r.nextFloat() * 4.5f) * size * u * (1f - abs(it - 3) * .12f)
            Triple(cx + dx, cy - rad * .35f, rad)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.maskFilter = BlurMaskFilter(1.6f * u * size, BlurMaskFilter.Blur.NORMAL)
        p.color = if (rain) 0xFF6E7782.toInt() else 0xFF9FA9B5.toInt(); p.alpha = if (rain) 150 else 70
        puffs.forEach { (x, y, rad) -> c.drawCircle(x, y + 2.2f * u, rad, p) }
        p.color = if (rain) 0xFFC3C8CE.toInt() else 0xFFFFFFFF.toInt(); p.alpha = if (near) 240 else 190
        puffs.forEach { (x, y, rad) -> c.drawCircle(x, y, rad, p) }
        c.drawRoundRect(RectF(cx - 20 * size * u, cy - 1 * u, cx + 20 * size * u, cy + 4.5f * size * u), 4 * u, 4 * u, p)
    }

    /** A ridge line from a few slow waves; [smooth] ones (far away) have fewer, gentler bumps. */
    private fun ridge(r: Random, u: Float, base: Float, amp: Float, smooth: Boolean): Path {
        val ph = List(3) { r.nextFloat() * 2 * PI.toFloat() }
        val fr = if (smooth) listOf(1.1f, 2.3f, 4.1f) else listOf(1.6f, 3.4f, 7.3f)
        val wt = if (smooth) listOf(.62f, .3f, .08f) else listOf(.5f, .32f, .18f)
        fun y(x: Float) = base - amp * (0..2).sumOf { wt[it] * sin(x / GW * fr[it] * PI.toFloat() + ph[it]).toDouble() }.toFloat()
        return Path().apply {
            moveTo(0f, y(0f) * u)
            var x = 2f
            while (x <= GW + 2) { lineTo(x * u, y(x) * u); x += 2f }
            lineTo(GW * u + 4, GH * u); lineTo(0f, GH * u); close()
        }
    }

    /** A slim tree on the third ridge: trunk and a narrow crown. */
    private fun tree(c: Canvas, r: Random, u: Float, ground: Float, color: Int) {
        val x = (14 + r.nextFloat() * 66) * u; val g = ground * u
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawRect(x - .45f * u, g - 7 * u, x + .45f * u, g, p)
        c.drawOval(RectF(x - 2.6f * u, g - 15 * u, x + 2.6f * u, g - 5 * u), p)
    }

    private fun birds(c: Canvas, r: Random, u: Float, lx: Float, ly: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x993A3530.toInt(); style = Paint.Style.STROKE; strokeWidth = .45f * u; strokeCap = Paint.Cap.ROUND }
        val bx = if (lx > 50 * u) 22 * u else 70 * u
        repeat(3) { i ->
            val x = bx + (i * 5 + r.nextFloat() * 2) * u; val y = ly + (6 + i * 2.5f + r.nextFloat() * 2) * u; val s = (1.4f - i * .2f) * u
            c.drawPath(Path().apply { moveTo(x - 2 * s, y - s * .6f); quadTo(x - s, y - 1.5f * s, x, y); quadTo(x + s, y - 1.5f * s, x + 2 * s, y - s * .6f) }, p)
        }
    }

    private fun rain(c: Canvas, r: Random, u: Float, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        repeat(70) {
            val x = r.nextFloat() * (w + 20 * u); val y = (22 + r.nextFloat() * 90) * u; val len = (4 + r.nextFloat() * 5) * u
            p.color = 0xFFF1F4F7.toInt(); p.alpha = 50 + r.nextInt(90); p.strokeWidth = (.25f + r.nextFloat() * .3f) * u
            c.drawLine(x, y, x - len * .28f, y + len, p)
        }
    }

    /** Bottom left, on the near ridge: that night's moon and the weather, small and spaced, like a print's plate line. */
    private fun caption(c: Canvas, u: Float, date: LocalDate, wx: Weather) {
        val y = 117.5f * u; val mx = 9.5f * u; val mr = 2.4f * u
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x40FFFFFF; c.drawCircle(mx, y - 1.3f * u, mr, p)
        p.color = 0xE6FFFAF0.toInt(); c.drawPath(moon(mx, y - 1.3f * u, mr, moonAge(date)), p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFAF0.toInt(); textSize = 3.4f * u; letterSpacing = .28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        c.drawText(wx.label.uppercase(), mx + mr + 2.8f * u, y, t)
    }

    /** Fine print grain, so flat colour doesn't look digital. */
    private fun grain(c: Canvas, r: Random, w: Int, h: Int) {
        val p = Paint()
        repeat(w * h / 90) {
            p.color = if (r.nextBoolean()) 0x0F000000 else 0x12FFFFFF
            val x = r.nextFloat() * w; val y = r.nextFloat() * h
            c.drawRect(x, y, x + 1.6f, y + 1.6f, p)
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(v: Int, s: Int) = (v shr s) and 0xFF
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
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
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, if (waxing) 180f else -180f)
            val inner = RectF(cx - rx, cy - r, cx + rx, cy + r)
            val bulgeOut = (k < 0)
            arcTo(inner, 90f, if (waxing == bulgeOut) 180f else -180f)
            close()
        }
    }
}
