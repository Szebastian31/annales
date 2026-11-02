package com.annales.journal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.data.Moods

/**
 * The state-of-mind slider: a horizon that bends up in the middle, coloured violet to blue along the mockup's gradient.
 * Drag or tap anywhere along it; the mood snaps to the nearest of the seven steps when you let go.
 */
@Composable
fun MoodHorizon(mood: Int?, enabled: Boolean, onMood: (Int) -> Unit, modifier: Modifier = Modifier) {
    var t by remember(mood) { mutableFloatStateOf(mood?.let { it / 6f } ?: -1f) }
    val live = if (t >= 0f) (t * 6).let { Math.round(it) } else null
    val color = live?.let { Color(Moods.color(it)) } ?: K.muted

    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                live?.let { Moods.names[it] } ?: "How did it feel?",
                style = TextStyle(fontFamily = K.serif, fontSize = 20.sp, color = if (live != null) color else K.ink),
                modifier = Modifier.weight(1f),
            )
            if (live == null) Text("drag along the line", style = SmallStyle)
        }
        Canvas(
            Modifier.fillMaxWidth().height(78.dp).padding(vertical = 6.dp)
                .then(if (!enabled) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { p -> t = (p.x / size.width).coerceIn(0f, 1f); onMood(Math.round(t * 6)) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { if (t >= 0f) onMood(Math.round(t * 6)) },
                        ) { change, _ -> t = (change.position.x / size.width).coerceIn(0f, 1f) }
                    })
        ) {
            val w = size.width; val h = size.height
            val pad = 12.dp.toPx()
            val y0 = h - pad; val yc = -h * .35f // a strong bend: the control point sits above the canvas
            fun yAt(x: Float): Float { val u = x / w; return (1 - u) * (1 - u) * y0 + 2 * u * (1 - u) * yc + u * u * y0 }
            val curve = Path().apply { moveTo(0f, y0); quadraticBezierTo(w / 2, yc, w, y0) }
            val stops = Moods.stops.map { Color(it.toInt()) }
            // soft glow under the line, then the line
            drawPath(curve, Brush.horizontalGradient(stops.map { it.copy(alpha = .28f) }), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curve, Brush.horizontalGradient(stops), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            // the seven steps as faint ticks
            for (i in 0..6) {
                val x = w * i / 6f
                drawCircle(Color.White.copy(alpha = .7f), 2.5.dp.toPx(), Offset(x, yAt(x)))
            }
            if (t >= 0f) {
                val x = w * t; val c = Offset(x, yAt(x))
                drawCircle(color.copy(alpha = .3f), 15.dp.toPx(), c)
                drawCircle(Color.White, 10.dp.toPx(), c)
                drawCircle(color, 7.dp.toPx(), c)
            }
        }
    }
}
