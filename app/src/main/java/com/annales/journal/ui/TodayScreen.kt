package com.annales.journal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.Day
import com.annales.journal.data.Ink
import com.annales.journal.data.Stamp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

/** The day's page: Today, or any past day opened from the Journal. */
@Composable
fun DayPage(
    model: AnnalesModel, date: LocalDate,
    onAdd: () -> Unit, onOpen: (Stamp) -> Unit, modifier: Modifier = Modifier,
    onOpenPast: (LocalDate, Stamp) -> Unit = { _, _ -> },
) {
    val day = model.day(date)
    val today = date == LocalDate.now()
    val name = if (today) "Today, " else date.format(DateTimeFormatter.ofPattern("d MMMM, ", Locale.ENGLISH))
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header(
            "${date.format(longDate)} · №${date.dayOfYear}",
            title(name, if (day.sealed) "sealed" else "not sealed yet"),
        )
        Stamps(model, day, onAdd, onOpen)
        Spacer(Modifier.height(18.dp))
        MoodHorizon(day.mood, enabled = true, onMood = { model.setMood(date, it) })
        if (day.mood != null) DayTags(model, day)
        Text(
            "${prettyOffice(day.office.ifBlank { model.postmarkFor(date).first })} · №${date.dayOfYear} · " +
                if (day.sealed) "sealed" else "not sealed yet",
            style = SmallStyle, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
        )
        if (!day.sealed && day.stamps.isNotEmpty()) SealBox(model, day)
        if (day.stamps.isEmpty()) Text(
            "Up to three stamps a day, cut from your photos. Seal the day and each one gets a postmark and goes into Photos.",
            style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
        if (today) OnThisDay(model, date, onOpenPast)
    }
}

@Composable
private fun Stamps(model: AnnalesModel, day: Day, onAdd: () -> Unit, onOpen: (Stamp) -> Unit) {
    val n = day.stamps.size + if (day.canAdd) 1 else 0
    val w: Dp = when (n) { 1 -> 210.dp; 2 -> 146.dp; else -> 94.dp } // three must fit a 360 dp phone
    val tilt = listOf(-2.5f, 1.8f, -1f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        day.stamps.forEachIndexed { i, s ->
            Box {
                StampView(
                    model, s.id, day.date.dayOfYear, day.mood, w,
                    rotation = if (day.stamps.size == 1) -1.5f else tilt[i],
                    modifier = Modifier.clickable { onOpen(s) },
                )
                if (!day.sealed) Box(
                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(24.dp)
                        .background(Color(0xCC1F1A16), CircleShape)
                        .clickable { model.removeStamp(day.date, s) },
                    contentAlignment = Alignment.Center,
                ) { Text("×", color = Color.White, fontSize = 15.sp) }
            }
        }
        if (day.canAdd) {
            Box(
                Modifier.size(w, w * 1.25f).clickable(onClick = onAdd).drawBehind {
                    drawRoundRect(
                        K.muted, cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("＋", style = TextStyle(fontSize = 26.sp, color = K.muted))
                    Text(if (day.stamps.isEmpty()) "Add a stamp" else "Another", style = SmallStyle)
                    Text("${Day.MAX_STAMPS - day.stamps.size} left", style = SmallStyle.copy(fontSize = 10.sp))
                }
            }
        }
    }
}

/** "GLIWICE 1" → "Gliwice 1", "KRAKÓW 1" → "Kraków 1". */
fun prettyOffice(mark: String) = mark.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

@Composable
private fun SealBox(model: AnnalesModel, day: Day) {
    val trip = model.tripOn(day.date)
    val (mark, bottom, sealInk) = model.postmarkFor(day.date)
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .background(K.surface, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        if (trip != null) {
            Text("ON A TRIP · ${trip.name.uppercase()}", style = KickerStyle)
            Text(
                "Stamped ${mark}${if (bottom.isNotBlank()) " · $bottom" else ""}, in ${sealInk.label.lowercase()} ink. Change it in the trip's settings.",
                style = SmallStyle, modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Text("Postmark ink", style = KickerStyle)
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ink.entries.forEach { ink -> InkChip(ink, ink == model.ink) { model.changeInk(ink) } }
            }
            Text(
                if (model.ink == Ink.OFFICIAL) "Black, the way the post office in ${prettyOffice(model.office)} would stamp it."
                else "Your own touch: the post office would use black.",
                style = SmallStyle,
            )
        }
        Button(
            onClick = { model.seal(day.date) }, enabled = !model.busy,
            colors = ButtonDefaults.buttonColors(containerColor = K.acc),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
        ) { Text(if (model.busy) "Sealing…" else "Seal the day", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        if (model.saveToPhotos) Text(
            "Each stamp goes into Photos › Annales as you seal it.",
            style = SmallStyle, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
fun InkChip(ink: Ink, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(ink.argb) else K.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) K.accSoft else Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(12.dp).background(Color(ink.argb), CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(ink.label, color = K.ink, fontSize = 13.sp)
    }
}

/** What shaped the day and how it felt. Feelings follow the mood; ones you tagged under another mood stay visible. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DayTags(model: AnnalesModel, day: Day) {
    val mood = day.mood ?: return
    val feels = (com.annales.journal.data.Tags.feelsFor(mood) + day.feels).distinct()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("WHAT SHAPED IT", style = KickerStyle, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            com.annales.journal.data.Tags.factors.forEach { f -> Chip(f, f in day.factors) { model.toggleFactor(day.date, f) } }
        }
        Text("HOW IT FELT", style = KickerStyle, modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            feels.forEach { f -> Chip(f, f in day.feels) { model.toggleFeel(day.date, f) } }
        }
    }
}

/** The same date in an earlier year: its first stamp, how it felt, and its title. */
@Composable
private fun OnThisDay(model: AnnalesModel, date: LocalDate, onOpen: (LocalDate, Stamp) -> Unit) {
    val past = model.onThisDay(date).firstOrNull() ?: return
    val s = past.stamps.first()
    val years = date.year - past.date.year
    Text("ON THIS DAY", style = KickerStyle, modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp))
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(K.page, RoundedCornerShape(18.dp))
            .clickable { onOpen(past.date, s) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StampView(model, s.id, past.date.dayOfYear, past.mood, 72.dp, rotation = -2f)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (years == 1) "A year ago" else "$years years ago") +
                    (past.mood?.let { " · ${com.annales.journal.data.Moods.names[it]}" } ?: ""),
                style = SmallStyle,
            )
            Text(
                s.title.ifBlank { s.note.ifBlank { past.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)) } },
                style = TextStyle(fontFamily = K.serif, fontSize = 18.sp, color = K.ink), maxLines = 2,
            )
            if (past.stamps.size > 1) Text("${past.stamps.size} stamps that day", style = SmallStyle)
        }
    }
}
