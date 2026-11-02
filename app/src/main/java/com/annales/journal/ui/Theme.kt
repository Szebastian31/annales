package com.annales.journal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.Moods
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Kraft look from the mockup. */
object K {
    val bg = Color(0xFFCDAE84)
    val surface = Color(0xFFECDCBF)
    val page = Color(0xFFDCC39C)
    val ink = Color(0xFF3B2A1A)
    val muted = Color(0xFF7A5F42)
    val line = Color(0x2E462D14)
    val acc = Color(0xFF8F3B22)
    val accSoft = Color(0xFFE3C9A6)
    val green = Color(0xFF3D5A40)
    val serif = FontFamily.Serif
}

@Composable
fun AnnalesTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = K.acc, onPrimary = Color.White, background = K.bg, surface = K.surface,
        onBackground = K.ink, onSurface = K.ink, secondary = K.green, outline = K.line,
    ),
    content = content,
)

/** "Today, *not sealed yet*": plain words, then the accent in italics, the way every title in the mockup reads. */
fun title(plain: String, em: String): AnnotatedString = buildAnnotatedString {
    append(plain)
    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = K.acc)) { append(em) }
}

val TitleStyle = TextStyle(fontFamily = K.serif, fontSize = 30.sp, lineHeight = 34.sp, color = K.ink)
val KickerStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = K.muted)
val BodyStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = K.ink)
val SmallStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = K.muted)

@Composable
fun Header(kicker: String, title: AnnotatedString, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
        Text(kicker.uppercase(), style = KickerStyle)
        Text(title, style = TitleStyle, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A stamp as drawn by the renderer, with the day number and mood dot laid over it like the mockup. */
@Composable
fun StampView(
    model: AnnalesModel, id: String, dayOfYear: Int, mood: Int?, width: Dp,
    modifier: Modifier = Modifier, rotation: Float = 0f,
) {
    val img by produceState<ImageBitmap?>(null, id, model.version) {
        value = withContext(Dispatchers.IO) { model.stampImage(id) }
    }
    val small = width < 110.dp
    Box(
        modifier
            .rotate(rotation)
            .size(width, width * 1.25f)
    ) {
        img?.let { Image(it, null, Modifier.aspectRatio(.8f), contentScale = ContentScale.Fit) }
        Text(
            "$dayOfYear",
            style = TextStyle(
                fontFamily = K.serif, fontWeight = FontWeight.Bold, color = Color.White, fontSize = if (small) 10.5.sp else 16.sp,
                shadow = Shadow(Color(0x80000000), blurRadius = 6f),
            ),
            modifier = Modifier.align(Alignment.TopStart).padding(start = if (small) 7.dp else 13.dp, top = if (small) 7.dp else 12.dp),
        )
        if (mood != null) {
            val s = if (small) 7.dp else 11.dp
            Box(
                Modifier.align(Alignment.TopEnd).padding(if (small) 7.dp else 13.dp).size(s)
                    .background(Color(Moods.color(mood)), CircleShape)
                    .border(if (small) 1.5.dp else 2.dp, Color.White, CircleShape)
            )
        }
    }
}
