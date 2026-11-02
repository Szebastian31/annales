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
        Text(
            "${day.office.ifBlank { model.office }.lowercase().replaceFirstChar { it.titlecase() }} · №${date.dayOfYear} · " +
                if (day.sealed) "sealed" else "not sealed yet",
            style = SmallStyle, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
        )
        if (!day.sealed && day.stamps.isNotEmpty()) SealBox(model, day)
        if (day.stamps.isEmpty()) Text(
            "Up to three stamps a day, cut from your photos. Seal the day and each one gets a postmark and goes into Photos.",
            style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Stamps(model: AnnalesModel, day: Day, onAdd: () -> Unit, onOpen: (Stamp) -> Unit) {
    val n = day.stamps.size + if (day.canAdd) 1 else 0
    val w: Dp = when (n) { 1 -> 210.dp; 2 -> 150.dp; else -> 104.dp }
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
                    Text("${Day.MAX_STAMPS - day.stamps.size} left today", style = SmallStyle.copy(fontSize = 10.sp))
                }
            }
        }
    }
}

@Composable
private fun SealBox(model: AnnalesModel, day: Day) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .background(K.surface, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Text("Postmark ink", style = KickerStyle)
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ink.entries.forEach { ink -> InkChip(ink, ink == model.ink) { model.changeInk(ink) } }
        }
        Text(
            if (model.ink == Ink.OFFICIAL) "Black, the way the post office in ${model.office.lowercase().replaceFirstChar { it.titlecase() }} would stamp it."
            else "Your own touch: the post office would use black.",
            style = SmallStyle,
        )
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
