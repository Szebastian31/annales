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

    // Saved stamps keep a clear margin round the paper, so the cancels can hang off the edges as in the mockup:
    // the date ring past the right edge, a quarter of the wavy lines past the left.
    const val PL = 160
    const val PR = 280
    const val PT = 60
    const val PB = 60
    const val OW = W + PL + PR
    const val OH = H + PT + PB
    private val k = W / 150f

    /** The ten edges from the mockup: key, name, and a few words on what they look like. */
    val cuts = listOf(
        Triple("classic", "Classic", "round holes"), Triple("scallop", "Scallop", "big soft bites"),
        Triple("pinhole", "Pinhole", "tiny, dense"), Triple("comb", "Comb", "square teeth"),
        Triple("pinking", "Pinking", "zigzag shears"), Triple("wave", "Wave", "smooth ripple"),
        Triple("rouletted", "Rouletted", "slit tear line"), Triple("deckle", "Deckle", "torn by hand"),
        Triple("ticket", "Ticket", "notched stub"), Triple("sticker", "Sticker", "die-cut round"),
    )

    /** The paper outline with its edge cut, in a [w] × [h] box. [seed] tears each deckle edge differently. */
    fun shape(cut: String, seed: Int = 7, w: Float = W.toFloat(), h: Float = H.toFloat()): Path {
        val u = min(w, h) / 150f
        val holes = Path()
        var paper = Path().apply { addRect(0f, 0f, w, h, Path.Direction.CW) }

        // points every L/n along the four edges, corners included
        fun sides(p: Float, at: (x: Float, y: Float) -> Unit) {
            listOf(w to false, h to true).forEach { (len, vertical) ->
                val n = maxOf(2, (len / p).roundToInt())
                for (i in 0..n) {
                    val t = i * len / n
                    if (vertical) { at(0f, t); at(w, t) } else { at(t, 0f); at(t, h) }
                }
            }
        }
        fun circles(r: Float, p: Float) = sides(p) { x, y -> holes.addCircle(x, y, r, Path.Direction.CW) }
        // a wavy or torn outline: depth(t, period) inward from each edge, sampled every [step]
        fun outline(p: Float, step: Float, depth: (t: Float, period: Float) -> Float) {
            paper = Path()
            var first = true
            fun pt(x: Float, y: Float) { if (first) { paper.moveTo(x, y); first = false } else paper.lineTo(x, y) }
            fun run(len: Float, map: (t: Float, d: Float) -> Pair<Float, Float>) {
                val period = len / maxOf(1, (len / p).roundToInt())
                val k = kotlin.math.ceil(len / step).toInt()
                for (i in 0 until k) { val t = i * len / k; val (x, y) = map(t, depth(t, period)); pt(x, y) }
            }
            run(w) { t, d -> t to d }; run(h) { t, d -> (w - d) to t }
            run(w) { t, d -> (w - t) to (h - d) }; run(h) { t, d -> d to (h - t) }
            paper.close()
        }

        when (cut) {
            "scallop" -> circles(5.8f * u, 13.5f * u)
            "pinhole" -> circles(1.3f * u, 4.3f * u)
            "comb" -> { val s = 4.8f * u; sides(9.8f * u) { x, y -> holes.addRect(x - s / 2, y - s / 2, x + s / 2, y + s / 2, Path.Direction.CW) } }
            "pinking" -> outline(7.4f * u, .6f * u) { t, period -> val q = (t % period) / period; 4f * u * (if (q < .5f) q * 2 else 2 - q * 2) }
            "wave" -> outline(12f * u, .9f * u) { t, period -> 3.4f * u * (1 - kotlin.math.cos(2 * Math.PI * t / period).toFloat()) / 2 }
            "deckle" -> {
                val r = Random(seed); val ph = FloatArray(2) { r.nextFloat() * 9 }
                outline(9f * u, 1.5f * u) { t, _ ->
                    u * (1.4f + 1.2f * kotlin.math.sin(t / (5 * u) + ph[0]) + .8f * kotlin.math.sin(t / (2.1f * u) + ph[1]) + .9f * r.nextFloat())
                }
            }
            "rouletted" -> {
                val m = 3.2f * u; val l = 4.6f * u; val th = 1.3f * u
                listOf(w to false, h to true).forEach { (len, vertical) ->
                    val n = ((len - 2 * m) / (7.4f * u)).roundToInt()
                    for (i in 1 until n) {
                        val t = m + i * (len - 2 * m) / n
                        listOf(m, (if (vertical) w else h) - m).forEach { o ->
                            if (vertical) holes.addRect(o - th / 2, t - l / 2, o + th / 2, t + l / 2, Path.Direction.CW)
                            else holes.addRect(t - l / 2, o - th / 2, t + l / 2, o + th / 2, Path.Direction.CW)
                        }
                    }
                }
            }
            "ticket" -> {
                val r = 7f * u
                paper = Path().apply { addRoundRect(RectF(0f, 0f, w, h), 2 * u, 2 * u, Path.Direction.CW) }
                listOf(0f to 0f, w to 0f, 0f to h, w to h).forEach { (x, y) -> holes.addCircle(x, y, r * .8f, Path.Direction.CW) }
                listOf(0f to h / 2, w to h / 2).forEach { (x, y) -> holes.addCircle(x, y, r, Path.Direction.CW) }
                var x = r * 1.4f
                while (x < w - r) { holes.addCircle(x, h * .78f, .85f * u, Path.Direction.CW); x += 3.6f * u }
            }
            "sticker" -> { val rr = min(w, h) * .12f; paper = Path().apply { addRoundRect(RectF(0f, 0f, w, h), rr, rr, Path.Direction.CW) } }
            else -> circles(3.5f * u, 9.8f * u)
        }
        return Path(paper).apply { op(holes, Path.Op.DIFFERENCE) }
    }

    /** [photo] is the cropped photo (any size, 4:5). Without a [seal] it's a draft: just the cut paper. */
    fun render(photo: Bitmap, cut: String, seal: Seal?, seed: Int = 7): Bitmap {
        val out = Bitmap.createBitmap(OW, OH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.save()
        c.translate(PL.toFloat(), PT.toFloat())
        c.clipPath(shape(cut, seed))
        c.drawBitmap(photo, null, Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()
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
        val layer = Bitmap.createBitmap(OW, OH, Bitmap.Config.ARGB_8888)
        val c = Canvas(layer)
        val pm = Hit(s.seed)
        val kl = Hit(s.seed + 101)

        // the date ring: 62% of the short side, hanging off the right edge
        val R = min(W, H) * .62f
        mark(c, pm) { m ->
            m.translate(PL + W - R * .58f + pm.dx, PT + H * .5f - R * .42f + pm.dy)
            m.rotate(pm.rot, R / 2, R / 2)
            m.scale(R / 100f, R / 100f)
            datownik(m, s)
        }
        // the wavy lines: a quarter of them off the left edge, just under the day number
        val K = min(W, H) * .3f
        mark(c, kl) { m ->
            m.translate(PL - K * 1.9f * .25f - kl.dx * .3f, PT + H * .16f + kl.dy * .4f)
            m.rotate(pm.rot * .4f - 13f, K * .95f, K / 2)
            m.scale(K / 60f, K / 60f)
            waves(m, s.ink)
        }
        return layer
    }

    /** Draws one mark on its own bitmap, wears its ink thin in patches, then presses it on [target] at the hit's pressure. */
    private fun mark(target: Canvas, hit: Hit, draw: (Canvas) -> Unit) {
        val m = Bitmap.createBitmap(OW, OH, Bitmap.Config.ARGB_8888)
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
        val out = Bitmap.createBitmap(OW, OH, Bitmap.Config.ARGB_8888)
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
        val full = Rect(0, 0, OW, OH)
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
