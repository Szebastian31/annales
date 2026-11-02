package com.annales.journal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.CollType
import com.annales.journal.data.Collection
import com.annales.journal.data.Ink
import com.annales.journal.data.Places
import com.annales.journal.data.Stamp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dShort = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dLong = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun rangeText(c: Collection): String = when {
    c.from == c.to -> c.from.format(dLong)
    c.from.year == c.to.year && c.from.month == c.to.month -> "${c.from.dayOfMonth} – ${c.to.format(dLong)}"
    c.from.year == c.to.year -> "${c.from.format(dShort)} – ${c.to.format(dLong)}"
    else -> "${c.from.format(dLong)} – ${c.to.format(dLong)}"
}

fun inkOf(c: Collection): Ink = c.ink?.let { runCatching { Ink.valueOf(it) }.getOrNull() } ?: Ink.OFFICIAL

fun collLines(model: AnnalesModel, c: Collection): Pair<String, String> {
    val n = model.count(c)
    val stamps = "$n stamp${if (n == 1) "" else "s"}"
    return when (c.type) {
        CollType.TRIP -> Places.of(c.place).let { "${rangeText(c)} · ${it.region}" to "$stamps · postmarked ${it.mark}" }
        CollType.EVENT -> rangeText(c) to stamps
        CollType.ANNIV -> {
            val years = model.entries(c).count { it.second.isNotEmpty() }
            val md = LocalDate.of(2024, c.month, c.day).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))
            "Every $md · $years year${if (years == 1) "" else "s"} so far" to stamps
        }
        CollType.THEME -> "$n hand-picked stamp${if (n == 1) "" else "s"}" to "Add from any stamp's page"
    }
}

@Composable
fun CollectionsScreen(model: AnnalesModel, onOpen: (Collection) -> Unit, onNew: (CollType) -> Unit, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val all = model.collections.sortedByDescending { if (it.type == CollType.THEME || it.type == CollType.ANNIV) LocalDate.MIN else it.from }
    val list = all.filter { filter == null || it.type.name == filter }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header("${all.size} collection${if (all.size == 1) "" else "s"}", title("Your ", "collections"))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("All ${all.size}", filter == null) { filter = null }
            CollType.entries.forEach { t -> Chip("${t.plural} ${all.count { it.type == t }}", filter == t.name) { filter = t.name } }
        }
        Spacer(Modifier.size(10.dp))
        if (list.isEmpty()) Text(
            if (filter == null) "Nothing here yet. A trip, a wedding, an anniversary or a theme like Coffee: make one below."
            else "No ${CollType.valueOf(filter!!).plural.lowercase()} yet.",
            style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
        list.forEach { c -> CollCard(model, c) { onOpen(c) } }
        OutlinedButton(
            onClick = { onNew(filter?.let { CollType.valueOf(it) } ?: CollType.TRIP) },
            border = BorderStroke(1.5.dp, K.muted),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text("＋ New collection", color = K.ink, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit) = Text(
    label,
    style = TextStyle(fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) K.onAcc else K.ink),
    modifier = Modifier.background(if (on) K.acc else K.surface, RoundedCornerShape(99.dp)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 7.dp),
)

@Composable
private fun CollCard(model: AnnalesModel, c: Collection, onClick: () -> Unit) {
    val cover = model.cover(c)
    val (l1, l2) = collLines(model, c)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().background(K.page, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(70.dp, 86.dp), contentAlignment = Alignment.Center) {
            if (cover != null) StampView(model, cover.second.id, cover.first.date.dayOfYear, cover.first.mood, 64.dp, rotation = -2f)
            else Ghost(c.type, "empty")
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            TypeTag(c)
            Text(c.name, style = TextStyle(fontWeight = FontWeight.Medium, fontFamily = K.serif, fontSize = 20.sp, color = K.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(l1, style = SmallStyle); Text(l2, style = SmallStyle)
        }
    }
}

@Composable
fun TypeTag(c: Collection, withInk: Boolean = false) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).background(Color(inkOf(c).argb), CircleShape))
    Spacer(Modifier.size(6.dp))
    Text(
        (c.type.label + if (withInk && c.type != CollType.THEME) " · ${inkOf(c).label} ink" else "").uppercase(),
        style = KickerStyle.copy(fontSize = 10.sp),
    )
}

@Composable
fun Ghost(type: CollType, label: String) = Box(
    Modifier.size(60.dp, 74.dp).border(1.5.dp, K.muted.copy(alpha = .5f), RoundedCornerShape(6.dp)),
    contentAlignment = Alignment.Center,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(type.glyph, style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, color = K.muted))
        Text(label, style = SmallStyle.copy(fontSize = 10.sp))
    }
}

/** One collection: a header card, then each day with its stamps, or the hand-picked grid for a theme. */
@Composable
fun CollectionScreen(
    model: AnnalesModel, c: Collection,
    onBack: () -> Unit, onEdit: () -> Unit, onOpenStamp: (LocalDate, Stamp, List<Pair<LocalDate, Stamp>>) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val entries = model.entries(c)
    val all = entries.flatMap { (d, ss) -> ss.map { d.date to it } }
    val cover = model.cover(c)
    val (l1, l2) = collLines(model, c)
    val today = LocalDate.now()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Collections", color = K.acc) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("Edit", color = K.acc, fontWeight = FontWeight.Bold) }
        }
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(K.page, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(100.dp, 124.dp), contentAlignment = Alignment.Center) {
                if (cover != null) StampView(model, cover.second.id, cover.first.date.dayOfYear, cover.first.mood, 94.dp, rotation = -2f)
                else Ghost(c.type, "cover")
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                TypeTag(c, withInk = true)
                Text(c.name, style = TextStyle(fontWeight = FontWeight.Medium, fontFamily = K.serif, fontSize = 24.sp, color = K.ink))
                Text(l1, style = SmallStyle); Text(l2, style = SmallStyle)
            }
        }
        if (c.note.isNotBlank()) Text(
            c.note, style = BodyStyle.copy(fontStyle = FontStyle.Italic, color = K.muted),
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )

        if (c.type == CollType.THEME) {
            if (all.isEmpty()) Text(
                "Empty for now. Open any stamp and tap ＋ ${c.name} to add it here.",
                style = SmallStyle, modifier = Modifier.padding(22.dp),
            ) else StampGrid(model, all) { (d, s) -> onOpenStamp(d, s, all) }
        } else entries.forEachIndexed { i, (d, ss) ->
            val label = if (c.type == CollType.ANNIV) "${d.date.year} · ${d.date.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }}"
            else "Day ${i + 1} · ${d.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}"
            Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                Text(label.uppercase(), style = KickerStyle.copy(fontSize = 10.sp))
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ss.forEach { s ->
                        StampView(model, s.id, d.date.dayOfYear, d.mood, 66.dp,
                            modifier = Modifier.clickable { onOpenStamp(d.date, s, all) })
                    }
                    when {
                        d.date.isAfter(today) -> Text("Not yet. Stamps you seal that day land here.", style = SmallStyle)
                        !d.sealed && d.stamps.isNotEmpty() -> TextButton(onClick = { onOpenDay(d.date) }) {
                            Text("Seal this day to add it here", color = K.acc, fontWeight = FontWeight.Bold)
                        }
                        !d.sealed -> TextButton(onClick = { onOpenDay(d.date) }) { Text("＋ Add a stamp", color = K.acc) }
                    }
                }
            }
        }
        if (c.type == CollType.ANNIV && entries.isEmpty()) Text(
            "Seal a day on this date and it lands here, every year.",
            style = SmallStyle, modifier = Modifier.padding(22.dp),
        )
    }
}

@Composable
fun StampGrid(model: AnnalesModel, items: List<Pair<LocalDate, Stamp>>, onOpen: (Pair<LocalDate, Stamp>) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    val d = model.day(item.first)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        StampView(model, item.second.id, d.date.dayOfYear, d.mood, 96.dp, modifier = Modifier.clickable { onOpen(item) })
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
