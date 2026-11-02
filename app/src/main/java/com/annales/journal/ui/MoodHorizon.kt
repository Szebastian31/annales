package com.annales.journal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.data.Moods

/**
 * The state-of-mind card from the mockup. The horizon is flat at Neutral and bends as you drag: a frown towards Very
 * Unpleasant, a smile towards Very Pleasant, with a glowing orb riding the line. The card is tinted by the mood and the
 * name is the mood colour mixed with ink, so it reads clearly on every look.
 */
@Composable
fun MoodHorizon(moodV: Int?, enabled: Boolean, onMood: (Int) -> Unit, modifier: Modifier = Modifier) {
    var v by remember(moodV) { mutableIntStateOf(moodV ?: 50) }
    val has = moodV != null || v != (moodV ?: 50)
    val lv = Moods.levelOf(v)
    val col = Color(Moods.at(v / 100f))
    val nameCol = if (has) Color(Moods.readable(lv, K.ink.toArgb())) else K.muted
    val tint = if (has) col else Color(0xFF9AA0A6)

    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(lerp(K.surface, tint, .14f), K.surface)), RoundedCornerShape(24.dp))
            .border(1.dp, lerp(K.line, tint, .3f), RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                if (has) Moods.names[lv] else "How did it feel?",
                style = TextStyle(fontFamily = K.serif, fontSize = 23.sp, lineHeight = 26.sp, color = nameCol, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            if (!has) Text("drag along the line", style = SmallStyle)
        }
        Canvas(
            Modifier.fillMaxWidth().height(76.dp)
                .then(if (!enabled) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { p -> v = toV(p.x, size.width.toFloat()); onMood(v) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(onDragEnd = { onMood(v) }) { change, _ -> v = toV(change.position.x, size.width.toFloat()) }
                    })
        ) {
            // the mockup's 320 × 76 drawing, stretched to the width
            val sx = size.width / 320f; val sy = size.height / 76f
            val x0 = 18f * sx; val x1 = 302f * sx; val y = 33f * sy
            val t = v / 100f
            val k = (t - .5f) * 2 * 21 * sy
            val ctrl = y + 2 * k
            val ox = x0 + (x1 - x0) * t
            val oy = y + 4 * k * t * (1 - t)
            val stops = Moods.stops.map { Color(it.toInt()) }
            val grad = Brush.horizontalGradient(stops, x0, x1)

            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = if (has) .32f else .12f), Color.Transparent), Offset(ox, oy), size.width * .45f), size.width * .45f, Offset(ox, oy))
            val curve = Path().apply { moveTo(x0, y); quadraticBezierTo(size.width / 2, ctrl, x1, y) }
            drawPath(curve, grad, alpha = .14f, style = Stroke(9f * sy, cap = StrokeCap.Round))
            drawPath(curve, grad, style = Stroke(2.2f * sy, cap = StrokeCap.Round))
            for (i in 0..6) {
                val tx = x0 + (x1 - x0) * i / 6
                val on = has && lv == i
                drawLine(if (on) col else K.ink.copy(alpha = .25f), Offset(tx, (if (on) 62f else 64f) * sy), Offset(tx, 68f * sy), if (on) 2f * sy else 1f * sy, StrokeCap.Round)
            }
            drawCircle(col.copy(alpha = if (has) .4f else .15f), 16f * sy, Offset(ox, oy))
            drawCircle(Brush.radialGradient(listOf(Color.White, col, col.copy(alpha = .85f)), Offset(ox - 3f * sy, oy - 3.5f * sy), 14f * sy), 10.5f * sy, Offset(ox, oy))
            drawCircle(Color.White, 10.5f * sy, Offset(ox, oy), style = Stroke(2.4f * sy))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            val lab = KickerStyle.copy(fontSize = 8.5.sp, letterSpacing = 1.sp)
            Text("VERY UNPLEASANT", style = lab, modifier = Modifier.weight(1f))
            Text("NEUTRAL", style = lab)
            Text("VERY PLEASANT", style = lab, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

private fun toV(x: Float, w: Float): Int {
    val x0 = 18f / 320 * w; val x1 = 302f / 320 * w
    return (((x - x0) / (x1 - x0)) * 100).toInt().coerceIn(0, 100)
}
