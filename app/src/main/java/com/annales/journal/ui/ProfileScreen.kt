package com.annales.journal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.Ink
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProfileScreen(model: AnnalesModel, modifier: Modifier = Modifier) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = K.acc, unfocusedBorderColor = K.line, focusedTextColor = K.ink, unfocusedTextColor = K.ink,
        cursorColor = K.acc, focusedLabelColor = K.acc, unfocusedLabelColor = K.muted,
    )
    val stamps = model.days.values.sumOf { it.stamps.size }
    val sealed = model.days.values.count { it.sealed }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header("Profile & settings", title("Your ", "post office"))
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(K.acc, CircleShape), contentAlignment = Alignment.Center) {
                    Text("A", style = TextStyle(fontFamily = K.serif, fontSize = 22.sp, color = Color.White))
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Keeping annales", style = TextStyle(fontFamily = K.serif, fontSize = 19.sp, color = K.ink))
                    Text("since ${model.since.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))} · $stamps stamps · $sealed days sealed", style = SmallStyle)
                }
            }
        }

        Section("Postmark")
        Card {
            OutlinedTextField(model.office, model::changeOffice, label = { Text("Post office") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(model.postcode, model::changePostcode, label = { Text("Postcode") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Text(
                "Printed round your datownik, the way Poczta Polska does it: the office over the top, the postcode underneath.",
                style = SmallStyle, modifier = Modifier.padding(top = 8.dp),
            )
            Text("Default ink", style = KickerStyle, modifier = Modifier.padding(top = 14.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ink.entries.forEach { ink -> InkChip(ink, ink == model.ink) { model.changeInk(ink) } }
            }
        }

        Section("Perforation")
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("classic" to "Classic", "scallop" to "Scallop").forEach { (k, n) ->
                    OutlinedButton(
                        onClick = { model.changeCut(k) },
                        border = BorderStroke(if (model.cut == k) 2.dp else 1.dp, if (model.cut == k) K.acc else K.line),
                    ) { Text(n, color = K.ink, fontWeight = if (model.cut == k) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            Text("Unsealed stamps are re-cut straight away; sealed ones keep their edge.", style = SmallStyle, modifier = Modifier.padding(top = 8.dp))
        }

        Section("Photos & ritual")
        Card {
            Toggle("Save sealed stamps to Photos", "Each stamp goes into an \"Annales\" album, with its perforation and postmark", model.saveToPhotos, model::changeSaveToPhotos)
            Toggle("Evening reminder", "A soft nudge at 21:30 if today is still unsealed", model.reminder, model::changeReminder)
            Toggle("On this phone only", "No account, no cloud, no analytics", true, null)
        }
        Text(
            "Annales 0.1.0 · pre-release. Collections, Insights and backups are coming in later builds.",
            style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun Section(name: String) =
    Text(name.uppercase(), style = KickerStyle, modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp))

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(K.surface, RoundedCornerShape(18.dp)).padding(16.dp)
) { content() }

@Composable
private fun Toggle(name: String, sub: String, on: Boolean, onChange: ((Boolean) -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
            Text(sub, style = SmallStyle)
        }
        if (onChange != null) Switch(
            on, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = K.acc, checkedThumbColor = Color.White),
        ) else Text("✓", style = BodyStyle.copy(color = K.green, fontWeight = FontWeight.Bold))
    }
}
