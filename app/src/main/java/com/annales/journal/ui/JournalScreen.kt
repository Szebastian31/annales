package com.annales.journal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.CollType
import com.annales.journal.data.Day
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")

/** The journal, as in the mockup: a month calendar of sealed days, or "Pages", one page per month. */
@Composable
fun JournalScreen(model: AnnalesModel, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var pages by rememberSaveable { mutableStateOf(false) }
    val sealed = model.days.values.filter { it.sealed }
    val count = sealed.sumOf { it.stamps.size }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header("$count stamp${if (count == 1) "" else "s"} · ${sealed.size} day${if (sealed.size == 1) "" else "s"}", title("The ", "journal"))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(99.dp)).background(K.surface).border(1.dp, K.line, RoundedCornerShape(99.dp)).padding(3.dp)
        ) {
            listOf(false to "Calendar", true to "Pages").forEach { (p, n) ->
                Text(
                    n, textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (pages == p) K.surface else K.muted),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(99.dp)).background(if (pages == p) K.ink else Color.Transparent)
                        .clickable { pages = p }.padding(vertical = 8.dp),
                )
            }
        }
        if (pages) Pages(model, onOpenDay) else Calendar(model, onOpenDay)
    }
}

@Composable
private fun Calendar(model: AnnalesModel, onOpenDay: (LocalDate) -> Unit) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    val sealedN = model.days.values.count { it.sealed && YearMonth.from(it.date) == ym }
    val first = model.since.let { YearMonth.from(it) }.let { if (it > ym) ym else it }

    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("%02d".format(sealedN), style = TextStyle(fontWeight = FontWeight.Medium, fontFamily = K.serif, fontSize = 44.sp, lineHeight = 44.sp, color = K.ink))
                Text("DAYS · ${ym.year}", style = KickerStyle.copy(fontSize = 9.sp), modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            }
            Text(ym.format(DateTimeFormatter.ofPattern("LLLL", Locale.ENGLISH)), style = TextStyle(fontWeight = FontWeight.Medium, fontFamily = K.serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = K.acc))
        }
        Nav("‹", ym > first) { month = ym.minusMonths(1).toString() }
        Spacer(Modifier.size(6.dp))
        Nav("›", ym < YearMonth.now()) { month = ym.plusMonths(1).toString() }
    }

    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(K.surface, RoundedCornerShape(18.dp)).padding(horizontal = 8.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                Text(it.uppercase(), style = KickerStyle.copy(fontSize = 8.5.sp, letterSpacing = .8.sp), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        val lead = ym.atDay(1).dayOfWeek.value % 7 // Sunday first, as in the mockup
        val n = ym.lengthOfMonth()
        val tail = (7 - (lead + n) % 7) % 7
        val prev = ym.minusMonths(1)
        val cells = (lead - 1 downTo 0).map { prev.atDay(prev.lengthOfMonth() - it) to false } +
            (1..n).map { ym.atDay(it) to true } + (1..tail).map { ym.plusMonths(1).atDay(it) to false }
        val today = LocalDate.now()
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (date, inMonth) ->
                        Box(Modifier.weight(1f).aspectRatio(.82f)) { Cell(model, date, inMonth, today, onOpenDay) }
                    }
                }
            }
        }
    }
    Text(
        "Coloured bars mark trips and events, in their postmark ink. Tap any day to open its stamps.",
        style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
    )
}

@Composable
private fun Nav(glyph: String, on: Boolean, onClick: () -> Unit) = Box(
    Modifier.size(36.dp).clip(RoundedCornerShape(99.dp)).background(K.surface).border(1.dp, K.line, RoundedCornerShape(99.dp))
        .clickable(enabled = on, onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(glyph, fontSize = 20.sp, color = if (on) K.ink else K.line) }

@Composable
private fun Cell(model: AnnalesModel, date: LocalDate, inMonth: Boolean, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val d = model.days[date]
    val future = date.isAfter(today)
    val bar = model.collections.firstOrNull { (it.type == CollType.TRIP || it.type == CollType.EVENT) && it.covers(date) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(.82f).clip(RoundedCornerShape(8.dp))
            .background(if (date == today) K.accSoft else Color.Transparent)
            .then(if (date == today) Modifier.border(1.5.dp, K.acc, RoundedCornerShape(8.dp)) else Modifier)
            .then(if (!inMonth || future) Modifier else Modifier.clickable { onOpenDay(date) }),
        contentAlignment = Alignment.Center,
    ) {
        val st = d?.stamps?.firstOrNull()
        if (inMonth && d != null && d.sealed && st != null) {
            StampView(model, st.id, date.dayOfYear, d.mood, 30.dp)
            if (d.stamps.size > 1) Text(
                "${d.stamps.size}", style = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = K.onAcc),
                modifier = Modifier.align(Alignment.TopEnd).padding(1.dp).background(K.acc, RoundedCornerShape(99.dp)).padding(horizontal = 4.dp),
            )
        } else {
            val quiet = d != null && !d.sealed
            Text(
                "${date.dayOfMonth}",
                style = TextStyle(fontWeight = FontWeight.Medium, fontFamily = K.serif, fontSize = 14.sp, color = when { !inMonth || future -> K.line; quiet -> K.ink; else -> K.muted }),
            )
            if (quiet && inMonth) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp).size(4.dp).background(K.muted, RoundedCornerShape(99.dp)))
        }
        if (bar != null && inMonth) Box(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp, vertical = 2.dp).fillMaxWidth().height(3.dp)
                .background(Color(inkOf(bar).argb), RoundedCornerShape(99.dp))
        )
    }
}

/** One page per month, newest first: its sealed days as little stacks, and the quiet ones as dashed gaps. */
@Composable
private fun Pages(model: AnnalesModel, onOpenDay: (LocalDate) -> Unit) {
    val byMonth = model.days.values.filter { it.stamps.isNotEmpty() || it.mood != null }.groupBy { YearMonth.from(it.date) }
    if (byMonth.isEmpty()) {
        Text("Your pages fill up as you seal days.", style = SmallStyle, modifier = Modifier.padding(22.dp))
        return
    }
    Column(Modifier.padding(top = 16.dp)) {
        byMonth.keys.sortedDescending().forEach { ym ->
            val arr = byMonth.getValue(ym).sortedByDescending { it.date }
            val ss = arr.filter { it.sealed }
            val n = ss.sumOf { it.stamps.size }
            val quiet = arr.size - ss.size
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 20.dp, bottomEnd = 20.dp))
                    .background(K.page)
                    .background(Brush.horizontalGradient(listOf(Color(0x0F000000), Color.Transparent), endX = 40f))
                    .border(1.dp, K.line, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 20.dp, bottomEnd = 20.dp))
                    .paperGrain()
                    .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(ym.format(DateTimeFormatter.ofPattern("LLLL", Locale.ENGLISH)), style = TextStyle(fontWeight = FontWeight.Medium, fontFamily = K.serif, fontSize = 24.sp, color = K.ink))
                    Text(ROMAN[ym.monthValue - 1], style = TextStyle(fontWeight = FontWeight.Medium, fontFamily = K.serif, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = K.acc),
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                }
                Text("$n stamps over ${ss.size} days${if (quiet > 0) " · $quiet quiet" else ""}", style = SmallStyle, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                arr.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { d -> Box(Modifier.weight(1f).clickable { onOpenDay(d.date) }, contentAlignment = Alignment.Center) { Stack(model, d) } }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** A sealed day as a small pile: its first stamp, with paper edges behind it for each extra one. */
@Composable
private fun Stack(model: AnnalesModel, d: Day) {
    val st = d.stamps.firstOrNull()
    if (!d.sealed || st == null) {
        Box(Modifier.size(56.dp, 68.dp).dashed(K.line), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${d.date.dayOfMonth}", style = TextStyle(fontWeight = FontWeight.Medium, fontFamily = K.serif, fontSize = 15.sp, color = K.ink.copy(alpha = .6f)))
                Text("quiet day", style = SmallStyle.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold))
            }
        }
        return
    }
    Box(Modifier.size(66.dp, 82.dp), contentAlignment = Alignment.Center) {
        if (d.stamps.size > 2) Box(Modifier.size(52.dp, 65.dp).rotate(-5f).background(K.surface, RoundedCornerShape(3.dp)).border(1.dp, K.line, RoundedCornerShape(3.dp)))
        if (d.stamps.size > 1) Box(Modifier.size(52.dp, 65.dp).rotate(6f).background(K.surface, RoundedCornerShape(3.dp)).border(1.dp, K.line, RoundedCornerShape(3.dp)))
        StampView(model, st.id, d.date.dayOfYear, d.mood, 52.dp, rotation = if (d.date.dayOfMonth % 2 == 0) -2f else 2f)
    }
}
