package com.annales.journal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
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
        val ins = remember(model.days) { com.annales.journal.data.Insights(model.days.values, model.collections) }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(K.acc, CircleShape), contentAlignment = Alignment.Center) {
                    Text(model.name.trim().take(1).uppercase().ifBlank { "A" }, style = TextStyle(fontFamily = K.serif, fontSize = 24.sp, color = K.onAcc))
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicTextField(
                        model.name, model::changeName, singleLine = true,
                        textStyle = TextStyle(fontFamily = K.serif, fontSize = 21.sp, color = K.ink),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(K.acc),
                        decorationBox = { inner -> Box { if (model.name.isEmpty()) Text("Your name", style = TextStyle(fontFamily = K.serif, fontSize = 21.sp, color = K.muted)); inner() } },
                    )
                    Text("${prettyOffice(model.office).substringBefore(" ")} · keeping annales since ${model.since.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))}", style = SmallStyle)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                listOf(stamps to "stamps", sealed to "days", ins.streak to "streak", model.collections.size to "collections").forEach { (n, l) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", style = TextStyle(fontFamily = K.serif, fontSize = 22.sp, color = K.ink))
                        Text(l, style = SmallStyle.copy(fontSize = 11.sp))
                    }
                }
            }
        }

        Section("Look", K.look.label)
        Card {
            Look.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { l -> LookTile(l, l == K.look, Modifier.weight(1f)) { model.changeLook(l) } }
                }
            }
        }

        val about = StampRendererCuts.firstOrNull { it.first == model.cut }
        Section("Perforation", about?.second ?: "")
        Card {
            StampRendererCuts.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (k, n, _) -> CutTile(k, n, model.cut == k, Modifier.weight(1f)) { model.changeCut(k) } }
                }
            }
            Text("${about?.second ?: ""}: ${about?.third ?: ""}. Unsealed stamps are re-cut straight away; sealed ones keep their edge.", style = SmallStyle)
        }

        Section("Default postmark ink")
        Card {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ink.entries.forEach { ink -> InkChip(ink, ink == model.ink, label = if (ink == Ink.OFFICIAL) "Official · Poczta Polska" else ink.label) { model.changeInk(ink) } }
            }
            Text(
                "Official stamps it the way the local post office would: black ink and their own layout. A Poczta Polska datownik at home, the city's own office on a trip. Any other ink is your own touch.",
                style = SmallStyle, modifier = Modifier.padding(top = 10.dp),
            )
            OutlinedTextField(model.office, model::changeOffice, label = { Text("Your post office") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(model.postcode, model::changePostcode, label = { Text("Postcode") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Text("Printed round your datownik: the office over the top, the postcode underneath.", style = SmallStyle, modifier = Modifier.padding(top = 8.dp))
        }

        Section("Photos")
        Card {
            Toggle("Save sealed stamps to Photos", "Each stamp goes into an \"Annales\" album on your phone, with its perforation and postmark", model.saveToPhotos, model::changeSaveToPhotos)
            Toggle("Keep original photos", "So you can re-crop a stamp any time", model.keepOriginals, model::changeKeepOriginals)
        }

        Section("Postmark sources", "sibling apps")
        Card {
            listOf(
                Triple("Consensus Weather", "Weather, temperature, model agreement", false),
                Triple("Caelum Sky", "Moon phase, what was overhead", true),
                Triple("Fidelis Music", "Top track and listening time", false),
                Triple("Kalendae", "Spent vs safe-to-spend", false),
                Triple("Augur", "Daily tone, just for fun", false),
            ).forEach { (n, s, on) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                        Text(s, style = SmallStyle)
                    }
                    Text(if (on) "calculated here" else "not connected yet", style = SmallStyle.copy(fontSize = 11.sp, color = if (on) K.green else K.muted))
                }
            }
            Text("Annales can't read your other apps yet. The moon is worked out on the phone; the rest join once the apps can share with each other.", style = SmallStyle, modifier = Modifier.padding(top = 6.dp))
        }

        Section("Ritual & privacy")
        Card {
            Toggle("Evening reminder", "A soft nudge at 21:30 if today is still unsealed", model.reminder, model::changeReminder)
            Toggle("Seal haptics", "The satisfying thunk when you seal", model.haptics, model::changeHaptics)
            Toggle("On this phone only", "No account, no cloud, no analytics", true, null)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Encrypted backup file", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                    Text("Same format as Kalendae", style = SmallStyle)
                }
                Text("coming soon", style = SmallStyle.copy(fontSize = 11.sp))
            }
        }
        Text(
            "Annales 0.5.0 · pre-release.",
            style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun Section(name: String, right: String = "") = Row(
    Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Text(name.uppercase(), style = KickerStyle.copy(fontSize = 10.5.sp), modifier = Modifier.weight(1f))
    if (right.isNotBlank()) Text(right, style = SmallStyle.copy(fontSize = 11.sp))
}

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
            colors = SwitchDefaults.colors(checkedTrackColor = K.acc, checkedThumbColor = K.onAcc),
        ) else Text("✓", style = BodyStyle.copy(color = K.green, fontWeight = FontWeight.Bold))
    }
}

private val StampRendererCuts = com.annales.journal.render.StampRenderer.cuts

/** A swatch of the look: its page, a card, an accent dot and its name. */
@Composable
private fun LookTile(l: Look, on: Boolean, modifier: Modifier, onClick: () -> Unit) = Column(
    modifier.clip(RoundedCornerShape(14.dp)).background(Color(l.bg))
        .border(if (on) 2.5.dp else 1.dp, if (on) K.acc else K.line, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(10.dp),
) {
    Row(Modifier.fillMaxWidth().height(26.dp).background(Color(l.surface), RoundedCornerShape(6.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color(l.acc), CircleShape))
        Spacer(Modifier.size(4.dp))
        Box(Modifier.weight(1f).height(4.dp).background(Color(l.ink).copy(alpha = .5f), RoundedCornerShape(2.dp)))
    }
    Text(l.label, style = TextStyle(fontFamily = K.serif, fontSize = 15.sp, color = Color(l.ink)), modifier = Modifier.padding(top = 6.dp))
    Text(l.about, style = TextStyle(fontSize = 10.sp, color = Color(l.muted)))
}

/** The edge drawn as a little stamp shape. */
@Composable
private fun CutTile(key: String, name: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) = Column(
    modifier.clip(RoundedCornerShape(10.dp)).background(if (on) K.accSoft else Color.Transparent).clickable(onClick = onClick).padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    androidx.compose.foundation.Canvas(Modifier.size(36.dp, 45.dp)) {
        val path = com.annales.journal.render.StampRenderer.shape(key, 7, size.width, size.height)
        drawPath(path.asComposePath(), if (on) K.acc else K.muted)
    }
    Text(name, style = TextStyle(fontSize = 10.sp, color = if (on) K.acc else K.ink, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
}
