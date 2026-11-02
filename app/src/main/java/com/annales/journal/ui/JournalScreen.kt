package com.annales.journal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The journal as a calendar: each day shows its first stamp, tap a day to open it. */
@Composable
fun JournalScreen(model: AnnalesModel, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    val count = model.days.values.sumOf { it.stamps.size }
    val sealed = model.days.values.count { it.sealed }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header("$count stamp${if (count == 1) "" else "s"} · $sealed day${if (sealed == 1) "" else "s"} sealed", title("The ", "journal"))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = ym.minusMonths(1).toString() }) { Text("‹", fontSize = 22.sp, color = K.ink) }
            Text(
                ym.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ENGLISH)),
                style = TextStyle(fontFamily = K.serif, fontSize = 20.sp, color = K.ink),
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(onClick = { if (ym < YearMonth.now()) month = ym.plusMonths(1).toString() }) {
                Text("›", fontSize = 22.sp, color = if (ym < YearMonth.now()) K.ink else K.line)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = SmallStyle, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val first = ym.atDay(1)
        val lead = first.dayOfWeek.value - 1 // Monday first
        val cells = lead + ym.lengthOfMonth()
        val today = LocalDate.now()
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 7) {
                        val n = row * 7 + col - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(.8f)) {
                            if (n in 1..ym.lengthOfMonth()) DayCell(model, ym.atDay(n), today, onOpenDay)
                        }
                    }
                }
            }
        }
        Text(
            "Tap any day to add stamps or seal it. Future days open when they come.",
            style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun DayCell(model: AnnalesModel, date: LocalDate, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val day = model.days[date]
    val future = date.isAfter(today)
    val st = day?.stamps?.firstOrNull()
    Box(
        Modifier.fillMaxWidth().aspectRatio(.8f)
            .background(if (date == today) K.accSoft else K.surface.copy(alpha = if (future) .35f else .7f), RoundedCornerShape(8.dp))
            .then(if (future) Modifier else Modifier.clickable { onOpenDay(date) }),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null && st != null) {
            StampView(model, st.id, date.dayOfYear, day.mood, 40.dp, rotation = if (date.dayOfMonth % 2 == 0) -2f else 2f)
            if (day.stamps.size > 1) Text(
                "${day.stamps.size}", style = TextStyle(fontSize = 9.sp, color = Color.White),
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).background(K.acc, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp),
            )
        } else {
            Text("${date.dayOfMonth}", style = TextStyle(fontSize = 13.sp, color = if (future) K.line else K.muted))
        }
    }
}
