package com.annales.journal.ui

import com.annales.journal.tr
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.render.StampRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** "Frame the memory": move and pinch the photo under the stamp frame, in portrait, square or wide. */
@Composable
fun CropScreen(photo: Bitmap, cut: String = "classic", startShape: String = "portrait", onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val image = remember(photo) { photo.asImageBitmap() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var filter by remember { mutableStateOf("natural") }
    var shape by remember { mutableStateOf(startShape) }
    val (pw, ph) = StampRenderer.size(shape)
    val tint = remember(filter) { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(com.annales.journal.render.Filters.matrix(filter))) }

    // the frame: about 80% of the width in the stamp's shape, centred (and never taller than the space)
    fun frame(): Rect {
        val fw = min(box.width * (if (pw > ph) .86f else .8f), box.height * .8f * pw / ph)
        val fh = fw * ph / pw
        return Rect(Offset((box.width - fw) / 2, (box.height - fh) / 2), Size(fw, fh))
    }
    fun minScale(f: Rect) = max(f.width / photo.width, f.height / photo.height)
    fun clamp(f: Rect, s: Float, o: Offset): Offset {
        // at the smallest size the photo is exactly as wide (or tall) as the frame, and rounding can make the allowed range
        // a hair "backwards", which coerceIn refuses with a crash; then just centre it on that side
        fun fit(v: Float, lo: Float, hi: Float) = if (lo <= hi) v.coerceIn(lo, hi) else (lo + hi) / 2
        return Offset(fit(o.x, f.right - photo.width * s, f.left), fit(o.y, f.bottom - photo.height * s, f.top))
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF1F1A16)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(tr("Cancel"), color = Color.White) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Drag · pinch to resize"), style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color(0xB3FFFFFF)))
                Text(tr("Frame the memory"), style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 18.sp, color = Color.White))
            }
            TextButton(onClick = {
                val f = frame()
                if (scale <= 0f) return@TextButton
                val left = ((f.left - offset.x) / scale).roundToInt().coerceIn(0, photo.width - 1)
                val top = ((f.top - offset.y) / scale).roundToInt().coerceIn(0, photo.height - 1)
                val w = (f.width / scale).roundToInt().coerceIn(1, photo.width - left)
                val h = (f.height / scale).roundToInt().coerceIn(1, photo.height - top)
                val part = Bitmap.createBitmap(photo, left, top, w, h)
                onDone(com.annales.journal.render.Filters.apply(Bitmap.createScaledBitmap(part, pw, ph, true), filter))
            }) { Text(tr("Done"), color = Color(0xFFF2C9A0), fontWeight = FontWeight.Bold) }
        }

        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { size ->
            box = size
            val f = frame(); val s = minScale(f)
            scale = s
            offset = Offset(box.width / 2f - photo.width * s / 2, box.height / 2f - photo.height * s / 2)
        }.pointerInput(photo, shape) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val f = frame(); val lo = minScale(f)
                val s = (scale * zoom).coerceIn(lo, lo * 6f)
                val z = s / scale
                scale = s
                offset = clamp(f, s, centroid - (centroid - offset) * z + pan)
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                if (scale <= 0f) return@Canvas
                drawImage(
                    image,
                    dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                    dstSize = IntSize((photo.width * scale).roundToInt(), (photo.height * scale).roundToInt()),
                    colorFilter = tint,
                )
                val f = frame()
                val hole = Path().apply { addRect(f) }
                clipPath(hole, ClipOp.Difference) { drawRect(Color(0x99000000)) }
                // the edge it will get, so you can see what the perforation bites off
                val edge = StampRenderer.shape(cut, 7, f.width, f.height).asComposePath()
                translate(f.left, f.top) { drawPath(edge, Color.White, style = Stroke(1.5.dp.toPx())) }
            }
        }
        // Size: the same zoom as pinching, kept centred on the frame
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Size"), style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xB3FFFFFF)))
            val f = frame(); val lo = minScale(f)
            androidx.compose.material3.Slider(
                value = if (scale > 0f && lo > 0f) ((scale / lo - 1f) / 5f).coerceIn(0f, 1f) else 0f,
                onValueChange = { t ->
                    val s = lo * (1f + 5f * t)
                    val c = f.center
                    offset = clamp(f, s, c - (c - offset) * (s / scale))
                    scale = s
                },
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color(0xFFF2C9A0), activeTrackColor = Color(0xFFF2C9A0)),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
        // Shape: the mockup's portrait, square and wide. The photo stays where it is, grown just enough to fill the new frame.
        LaunchedEffect(shape) {
            if (box == IntSize.Zero || scale <= 0f) return@LaunchedEffect
            val f = frame(); val s = max(scale, minScale(f))
            offset = clamp(f, s, offset); scale = s
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StampRenderer.shapes.forEach { (k, n, _) -> CropChip(tr(n), k == shape) { shape = k } }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.annales.journal.render.Filters.all.forEach { (k, n) -> CropChip(tr(n), k == filter) { filter = k } }
        }
    }
}

@Composable
private fun CropChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, style = TextStyle(fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Color(0xFF1F1A16) else Color.White),
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Color(0xFFF2C9A0) else Color(0x26FFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
