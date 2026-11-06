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
import com.annales.journal.I18n
import com.annales.journal.tr

/**
 * Stamps full screen, like a gallery: swipe or use the arrows to move through [items]. [inside] names the collection
 * you came from, so its cover can be set from here.
 */
@Composable
fun Viewer(model: AnnalesModel, items: List<Pair<LocalDate, Stamp>>, start: Int, inside: Collection?, onEdit: ((LocalDate) -> Unit)? = null, canReorder: Boolean = false, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(start.coerceIn(0, maxOf(0, items.size - 1))) }
    if (items.isEmpty()) { androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }; return }
    val (date, first) = items[index]
    // re-read the stamp, in case it was changed a moment ago
    val stamp = model.day(date).stamps.firstOrNull { it.id == first.id } ?: first
    key(stamp.id) {
        ViewerPage(
            model, date, stamp, inside,
            position = if (items.size > 1) tr("{n} of {total}", "n" to index + 1, "total" to items.size) else null,
            onPrev = if (index > 0) ({ index-- }) else null,
            onNext = if (index < items.size - 1) ({ index++ }) else null,
            onClose = onClose, onEdit = onEdit?.let { e -> { e(date) } },
            // the day's stamps in their order: put this one first, the one the calendar and pages show
            onMakeFirst = if (canReorder && model.day(date).stamps.firstOrNull()?.id != stamp.id) ({ model.moveStamp(date, stamp, 0); index = 0 }) else null,
        )
    }
}

@Composable
private fun ViewerPage(
    model: AnnalesModel, date: LocalDate, stamp: Stamp, inside: Collection?, position: String?,
    onPrev: (() -> Unit)?, onNext: (() -> Unit)?, onClose: () -> Unit, onEdit: (() -> Unit)? = null, onMakeFirst: (() -> Unit)? = null,
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
            TextButton(onClick = { saveAndClose() }) { Text(tr("‹ Back"), color = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(position ?: if (back) tr("tap to turn back") else tr("tap the stamp to turn it over"), style = SmallStyle.copy(color = Color(0x99FFFFFF)))
            if (onMakeFirst != null) TextButton(onClick = onMakeFirst) { Text(tr("★ Make first"), color = Color.White, fontWeight = FontWeight.Bold) }
            if (onEdit != null) TextButton(onClick = onEdit) { Text(tr("Edit ✎"), color = Color.White, fontWeight = FontWeight.Bold) } else Spacer(Modifier.size(12.dp))
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
        val label = I18n.date(date, "EEEE d MMMM yyyy")
        Text(
            label, style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 22.sp, color = Color.White),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
        Text(
            listOfNotNull(day.mood?.let { tr(Moods.names[it]) }, model.placeOf(date, stamp), if (day.sealed) null else tr("not sealed yet"))
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
            DarkChip(if (isCover) tr("★ Cover of {name}", "name" to current.name) else tr("☆ Set as cover"), isCover) { if (!isCover) model.setCover(current, stamp.id) }
        }
        themes.forEach { t ->
            val inIt = stamp.id in t.ids
            DarkChip(if (inIt) "✓ ${t.name}" else "＋ ${t.name}", inIt) { model.toggleInTheme(t, stamp.id) }
        }
        if (themes.isEmpty()) Text(tr("Make a theme in Collections to file stamps like this one."), style = SmallStyle.copy(color = Color(0x80FFFFFF)))
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
        I18n.date(Instant.ofEpochMilli(day.sealedAt).atZone(ZoneId.systemDefault()), "d MMM, HH:mm") else null
    Column(modifier.size(width, width * 1.25f).background(K.surface).padding(20.dp)) {
        Text(I18n.upper("№${date.dayOfYear} · ${I18n.date(date, "EEEE d MMMM yyyy")}"), style = KickerStyle)
        Text(
            if (sealedAt != null) tr("Sealed {when} · {office}", "when" to sealedAt, "office" to prettyOffice(day.office)) else tr("Not sealed yet"),
            style = SmallStyle, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            day.line.ifBlank { tr("No line written for this day.") }.let { if (day.line.isBlank()) it else "“$it”" },
            style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 27.sp,
                color = if (day.line.isBlank()) K.muted else K.ink),
            modifier = Modifier.padding(top = 18.dp).weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            day.mood?.let { Box(Modifier.size(12.dp).background(Color(Moods.color(it)), CircleShape)); Spacer(Modifier.size(8.dp)) }
            Text(day.mood?.let { tr(Moods.names[it]) } ?: tr("No mood set"), style = BodyStyle.copy(fontWeight = FontWeight.Bold))
        }
        if (day.feels.isNotEmpty() || day.factors.isNotEmpty()) Text(
            listOf(day.feels.joinToString(", ") { tr(it) }, day.factors.joinToString(", ") { tr(it) }.let { if (it.isBlank()) "" else tr("because of {tags}", "tags" to it) })
                .filter { it.isNotBlank() }.joinToString(" · "),
            style = SmallStyle, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
        postmarkBits(model, day).takeIf { it.isNotEmpty() }?.let { bits ->
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(K.line))
            Text(bits.joinToString("  ·  "), style = SmallStyle.copy(color = K.ink), maxLines = 2, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
