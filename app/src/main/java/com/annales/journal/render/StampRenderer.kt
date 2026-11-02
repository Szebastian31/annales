package com.annales.journal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** What the post office stamps on a sealed day. */
data class Seal(val date: LocalDate, val hour: Int, val office: String, val postcode: String, val ink: Int, val seed: Int)

/**
 * Draws a stamp the way the mockup does: the photo cut to a perforated edge, then, once sealed, a Poczta Polska datownik
 * hanging off the right edge and wavy cancel lines over the top-left, each hit pressed a little differently.
 */
object StampRenderer {
    const val W = 800
    const val H = 1000
    private val u = min(W, H) / 150f // the mockup's unit: stamps there are about 150 px wide
    private val k = W / 150f

    /** The paper outline with the perforation punched out. */
    fun shape(cut: String): Path {
        val (r, p) = when (cut) {
            "scallop" -> 5.8f * u to 13.5f * u
            else -> 3.5f * u to 9.8f * u
        }
        val holes = Path()
        fun side(len: Float, at: (Float) -> Pair<Float, Float>) {
            val n = maxOf(2, (len / p).roundToInt())
            for (i in 0..n) { val (x, y) = at(i * len / n); holes.addCircle(x, y, r, Path.Direction.CW) }
        }
        side(W.toFloat()) { it to 0f }; side(W.toFloat()) { it to H.toFloat() }
        side(H.toFloat()) { 0f to it }; side(H.toFloat()) { W.toFloat() to it }
        return Path().apply {
            addRect(0f, 0f, W.toFloat(), H.toFloat(), Path.Direction.CW)
            op(holes, Path.Op.DIFFERENCE)
        }
    }

    /** [photo] is the cropped photo (any size, 4:5). Without a [seal] it's a draft: just the cut paper. */
    fun render(photo: Bitmap, cut: String, seal: Seal?): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.clipPath(shape(cut))
        c.drawBitmap(photo, null, Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (seal != null) c.drawBitmap(cancels(seal), 0f, 0f, null)
        return out
    }

    /** Every hit of the stamp is a little different: angle, pressure and patchy spots. Same numbers as the mockup's inkHit. */
    private class Hit(seed: Int) {
        private val r = Random(seed * 7919 + 13)
        val light = r.nextFloat() < .2f
        val rot = -22f + r.nextFloat() * 28f
        val op = if (light) .62f + r.nextFloat() * .12f else .8f + r.nextFloat() * .16f
        val dx = (r.nextFloat() - .5f) * 8f * k
        val dy = (r.nextFloat() - .5f) * 8f * k
        val patch = if (light) .55f else .3f + r.nextFloat() * .15f
        val noise = r.nextInt()
    }

    private fun cancels(s: Seal): Bitmap {
        val layer = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(layer)
        val pm = Hit(s.seed)
        val kl = Hit(s.seed + 101)

        // the date ring: 62% of the short side, hanging off the right edge
        val R = min(W, H) * .62f
        mark(c, pm) { m ->
            m.translate(W - R * .58f + pm.dx, H * .5f - R * .42f + pm.dy)
            m.rotate(pm.rot, R / 2, R / 2)
            m.scale(R / 100f, R / 100f)
            datownik(m, s)
        }
        // the wavy lines: a quarter of them off the left edge, just under the day number
        val K = min(W, H) * .3f
        mark(c, kl) { m ->
            m.translate(-K * 1.9f * .25f - kl.dx * .3f, H * .16f + kl.dy * .4f)
            m.rotate(pm.rot * .4f - 13f, K * .95f, K / 2)
            m.scale(K / 60f, K / 60f)
            waves(m, s.ink)
        }
        return layer
    }

    /** Draws one mark on its own bitmap, wears its ink thin in patches, then presses it on [target] at the hit's pressure. */
    private fun mark(target: Canvas, hit: Hit, draw: (Canvas) -> Unit) {
        val m = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val mc = Canvas(m)
        mc.save(); draw(mc); mc.restore()
        val mask = wear(hit)
        mc.drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        mask.recycle()
        target.drawBitmap(m, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (hit.op * 255).roundToInt() })
        m.recycle()
    }

    /** An alpha mask: mostly solid, with soft thin patches and fine grain. */
    private fun wear(hit: Hit): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val mc = Canvas(out)
        val r = Random(hit.noise)
        fun grid(cols: Int, rows: Int, lo: Float, hi: Float): Bitmap {
            val g = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            for (y in 0 until rows) for (x in 0 until cols) {
                val a = (lo + (hi - lo) * r.nextFloat()).coerceIn(0f, 1f)
                g.setPixel(x, y, Color.argb((a * 255).roundToInt(), 0, 0, 0))
            }
            return g
        }
        val full = Rect(0, 0, W, H)
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        val patches = grid(10, 12, 1f - hit.patch * 2.2f, 1.6f)
        mc.drawBitmap(patches, null, full, smooth)
        patches.recycle()
        val grain = grid(200, 250, .55f, 1.4f)
        mc.drawBitmap(grain, null, full, Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        grain.recycle()
        return out
    }

    /** Poczta Polska datownik on a 100-unit grid: ring, office name over the top, postcode under, date bridge dd.mm.yy and hour. */
    private fun datownik(c: Canvas, s: Seal) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = s.ink; strokeCap = Paint.Cap.ROUND }
        line.strokeWidth = 2.6f; c.drawCircle(50f, 50f, 44f, line)
        line.strokeWidth = 1.8f; c.drawLine(12f, 39f, 88f, 39f, line); c.drawLine(12f, 61f, 88f, 61f, line)

        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.ink; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val d = s.date
        mono.textSize = 14.5f
        c.drawText("%02d.%02d.%02d".format(d.dayOfMonth, d.monthValue, d.year % 100), 50f, 55.5f, mono)
        mono.textSize = 7.5f
        c.drawText("-${s.hour}", 50f, 35f, mono)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.ink; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        fun onArc(text: String, size: Float, spacing: Float, oval: RectF, start: Float, sweep: Float, radius: Float) {
            if (text.isBlank()) return
            label.textSize = size; label.letterSpacing = spacing / size
            val arc = Path().apply { addArc(oval, start, sweep) }
            val len = (PI * radius).toFloat()
            c.drawTextOnPath(text, arc, ((len - label.measureText(text)) / 2).coerceAtLeast(0f), 0f, label)
        }
        val top = s.office.uppercase()
        onArc(top, if (top.length > 9) 9.5f else 11f, 1.3f, RectF(14f, 14f, 86f, 86f), 180f, 180f, 36f)
        onArc(s.postcode, 7.5f, 1.2f, RectF(16f, 20f, 84f, 88f), 180f, -180f, 34f)
    }

    /** Five wavy cancel lines on the mockup's 114 × 60 grid. */
    private fun waves(c: Canvas, ink: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = ink; strokeWidth = 2.6f; strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 5) {
            val y = 8f + i * 11f
            val path = Path().apply {
                moveTo(4f, y)
                var x = 4f
                for (j in 0 until 6) { quadTo(x + 9f, y + if (j % 2 == 0) -6f else 6f, x + 18f, y); x += 18f }
            }
            c.drawPath(path, p)
        }
    }
}
