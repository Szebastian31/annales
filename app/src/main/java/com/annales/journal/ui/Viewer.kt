package com.annales.journal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.Moods
import com.annales.journal.data.Stamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A stamp full screen, like a gallery. Tap it to turn it over: the back holds the date, the mood, a title and a note. */
@Composable
fun Viewer(model: AnnalesModel, date: LocalDate, stamp: Stamp, onClose: () -> Unit) {
    val day = model.day(date)
    var back by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (back) 180f else 0f, tween(450), label = "flip")
    val width = (LocalConfiguration.current.screenWidthDp * .8f).dp
    var title by remember(stamp.id) { mutableStateOf(stamp.title) }
    var note by remember(stamp.id) { mutableStateOf(stamp.note) }
    fun saveAndClose() {
        if (title != stamp.title || note != stamp.note) model.updateStamp(date, stamp.copy(title = title, note = note))
        onClose()
    }
    androidx.activity.compose.BackHandler { saveAndClose() }

    Column(
        Modifier.fillMaxSize().background(Color(0xF21F1A16)).statusBarsPadding().navigationBarsPadding()
            .clickable(remember { MutableInteractionSource() }, null) { }, // swallow taps behind
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { saveAndClose() }) { Text("‹ Back", color = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(if (back) "tap to turn back" else "tap the stamp to turn it over", style = SmallStyle.copy(color = Color(0x99FFFFFF)))
            Spacer(Modifier.size(12.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.graphicsLayer { rotationY = turn; cameraDistance = 14f * density }
                    .clickable(remember { MutableInteractionSource() }, null) { back = !back }
            ) {
                if (turn <= 90f) {
                    StampView(model, stamp.id, date.dayOfYear, day.mood, width)
                } else {
                    Back(model, date, title, note, { title = it }, { note = it }, width, Modifier.graphicsLayer { rotationY = 180f })
                }
            }
        }
        val label = title.ifBlank { date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)) }
        Text(
            label, style = TextStyle(fontFamily = K.serif, fontSize = 22.sp, color = Color.White),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
        Text(
            listOfNotNull(day.mood?.let { Moods.names[it] }, if (day.sealed) "sealed in ${day.office.ifBlank { model.office }}" else "not sealed yet")
                .joinToString(" · "),
            style = SmallStyle.copy(color = Color(0xB3FFFFFF)),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 20.dp),
        )
    }
}

@Composable
private fun Back(
    model: AnnalesModel, date: LocalDate, title: String, note: String,
    onTitle: (String) -> Unit, onNote: (String) -> Unit, width: androidx.compose.ui.unit.Dp, modifier: Modifier,
) {
    val day = model.day(date)
    val sealedAt = if (day.sealed && day.sealedAt > 0)
        Instant.ofEpochMilli(day.sealedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)) else null
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = K.acc, unfocusedBorderColor = K.line, focusedTextColor = K.ink, unfocusedTextColor = K.ink,
        cursorColor = K.acc, focusedLabelColor = K.acc, unfocusedLabelColor = K.muted,
    )
    Column(
        modifier.size(width, width * 1.25f).background(K.surface).padding(18.dp),
    ) {
        Text("№${date.dayOfYear} · ${date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH))}", style = KickerStyle)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            day.mood?.let { Box(Modifier.size(12.dp).background(Color(Moods.color(it)), CircleShape)); Spacer(Modifier.size(8.dp)) }
            Text(day.mood?.let { Moods.names[it] } ?: "No mood set", style = BodyStyle)
        }
        Text(
            if (sealedAt != null) "Sealed $sealedAt · ${day.office}" else "Not sealed yet",
            style = SmallStyle, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(title, onTitle, label = { Text("Title") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(note, onNote, label = { Text("Note") }, colors = fieldColors, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
