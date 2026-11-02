package com.annales.journal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.CollType
import com.annales.journal.data.Collection
import com.annales.journal.data.Moods
import com.annales.journal.data.Stamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stamps full screen, like a gallery: swipe or use the arrows to move through [items]. [inside] names the collection
 * you came from, so its cover can be set from here.
 */
@Composable
fun Viewer(model: AnnalesModel, items: List<Pair<LocalDate, Stamp>>, start: Int, inside: Collection?, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(start.coerceIn(0, maxOf(0, items.size - 1))) }
    if (items.isEmpty()) { androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }; return }
    val (date, first) = items[index]
    // re-read the stamp, in case it was changed a moment ago
    val stamp = model.day(date).stamps.firstOrNull { it.id == first.id } ?: first
    key(stamp.id) {
        ViewerPage(
            model, date, stamp, inside,
            position = if (items.size > 1) "${index + 1} of ${items.size}" else null,
            onPrev = if (index > 0) ({ index-- }) else null,
            onNext = if (index < items.size - 1) ({ index++ }) else null,
            onClose = onClose,
        )
    }
}

@Composable
private fun ViewerPage(
    model: AnnalesModel, date: LocalDate, stamp: Stamp, inside: Collection?, position: String?,
    onPrev: (() -> Unit)?, onNext: (() -> Unit)?, onClose: () -> Unit,
) {
    val day = model.day(date)
    var back by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (back) 180f else 0f, tween(450), label = "flip")
    val width = (LocalConfiguration.current.screenWidthDp * .78f).dp
    fun saveAndClose() = onClose()
    androidx.activity.compose.BackHandler { saveAndClose() }
    var drag by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier.fillMaxSize().background(Color(0xF21F1A16)).statusBarsPadding().navigationBarsPadding()
            .clickable(remember { MutableInteractionSource() }, null) { }, // swallow taps behind
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { saveAndClose() }) { Text("‹ Back", color = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(position ?: if (back) "tap to turn back" else "tap the stamp to turn it over", style = SmallStyle.copy(color = Color(0x99FFFFFF)))
            Spacer(Modifier.size(12.dp))
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(onPrev, onNext) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val go = if (drag > 120f) onPrev else if (drag < -120f) onNext else null
                        if (go != null) go()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, dx -> drag += dx }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.graphicsLayer { rotationY = turn; cameraDistance = 14f * density; translationX = drag * .4f }
                    .clickable(remember { MutableInteractionSource() }, null) { back = !back }
            ) {
                if (turn <= 90f) {
                    StampView(model, stamp.id, date.dayOfYear, day.mood, width)
                } else {
                    Back(model, date, width, Modifier.graphicsLayer { rotationY = 180f })
                }
            }
            onPrev?.let { Arrow("‹", Modifier.align(Alignment.CenterStart)) { it() } }
            onNext?.let { Arrow("›", Modifier.align(Alignment.CenterEnd)) { it() } }
        }
        val label = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH))
        Text(
            label, style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 22.sp, color = Color.White),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
        Text(
            listOfNotNull(day.mood?.let { Moods.names[it] }, if (day.sealed) "sealed in ${prettyOffice(day.office.ifBlank { model.office })}" else "not sealed yet")
                .joinToString(" · "),
            style = SmallStyle.copy(color = Color(0xB3FFFFFF)),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
        )
        // the day's one line, on every stamp of that day
        if (day.line.isNotBlank() && !back) Text(
            "“${day.line}”",
            style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp,
                color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 10.dp),
        )
        Themes(model, stamp, inside)
    }
}

@Composable
private fun Arrow(glyph: String, modifier: Modifier, onClick: () -> Unit) = Box(
    modifier.padding(6.dp).size(40.dp).background(Color(0x33FFFFFF), CircleShape).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(glyph, color = Color.White, fontSize = 24.sp) }

/** Chips under the stamp: one per theme (tap to add or remove), plus "Set as cover" inside a collection. */
@Composable
private fun Themes(model: AnnalesModel, stamp: Stamp, inside: Collection?) {
    val themes = model.collections.filter { it.type == CollType.THEME }
    val current = inside?.let { c -> model.collections.firstOrNull { it.id == c.id } }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (current != null && model.entries(current).any { (_, ss) -> ss.any { it.id == stamp.id } }) {
            val isCover = model.cover(current)?.second?.id == stamp.id
            DarkChip(if (isCover) "★ Cover of ${current.name}" else "☆ Set as cover", isCover) { if (!isCover) model.setCover(current, stamp.id) }
        }
        themes.forEach { t ->
            val inIt = stamp.id in t.ids
            DarkChip(if (inIt) "✓ ${t.name}" else "＋ ${t.name}", inIt) { model.toggleInTheme(t, stamp.id) }
        }
        if (themes.isEmpty()) Text("Make a theme in Collections to file stamps like this one.", style = SmallStyle.copy(color = Color(0x80FFFFFF)))
    }
}

@Composable
private fun DarkChip(label: String, on: Boolean, onClick: () -> Unit) = Text(
    label,
    style = TextStyle(fontSize = 14.sp, color = if (on) Color(0xFF1F1A16) else Color.White, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
    modifier = Modifier.background(if (on) Color(0xFFF2C9A0) else Color(0x26FFFFFF), RoundedCornerShape(99.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
)

/** The back of the stamp: the day it belongs to, its one line, and how it felt. Edited on the day's page, not here. */
@Composable
private fun Back(model: AnnalesModel, date: LocalDate, width: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val day = model.day(date)
    val sealedAt = if (day.sealed && day.sealedAt > 0)
        Instant.ofEpochMilli(day.sealedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)) else null
    Column(modifier.size(width, width * 1.25f).background(K.surface).padding(20.dp)) {
        Text("№${date.dayOfYear} · ${date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH))}".uppercase(), style = KickerStyle)
        Text(
            if (sealedAt != null) "Sealed $sealedAt · ${prettyOffice(day.office)}" else "Not sealed yet",
            style = SmallStyle, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            day.line.ifBlank { "No line written for this day." }.let { if (day.line.isBlank()) it else "“$it”" },
            style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 27.sp,
                color = if (day.line.isBlank()) K.muted else K.ink),
            modifier = Modifier.padding(top = 18.dp).weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            day.mood?.let { Box(Modifier.size(12.dp).background(Color(Moods.color(it)), CircleShape)); Spacer(Modifier.size(8.dp)) }
            Text(day.mood?.let { Moods.names[it] } ?: "No mood set", style = BodyStyle.copy(fontWeight = FontWeight.Bold))
        }
        if (day.feels.isNotEmpty() || day.factors.isNotEmpty()) Text(
            listOf(day.feels.joinToString(", "), day.factors.joinToString(", ").let { if (it.isBlank()) "" else "because of $it" })
                .filter { it.isNotBlank() }.joinToString(" · "),
            style = SmallStyle, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
