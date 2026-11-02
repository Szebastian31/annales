package com.annales.journal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.annales.journal.render.StampRenderer as SR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One of the six looks from the mockup. [dark] flips the status bar icons. */
enum class Look(
    val label: String, val about: String, val bg: Long, val surface: Long, val page: Long, val ink: Long, val muted: Long,
    val line: Long, val acc: Long, val accSoft: Long, val acc2: Long, val onAcc: Long = 0xFFFFFFFF, val dark: Boolean = false,
) {
    KRAFT("Kraft", "brown envelope", 0xFFCDAE84, 0xFFECDCBF, 0xFFDCC39C, 0xFF3B2A1A, 0xFF7A5F42, 0x2E462D14, 0xFF8F3B22, 0xFFE3C9A6, 0xFF3D5A40),
    AIRMAIL("Airmail", "par avion", 0xFFF3F0E8, 0xFFFFFDF8, 0xFFFAF6EE, 0xFF1F2A44, 0xFF7B8296, 0xFFE2DDD2, 0xFFC8323C, 0xFFF6DCDC, 0xFF23498A),
    GALLERY("Gallery", "white wall", 0xFFFFFFFF, 0xFFF5F5F3, 0xFFF1F1EF, 0xFF111111, 0xFF8C8C8C, 0xFFE8E8E8, 0xFFE0482D, 0xFFFBE3DD, 0xFF111111),
    HERBARIUM("Herbarium", "pressed sage", 0xFFDDE2CF, 0xFFF4F3E8, 0xFFEBE9D8, 0xFF2C3326, 0xFF6F7864, 0xFFCCD1BC, 0xFF667A35, 0xFFE1E6C9, 0xFF8A5A6B),
    RIVIERA("Riviera", "sea and coral", 0xFFD9EAEE, 0xFFFDFAF3, 0xFFF7F0E3, 0xFF1D3945, 0xFF6A8690, 0xFFCADDE1, 0xFFE0694A, 0xFFFADFD5, 0xFF2F7A8A),
    INK("Ink", "for evenings", 0xFF1D2027, 0xFF252931, 0xFF2A2E37, 0xFFE7E3DA, 0xFF979BA5, 0xFF343944, 0xFFCFA276, 0xFF3A352F, 0xFF93A7C2, onAcc = 0xFF1D2027, dark = true),
}

/** The current look's colours. Reading them inside a composable redraws it when the look changes. */
object K {
    var look by mutableStateOf(Look.KRAFT)
    private fun c(v: Long) = Color(v)
    val bg get() = c(look.bg)
    val surface get() = c(look.surface)
    val page get() = c(look.page)
    val ink get() = c(look.ink)
    val muted get() = c(look.muted)
    val line get() = c(look.line)
    val acc get() = c(look.acc)
    val accSoft get() = c(look.accSoft)
    val onAcc get() = c(look.onAcc)
    /** The second accent; on Kraft it's the green used for ticks and "more pleasant". */
    val green get() = c(look.acc2)
    val serif = FontFamily.Serif
}

@Composable
fun AnnalesTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = K.acc, onPrimary = K.onAcc, background = K.bg, surface = K.surface,
        onBackground = K.ink, onSurface = K.ink, secondary = K.green, outline = K.line,
    ),
    content = content,
)

/** "Today, *not sealed yet*": plain words, then the accent in italics, the way every title in the mockup reads. */
fun title(plain: String, em: String): AnnotatedString = buildAnnotatedString {
    append(plain)
    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = K.acc)) { append(em) }
}

val TitleStyle get() = TextStyle(fontFamily = K.serif, fontSize = 30.sp, lineHeight = 34.sp, color = K.ink)
val KickerStyle get() = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = K.muted)
val BodyStyle get() = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = K.ink)
val SmallStyle get() = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = K.muted)

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
    /** Let the postmark spill past the paper, as on the envelope. Off for tiny thumbnails, where it would cover neighbours. */
    bleed: Boolean = width >= 50.dp,
) {
    val img by produceState<ImageBitmap?>(null, id, model.version) {
        value = withContext(Dispatchers.IO) { model.stampImage(id) }
    }
    val small = width < 110.dp
    // the paper inside the image (saved stamps carry a margin for the cancels; ones saved before 0.5.0 don't)
    val padded = img?.let { it.width > SR.W } ?: false
    val pw = img?.let { if (padded) it.width - SR.PL - SR.PR else it.width } ?: SR.W
    val ph = img?.let { if (padded) it.height - SR.PT - SR.PB else it.height } ?: SR.H
    // the slot is always portrait-sized. Square and wide stamps keep the mockup's proportions and spill a little sideways,
    // like the cancels do; tiny thumbnails, which clip, shrink them to fit instead.
    val u = if (bleed) width / SR.W else minOf(width / pw, width * 1.25f / ph)
    Box(
        modifier
            .rotate(rotation)
            .size(width, width * 1.25f)
            .then(if (bleed) Modifier else Modifier.clipToBounds()),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.requiredSize(u * pw, u * ph)) {
            img?.let { bmp ->
                if (padded) {
                    // draw it larger, so the paper fills this box and the cancels spill past it
                    Image(
                        bmp, null, contentScale = ContentScale.FillBounds,
                        modifier = Modifier.align(Alignment.Center)
                            .offset(x = u * ((SR.PR - SR.PL) / 2f), y = u * ((SR.PB - SR.PT) / 2f))
                            .requiredSize(u * bmp.width, u * bmp.height),
                    )
                } else Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
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
}
