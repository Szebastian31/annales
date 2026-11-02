package com.annales.journal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.CollType
import com.annales.journal.data.Collection
import com.annales.journal.data.Ink
import com.annales.journal.data.Places
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** New or edit collection. [start] is the existing one, or a blank of the chosen type. */
@Composable
fun CollectionForm(model: AnnalesModel, start: Collection, onClose: () -> Unit, onSaved: (Collection) -> Unit, onDeleted: () -> Unit) {
    val isNew = start.id == 0L
    var f by remember { mutableStateOf(start) }
    var picking by remember { mutableStateOf<String?>(null) } // "from", "to" or "ann"
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    val ok = f.name.isNotBlank() && (f.type != CollType.TRIP || f.place.isNotBlank()) &&
        (f.type !in setOf(CollType.TRIP, CollType.EVENT) || !f.to.isBefore(f.from))
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = K.acc, unfocusedBorderColor = K.line, focusedTextColor = K.ink, unfocusedTextColor = K.ink,
        cursorColor = K.acc, focusedLabelColor = K.acc, unfocusedLabelColor = K.muted,
    )
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

    Column(Modifier.fillMaxSize().background(K.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ Back", color = K.acc) }
            Spacer(Modifier.weight(1f))
            Text(if (isNew) "New collection" else "Edit collection", style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 18.sp, color = K.ink))
            Spacer(Modifier.weight(1f)); Spacer(Modifier.size(64.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            // type
            CollType.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { t ->
                        val on = t == f.type
                        Column(
                            Modifier.weight(1f).background(if (on) K.accSoft else K.surface, RoundedCornerShape(14.dp))

                                .clickable(enabled = isNew || on) { f = f.copy(type = t) }.padding(12.dp)
                        ) {
                            Text("${t.glyph} ${t.label}", style = BodyStyle.copy(fontWeight = FontWeight.Bold, color = if (on) K.acc else K.ink))
                            Text(t.about, style = SmallStyle.copy(fontSize = 11.sp))
                        }
                    }
                }
            }
            val hint = mapOf(CollType.TRIP to "Weekend in Kraków", CollType.EVENT to "Ola's wedding", CollType.ANNIV to "Our anniversary", CollType.THEME to "Coffee")
            OutlinedTextField(f.name, { f = f.copy(name = it) }, label = { Text("Name") }, placeholder = { Text(hint[f.type] ?: "") },
                singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())

            if (f.type == CollType.TRIP) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(f.place, { f = f.copy(place = it) }, label = { Text("Where") }, placeholder = { Text("City") },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Places.suggestions.forEach { s -> Chip(s, f.place == s) { f = f.copy(place = s, name = f.name.ifBlank { s }) } }
                }
            }
            if (f.type == CollType.TRIP || f.type == CollType.EVENT) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("From", f.from.format(fmt), Modifier.weight(1f)) { picking = "from" }
                    DateField("To", f.to.format(fmt), Modifier.weight(1f), error = f.to.isBefore(f.from)) { picking = "to" }
                }
            }
            if (f.type == CollType.ANNIV) {
                Spacer(Modifier.height(8.dp))
                DateField("Date · repeats every year", LocalDate.of(2024, f.month, f.day).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)), Modifier.fillMaxWidth()) { picking = "ann" }
            }

            if (f.type != CollType.THEME) {
                val p = Places.of(if (f.type == CollType.TRIP) f.place.ifBlank { "Somewhere" } else "Gliwice")
                Text("POSTMARK INK", style = KickerStyle, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ink.entries.forEach { ink -> InkChip(ink, inkOf(f) == ink) { f = f.copy(ink = if (ink == Ink.OFFICIAL) null else ink.name) } }
                }
                Text(
                    when {
                        f.type != CollType.TRIP -> "Changes how this collection is labelled. Days are stamped by your own post office."
                        inkOf(f) == Ink.OFFICIAL -> "Days in this trip are stamped ${p.mark}${if (p.bottom.isNotBlank()) " · ${p.bottom}" else ""}, in black, like ${p.post} would."
                        else -> "Days in this trip are stamped ${p.mark}, in ${inkOf(f).label.lowercase()}: your own touch."
                    },
                    style = SmallStyle, modifier = Modifier.padding(top = 6.dp),
                )
            } else Text(
                "Themes don't change postmarks. Add stamps from any stamp's page with ＋ ${f.name.ifBlank { "this theme" }}.",
                style = SmallStyle, modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(f.note, { f = f.copy(note = it) }, label = { Text("Story") }, placeholder = { Text("What is this collection about?") },
                minLines = 3, colors = fieldColors, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = { onSaved(model.saveCollection(f.copy(name = f.name.trim(), place = f.place.trim()))) }, enabled = ok,
                colors = ButtonDefaults.buttonColors(containerColor = K.acc),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(50.dp),
            ) { Text(if (isNew) "Create collection" else "Save", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            if (!isNew) {
                TextButton(onClick = { if (confirmDelete) { model.deleteCollection(start); onDeleted() } else confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(if (confirmDelete) "Tap again to delete" else "Delete collection", color = Color(0xFFB4483A), fontWeight = FontWeight.SemiBold)
                }
                Text("Deleting a collection keeps every stamp in your journal.", style = SmallStyle, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    picking?.let { which ->
        val year = LocalDate.now().year
        val initial = when (which) {
            "from" -> f.from; "to" -> f.to
            else -> LocalDate.of(year, f.month, 1).let { it.withDayOfMonth(minOf(f.day, it.lengthOfMonth())) }
        }
        DatePick(initial, onDismiss = { picking = null }) { d ->
            f = when (which) {
                "from" -> f.copy(from = d, to = if (f.to.isBefore(d)) d else f.to)
                "to" -> f.copy(to = d)
                else -> f.copy(month = d.monthValue, day = d.dayOfMonth)
            }
            picking = null
        }
    }
}

@Composable
private fun DateField(label: String, value: String, modifier: Modifier, error: Boolean = false, onClick: () -> Unit) = Column(
    modifier.background(K.surface, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
) {
    Text(label, style = SmallStyle.copy(fontSize = 11.sp, color = if (error) Color(0xFFB4483A) else K.muted))
    Text(value, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePick(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("OK", color = K.acc, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = K.muted) } },
    ) { DatePicker(state) }
}

