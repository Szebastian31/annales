package com.annales.journal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.I18n
import com.annales.journal.data.Day
import com.annales.journal.data.Ink
import com.annales.journal.data.Moon
import com.annales.journal.data.Places
import com.annales.journal.data.Stamp
import com.annales.journal.data.Tags
import com.annales.journal.tn
import com.annales.journal.tr
import java.time.LocalDate
import kotlin.math.roundToInt

private fun capDate(d: LocalDate) = I18n.date(d, "d MMM yyyy")
private val PROMPTS = listOf("What made you smile?", "Where did the light look best?", "Who did you talk to?",
    "What would you tell next year's you?", "One thing you'd keep from today?")

/** "GLIWICE 1" → "Gliwice 1", "KRAKÓW 1" → "Kraków 1". */
fun prettyOffice(mark: String) = mark.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

/** The region shown on the envelope: the trip's, or the home office's (GLIWICE 1 → Silesia). */
private fun regionOf(model: AnnalesModel, date: LocalDate): String =
    model.tripOn(date)?.let { Places.of(it.place).region }
        ?: Places.of(prettyOffice(model.day(date).office.ifBlank { model.office }).substringBefore(" ")).region

/**
 * One day, stamp first: the envelope and its slots, then three rows (state of mind, one line, the postmark) that each
 * open a sheet, then the Seal button. Today and any past day share it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPage(
    model: AnnalesModel, date: LocalDate,
    onAdd: () -> Unit, onOpen: (Stamp) -> Unit, modifier: Modifier = Modifier,
    onOpenPast: (LocalDate, Stamp) -> Unit = { _, _ -> },
    onReplace: (Stamp) -> Unit = {}, onRecrop: (Stamp) -> Unit = {},
) {
    val day = model.day(date)
    val today = date == LocalDate.now()
    var sel by remember(date) { mutableIntStateOf(0) }
    var sheet by remember(date) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(date) { model.fillIn(date) }
    if (sel > day.stamps.lastIndex) sel = maxOf(0, day.stamps.lastIndex)
    val st = day.stamps.getOrNull(sel)

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Column(Modifier.padding(start = 22.dp, end = 18.dp, top = 14.dp, bottom = 8.dp)) {
            Text(I18n.upper("№${date.dayOfYear} · ${I18n.date(date, "EEEE · d MMM yyyy")}"), style = KickerStyle)
            Text(
                title(when {
                    today -> tr(if (day.sealed) "Today, *sealed*" else "Today, *not sealed yet*")
                    else -> tr(if (day.sealed) "{date}, *sealed*" else "{date}, *not sealed yet*", "date" to I18n.date(date, "d MMMM"))
                }),
                style = TitleStyle.copy(fontSize = 30.sp, lineHeight = 33.sp), modifier = Modifier.padding(top = 6.dp),
            )
        }

        Envelope(model, day, st, onAdd) { st?.let(onOpen) }
        Strip(model, day, sel, onSelect = { sel = it }, onAdd = onAdd)
        if (st != null) {
            var placing by remember(st.id) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                Row(
                    Modifier.clip(RoundedCornerShape(99.dp)).background(K.surface).border(1.dp, K.line, RoundedCornerShape(99.dp))
                        .clickable { placing = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📍", fontSize = 13.sp)
                    Text(model.placeOf(date, st), style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 6.dp))
                    Text("  ✎", style = SmallStyle.copy(color = K.acc))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                if (sel > 0) Quiet(tr("★ Make first")) { model.moveStamp(date, st, 0); sel = 0 }
                if (model.hasOriginal(st)) Quiet(tr("Re-crop")) { onRecrop(st) }
                Quiet(tr("Replace")) { onReplace(st) }
                Quiet(tr("Remove"), warn = true) { model.removeStamp(date, st) }
            }
            if (placing) {
                val home = prettyOffice(day.office.ifBlank { model.postmarkFor(date).first }).substringBefore(" ")
                PlaceDialog(st.place, home, model.placeSuggestions(date), onDismiss = { placing = false }) { model.setPlace(date, st, it); placing = false }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(K.surface)) {
            val mood = day.mood
            Step(
                lead = { if (mood != null) Box(Modifier.size(16.dp).background(Color(com.annales.journal.data.Moods.color(mood)), CircleShape)) else Glyph("◐") },
                label = tr("State of mind"),
                value = mood?.let { tr(com.annales.journal.data.Moods.names[it]) + (day.feels.takeIf { f -> f.isNotEmpty() }?.let { f -> " · " + f.take(2).joinToString(", ") { s -> tr(s) } } ?: "") }
                    ?: tr(if (today) "How did today feel?" else "How did it feel?"),
                done = mood != null,
            ) { sheet = "mood" }
            StepLine()
            Step({ Glyph("✎") }, tr("One line"), day.line.ifBlank { tr("A line about the day") }, done = day.line.isNotBlank(), italic = day.line.isNotBlank()) { sheet = "line" }
            StepLine()
            Step({ Glyph("✉") }, tr("The postmark"), postmarkBits(model, day).joinToString("  ·  ").ifBlank { tr("Ink, weather, music, money") }, done = true) { sheet = "postmark" }
        }

        SealButton(model, day, today)
        if (today) OnThisDay(model, date, onOpenPast)
    }

    sheet?.let { which ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = K.bg, contentColor = K.ink,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(bottom = 24.dp)) {
                when (which) {
                    "mood" -> {
                        SheetTitle(tr("State of mind"), tr(if (today) "how you felt overall today" else "how you felt overall that day"))
                        MoodHorizon(day.moodV ?: day.mood?.let { it * 100 / 6 }, enabled = true, onMood = { model.setMoodV(date, it) })
                        if (day.mood != null) Feelings(model, day)
                    }
                    "line" -> {
                        SheetTitle(tr("One line"), tr("up to 200 characters"))
                        OneLine(model, day, today)
                    }
                    else -> {
                        SheetTitle(tr("The postmark"), tr(if (day.sealed) "as it was stamped" else "filled in for you"))
                        Sec(tr("Ink"), tr(model.inkOn(date).label))
                        Inks(model, day)
                        Spacer(Modifier.height(10.dp))
                        PostmarkCard(model, day)
                        if (day.sealed && !today) Text(
                            "🔒  " + tr("The recorded postmark stays as it was on {date}: the office, date and hour it actually said. You can still change its ink, stamps and words.", "date" to capDate(date)),
                            style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        )
                    }
                }
                Button(
                    onClick = { sheet = null }, shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = K.ink, contentColor = K.surface),
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 18.dp).fillMaxWidth().height(50.dp),
                ) { Text(tr("Done"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            }
        }
    }
}

/** The short version of the postmark, for the row on the day page and the back of a stamp: weather, music, money, tone. */
internal fun postmarkBits(model: AnnalesModel, day: Day): List<String> {
    val x = day.extras
    return listOfNotNull(
        x["wx.code"]?.toIntOrNull()?.let { c -> "${SKY_GLYPH[com.annales.journal.data.Siblings.describe(c).second] ?: "☁"} ${x["wx.hi"]}°" },
        x["music.minutes"]?.let { "♪ " + tr("{n} min", "n" to it) },
        x["money.spent"]?.let { "¤ $it" },
        x["augur.tone"]?.let { "✶ $it" + (x["augur.number"]?.let { n -> " №$n" } ?: "") },
    )
}

@Composable
private fun Step(lead: @Composable () -> Unit, label: String, value: String, done: Boolean, italic: Boolean = false, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { lead() }
    Column(Modifier.weight(1f).padding(start = 14.dp)) {
        Text(I18n.upper(label), style = KickerStyle)
        Text(
            value, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
            style = if (italic) TextStyle(fontFamily = K.serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, color = K.ink)
            else BodyStyle.copy(fontSize = 16.sp, color = if (done) K.ink else K.muted),
        )
    }
    Text("›", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = K.muted), modifier = Modifier.padding(start = 10.dp))
}

@Composable
private fun StepLine() = Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(K.line))

@Composable
private fun Glyph(g: String) = Text(g, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = K.acc))

@Composable
private fun Quiet(label: String, warn: Boolean = false, onClick: () -> Unit) = androidx.compose.material3.TextButton(onClick = onClick) {
    Text(label, style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (warn) Color(0xFFB4483A) else K.acc))
}

@Composable
private fun SheetTitle(title: String, sub: String) = Column(Modifier.padding(start = 22.dp, end = 18.dp, bottom = 12.dp)) {
    Text(title, style = TitleStyle.copy(fontSize = 26.sp, lineHeight = 30.sp))
    Text(sub, style = SmallStyle, modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun Badge(label: String, filled: Boolean) = Text(
    label,
    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (filled) K.surface else K.ink),
    modifier = Modifier.background(if (filled) K.ink else Color.Transparent, RoundedCornerShape(99.dp))
        .border(1.dp, if (filled) K.ink else K.line, RoundedCornerShape(99.dp)).padding(horizontal = 11.dp, vertical = 7.dp),
)

@Composable
private fun Sec(left: String, right: String = "") = Row(
    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Text(I18n.upper(left), style = KickerStyle.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
    if (right.isNotBlank()) Text(right, style = SmallStyle.copy(fontSize = 12.5.sp))
}

/** The day of the year as a ring filling up, like the mockup's corner badge. */
@Composable
private fun YearRing(date: LocalDate) {
    val total = date.lengthOfYear()
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(58.dp)) {
            val sw = 3.5.dp.toPx(); val inset = sw / 2 + 2.dp.toPx()
            val tl = Offset(inset, inset); val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(K.line, 0f, 360f, false, tl, sz, style = Stroke(sw))
            drawArc(K.acc, -90f, 360f * date.dayOfYear / total, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("${date.dayOfYear}", style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 18.sp, color = K.acc))
        Text("OF $total", style = KickerStyle.copy(fontSize = 9.5.sp), modifier = Modifier.align(Alignment.BottomCenter).offset(y = 12.dp))
    }
}


/** The envelope: the selected stamp on paper, the region pill, and the №/date caption with its title. */
@Composable
private fun Envelope(model: AnnalesModel, day: Day, st: Stamp?, onAdd: () -> Unit, onOpen: () -> Unit) {
    val ink = model.inkOn(day.date)
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(268.dp).clip(RoundedCornerShape(20.dp))
            .background(K.page).border(1.dp, K.line, RoundedCornerShape(20.dp)).paperGrain(),
        contentAlignment = Alignment.Center,
    ) {
        if (st != null) {
            Box(Modifier.clickable(onClick = onOpen).padding(bottom = 16.dp)) {
                StampView(model, st.id, day.date.dayOfYear, day.mood, 160.dp, rotation = if (day.sealed) -1.5f else 0f)
            }
        } else {
            Box(
                Modifier.padding(bottom = 16.dp).size(150.dp, 188.dp).clickable(onClick = onAdd).dashed(K.muted),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("＋", style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 30.sp, color = K.ink))
                    Text(tr("Add a stamp"), style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                    Text(tr("a photo, or the day's sky"), style = SmallStyle)
                }
            }
        }
        val cap = if (day.sealed) "№${day.date.dayOfYear} · ${capDate(day.date)}" else "№${day.date.dayOfYear} · ${tr("not sealed yet")}"
        val t = day.line.ifBlank { null }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp).width(190.dp)) {
            Text(I18n.upper(cap), style = KickerStyle.copy(fontSize = 11.5.sp, letterSpacing = 1.3.sp))
            if (t != null) Text(t, style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = K.ink),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 11.dp).background(K.surface, RoundedCornerShape(99.dp))
                .border(1.dp, K.line, RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(Color(ink.argb), CircleShape))
            Spacer(Modifier.size(5.dp))
            Text(I18n.upper(tr(regionOf(model, day.date))), style = KickerStyle.copy(fontSize = 11.sp, letterSpacing = 1.1.sp))
        }
    }
}

/** A faint speckle over the envelope, standing in for the mockup's paper grain. */
fun Modifier.paperGrain() = drawBehind {
    val r = kotlin.random.Random(11)
    repeat(260) {
        drawCircle(Color(0xFF3C230A).copy(alpha = .035f + r.nextFloat() * .04f), .6f + r.nextFloat() * 1.4f,
            Offset(r.nextFloat() * size.width, r.nextFloat() * size.height))
    }
}

fun Modifier.dashed(c: Color) = drawBehind {
    drawRoundRect(c, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
}

/** The three slots under the envelope, and what to do next. */
@Composable
private fun Strip(model: AnnalesModel, day: Day, sel: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val n = day.stamps.size
    Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        day.stamps.forEachIndexed { i, s ->
            Box(
                Modifier.size(62.dp, 72.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (i == sel) K.accSoft else Color.Transparent)
                    .border(1.5.dp, if (i == sel) K.acc else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { StampView(model, s.id, day.date.dayOfYear, day.mood, 40.dp) }
        }
        if (day.canAdd) Box(
            Modifier.size(62.dp, 72.dp).clickable(onClick = onAdd).dashed(K.line), contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("＋", style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 20.sp, color = K.ink))
                Text(tr(if (n > 0) "Add" else "Add a stamp"), style = SmallStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(tn(n, "{n} of 3 stamps", "{n} of 3 stamps"), style = BodyStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold))
            Text(tr(if (n > 0) "Tap one to change it" else "No photo? The day's sky becomes the stamp"), style = SmallStyle.copy(fontSize = 12.5.sp))
        }
    }
}

@Composable
private fun Act(label: String, warn: Boolean = false, onClick: () -> Unit) = OutlinedButton(
    onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    border = BorderStroke(1.dp, K.line),
) { Text(label, color = if (warn) Color(0xFFB4483A) else K.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }

/** A tag chip as in the mockup: outlined, filled with ink when chosen. */
@Composable
private fun Tag(label: String, on: Boolean, onClick: () -> Unit) = Text(
    label,
    style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (on) K.surface else K.ink),
    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) K.ink else K.surface.copy(alpha = .8f))
        .border(1.dp, if (on) K.ink else K.line, RoundedCornerShape(99.dp)).clickable(onClick = onClick)
        .padding(horizontal = 11.dp, vertical = 8.dp),
)

/** The two questions under the horizon: feelings first (ten, then "Show more"), then what had the biggest impact. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Feelings(model: AnnalesModel, day: Day) {
    val mood = day.mood ?: return
    var more by remember { mutableStateOf(false) }
    val fs = Tags.feelsFor(mood)
    val list = (if (more) fs else fs.take(10)) + day.feels.filter { it !in fs }
    Column(Modifier.padding(horizontal = 18.dp)) {
        Text(tr("What best describes this feeling?"), style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 16.dp, bottom = 9.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { f -> Tag(tr(f), f in day.feels) { model.toggleFeel(day.date, f) } }
            if (fs.size > 10) Text(
                tr(if (more) "Show less" else "Show more"),
                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = K.acc),
                modifier = Modifier.clickable { more = !more }.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
        Text(tr("What's having the biggest impact?"), style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 16.dp, bottom = 9.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tags.factors.forEach { f -> Tag(tr(f), f in day.factors) { model.toggleFactor(day.date, f) } }
        }
    }
}

/** "One line": three prompts to start from, and up to 200 characters in italic serif. */
@Composable
private fun OneLine(model: AnnalesModel, day: Day, today: Boolean) {
    var hint by remember(day.date) { mutableStateOf(if (today) "Leaves everywhere, tea at Ola's…" else "What do you remember about this day?") }
    var text by remember(day.date) { mutableStateOf(day.line) }
    val start = day.date.dayOfMonth % 3
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PROMPTS.subList(start, start + 3).forEach { p ->
            Text(
                tr(p), style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = K.muted),
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(K.surface).border(1.dp, K.line, RoundedCornerShape(99.dp))
                    .clickable { hint = p }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(K.surface, RoundedCornerShape(18.dp)).padding(16.dp)) {
        val style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, color = K.ink)
        BasicTextField(
            text, { if (it.length <= 200) { text = it; model.setLine(day.date, it) } },
            textStyle = style, cursorBrush = SolidColor(K.acc), minLines = 2,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> Box { if (text.isEmpty()) Text(tr(hint), style = style.copy(color = K.muted.copy(alpha = .75f))); inner() } },
        )
        Text("${text.length} / 200", style = SmallStyle.copy(fontSize = 12.sp), modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
    }
}

/** "Official · Poczta Polska" (or the trip's post office), then the two reds. */
@Composable
private fun Inks(model: AnnalesModel, day: Day) {
    val post = model.tripOn(day.date)?.let { Places.of(it.place).post } ?: "Poczta Polska"
    val cur = model.inkOn(day.date)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ink.entries.forEach { ink ->
            InkChip(ink, ink == cur, label = if (ink == Ink.OFFICIAL) "${tr("Official")} · $post" else tr(ink.label)) { model.setDayInk(day.date, ink) }
        }
    }
    Text(
        tr(if (cur == Ink.OFFICIAL) "Black, the way the post office itself would stamp it." else "Your own touch: the post office would use black."),
        style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

/** What the postmark says: the office and date bridge, and the moon that night. */
@Composable
private fun PostmarkCard(model: AnnalesModel, day: Day) {
    val (mark, bottom, _) = model.postmarkFor(day.date)
    val office = if (day.sealed) day.office.ifBlank { mark } else mark
    val under = if (day.sealed) day.bottom.ifBlank { bottom } else bottom
    val hour = if (day.sealed && day.sealedAt > 0)
        java.time.Instant.ofEpochMilli(day.sealedAt).atZone(java.time.ZoneId.systemDefault()).hour else null
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(K.surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row1("✉", "$office${if (under.isNotBlank()) " · $under" else ""}",
            "%02d.%02d.%02d".format(day.date.dayOfMonth, day.date.monthValue, day.date.year % 100) + " · " + (hour?.let { tr("hour {n}", "n" to it) } ?: tr("the hour you seal it")),
            tr(if (model.tripOn(day.date) != null) "Trip" else "Profile"))
        Box(Modifier.fillMaxWidth().height(1.dp).background(K.line))
        Row1("☾", "${tr(Moon.name(day.date))} · ${tr("{n}% lit", "n" to (Moon.lit(day.date) * 100).roundToInt())}", tr("Calculated, exact"), "Caelum")
        val x = day.extras
        Box(Modifier.fillMaxWidth().height(1.dp).background(K.line))
        val code = x["wx.code"]?.toIntOrNull()
        if (code != null) {
            val agree = mapOf("agree" to "models agree", "roughly" to "models roughly aligned", "diverge" to "models diverge")[x["wx.agree"]]?.let { tr(it) } ?: ""
            val models = x["wx.models"]?.toIntOrNull() ?: 0
            Row1(SKY_GLYPH[com.annales.journal.data.Siblings.describe(code).second] ?: "☁",
                "${tr(com.annales.journal.data.Siblings.describe(code).first)} · ${x["wx.hi"]}° / ${x["wx.lo"]}°",
                "${tn(models, "{n} model", "{n} models")} · $agree", "Consensus")
        } else Row1("☁", tr("Weather"), tr(if (java.time.temporal.ChronoUnit.DAYS.between(day.date, LocalDate.now()) > 90) "Only kept for the last three months" else "Needs a connection to fetch"), "Consensus", faded = true)
        Box(Modifier.fillMaxWidth().height(1.dp).background(K.line))
        if (x["music.track"] != null) Row1("♪", "${x["music.track"]}${x["music.artist"]?.let { " · $it" } ?: ""}",
            tr("{n} min listened", "n" to (x["music.minutes"] ?: "0")), "Fidelis")
        else if (x["music.minutes"] != null) Row1("♪", tr("{n} min of music", "n" to x["music.minutes"]), tr("no track played through yet"), "Fidelis")
        else Row1("♪", tr("Top track"), sourceNote(model, com.annales.journal.data.Siblings.FIDELIS), "Fidelis", faded = true)
        Box(Modifier.fillMaxWidth().height(1.dp).background(K.line))
        if (x["money.spent"] != null) Row1("¤", tr("{amount} spent", "amount" to x["money.spent"]), x["money.safe"]?.let { tr("safe to spend {amount} a day", "amount" to it) } ?: tr("safe to spend wasn't noted that day"), "Kalendae")
        else Row1("¤", tr("Spent vs safe-to-spend"), sourceNote(model, com.annales.journal.data.Siblings.KALENDAE), "Kalendae", faded = true)
        Box(Modifier.fillMaxWidth().height(1.dp).background(K.line))
        if (x["augur.tone"] != null) Row1(
            "✶", x["augur.tone"]!! + (x["augur.number"]?.let { " · №$it" } ?: ""),
            listOfNotNull(x["augur.colour"], x["augur.agree"]?.let { tr("{n} systems agree", "n" to it) }).joinToString(" · ").ifBlank { tr("where the systems agree") },
            "Augur", swatches = augurColours(x),
        )
        else Row1("✶", tr("The day's tone"), sourceNote(model, com.annales.journal.data.Siblings.AUGUR), "Augur", faded = true)
    }
}

@Composable
private fun Row1(ic: String, b: String, s: String, src: String, faded: Boolean = false, swatches: List<Color> = emptyList()) = Row(
    Modifier.fillMaxWidth().padding(vertical = 10.dp).alpha(if (faded) .72f else 1f), verticalAlignment = Alignment.CenterVertically,
) {
    Text(ic, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = K.acc), modifier = Modifier.width(30.dp))
    Column(Modifier.weight(1f)) {
        Text(b, style = BodyStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold))
        Row(verticalAlignment = Alignment.CenterVertically) {
            swatches.forEach { Box(Modifier.padding(end = 5.dp).size(11.dp).background(it, CircleShape).border(1.dp, K.line, CircleShape)) }
            Text(s, style = SmallStyle.copy(fontSize = 12.5.sp))
        }
    }
    Text(I18n.upper(src), style = KickerStyle.copy(fontSize = 10.5.sp, letterSpacing = 1.sp))
}

/** The mockup's dark button: it says what's missing until the day can be sealed, and offers re-sealing after edits. */
@Composable
private fun SealButton(model: AnnalesModel, day: Day, today: Boolean) {
    val label = tr(when {
        model.busy -> "Sealing…"
        day.stamps.isEmpty() -> "Add a stamp to seal"
        day.mood == null -> if (today) "Choose your state of mind to seal" else "Choose a state of mind to seal"
        !day.sealed -> if (today) "Seal today" else "Seal this day"
        day.dirty -> "Save & re-seal"
        else -> if (today) "Sealed ✓ · change anything to edit" else "Sealed ✓ · no changes yet"
    })
    val ready = !model.busy && day.stamps.isNotEmpty() && day.mood != null && (!day.sealed || day.dirty)
    val done = day.sealed && !day.dirty
    Button(
        onClick = { model.seal(day.date) }, enabled = ready,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = K.ink, contentColor = K.surface,
            disabledContainerColor = if (done) K.accSoft else K.ink.copy(alpha = .32f), disabledContentColor = if (done) K.muted else K.surface,
        ),
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 18.dp).fillMaxWidth().height(54.dp),
    ) { Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
    if (ready && model.saveToPhotos) Text(
        tr(if (day.sealed) "The copies in Photos › Annales are updated too." else "Each stamp goes into Photos › Annales as you seal it."),
        style = SmallStyle, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
fun InkChip(ink: Ink, selected: Boolean, label: String = tr(ink.label), onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(ink.argb) else K.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) K.accSoft else Color.Transparent),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(12.dp).background(Color(ink.argb), CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(label, color = K.ink, fontSize = 14.sp)
    }
}

/** The same date in an earlier year: its first stamp, how it felt, and its one line. */
@Composable
private fun OnThisDay(model: AnnalesModel, date: LocalDate, onOpen: (LocalDate, Stamp) -> Unit) {
    val past = model.onThisDay(date).firstOrNull() ?: return
    val s = past.stamps.first()
    val years = date.year - past.date.year
    Sec(tr("On this day"), capDate(past.date))
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(K.page).paperGrain()
            .clickable { onOpen(past.date, s) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StampView(model, s.id, past.date.dayOfYear, past.mood, 72.dp, rotation = -2f)
        Spacer(Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text((if (years == 1) tr("A year ago") else tn(years, "{n} year ago", "{n} years ago")) + (past.mood?.let { " · ${tr(com.annales.journal.data.Moods.names[it])}" } ?: ""), style = SmallStyle)
            Text(
                past.line.ifBlank { I18n.date(past.date, "EEEE d MMMM yyyy") },
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 18.sp, color = K.ink), maxLines = 2,
            )
            if (past.stamps.size > 1) Text(tn(past.stamps.size, "{n} stamp that day", "{n} stamps that day"), style = SmallStyle)
        }
    }
}

private val SKY_GLYPH = com.annales.journal.render.SkyStamp.Weather.entries.associateWith { it.glyph }

/** Why a sibling's row is empty: not installed, or installed but not sharing yet. */
private fun sourceNote(model: AnnalesModel, app: com.annales.journal.data.Siblings.App) =
    when {
        !model.installed(app) -> tr("{app} isn't installed", "app" to app.name.substringBefore(" "))
        !model.canShare(app) -> tr("Update {app} to share", "app" to app.name.substringBefore(" "))
        else -> tr("Nothing shared for this day yet")
    }

/** Augur's colour(s) of the day: augur.colourHex, one hex or several separated by commas. */
internal fun augurColours(x: Map<String, String>): List<Color> =
    x["augur.colourHex"].orEmpty().split(",").mapNotNull { h ->
        runCatching { Color(android.graphics.Color.parseColor(h.trim())) }.getOrNull()
    }.take(3)
