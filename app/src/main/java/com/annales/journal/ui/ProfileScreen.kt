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
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import com.annales.journal.I18n
import com.annales.journal.tr

@Composable
fun ProfileScreen(model: AnnalesModel, modifier: Modifier = Modifier) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = K.acc, unfocusedBorderColor = K.line, focusedTextColor = K.ink, unfocusedTextColor = K.ink,
        cursorColor = K.acc, focusedLabelColor = K.acc, unfocusedLabelColor = K.muted,
    )
    val stamps = model.days.values.sumOf { it.stamps.size }
    val sealed = model.days.values.count { it.sealed }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header(tr("Profile & settings"), title(tr("Your *post office*")))
        val ins = remember(model.days) { com.annales.journal.data.Insights(model.days.values, model.collections) }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(K.acc, CircleShape), contentAlignment = Alignment.Center) {
                    Text(model.name.trim().take(1).uppercase().ifBlank { "A" }, style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 24.sp, color = K.onAcc))
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicTextField(
                        model.name, model::changeName, singleLine = true,
                        textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 21.sp, color = K.ink),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(K.acc),
                        decorationBox = { inner -> Box { if (model.name.isEmpty()) Text(tr("Your name"), style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 21.sp, color = K.muted)); inner() } },
                    )
                    Text("${prettyOffice(model.office).substringBefore(" ")} · ${tr("keeping annales since {date}", "date" to I18n.date(model.since, "MMM yyyy"))}", style = SmallStyle)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                listOf(stamps to tr("stamps"), sealed to tr("days"), ins.streak to tr("streak"), model.collections.size to tr("collections")).forEach { (n, l) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 22.sp, color = K.ink))
                        Text(l, style = SmallStyle.copy(fontSize = 12.5.sp))
                    }
                }
            }
        }

        Section(tr("Look"), tr(K.look.label))
        Card {
            Look.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { l -> LookTile(l, l == K.look, Modifier.weight(1f)) { model.changeLook(l) } }
                }
            }
        }

        val about = StampRendererCuts.firstOrNull { it.first == model.cut }
        Section(tr("Perforation"), about?.second?.let { tr(it) } ?: "")
        Card {
            StampRendererCuts.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (k, n, _) -> CutTile(k, tr(n), model.cut == k, Modifier.weight(1f)) { model.changeCut(k) } }
                }
            }
            Text(tr("{cut}: {about}. Unsealed stamps are re-cut straight away; sealed ones keep their edge.", "cut" to tr(about?.second ?: ""), "about" to tr(about?.third ?: "")), style = SmallStyle)
        }

        Section(tr("Default postmark ink"))
        Card {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ink.entries.forEach { ink -> InkChip(ink, ink == model.ink, label = if (ink == Ink.OFFICIAL) "${tr("Official")} · Poczta Polska" else tr(ink.label)) { model.changeInk(ink) } }
            }
            Text(
                tr("Official stamps it the way the local post office would: black ink and their own layout. A Poczta Polska datownik at home, the city's own office on a trip. Any other ink is your own touch."),
                style = SmallStyle, modifier = Modifier.padding(top = 10.dp),
            )
            OutlinedTextField(model.office, model::changeOffice, label = { Text(tr("Your post office")) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(model.postcode, model::changePostcode, label = { Text(tr("Postcode")) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Text(tr("Printed round your datownik: the office over the top, the postcode underneath."), style = SmallStyle, modifier = Modifier.padding(top = 8.dp))
        }

        Section(tr("Photos"))
        Card {
            Toggle(tr("Save sealed stamps to Photos"), tr("Each stamp goes into an “Annales” album on your phone: the clean perforated stamp, without the postmark ink"), model.saveToPhotos, model::changeSaveToPhotos)
            Toggle(tr("Keep original photos"), tr("So you can re-crop a stamp any time"), model.keepOriginals, model::changeKeepOriginals)
        }

        Section(tr("Postmark sources"), tr("sibling apps"))
        Card {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val S = com.annales.journal.data.Siblings
            listOf(
                Triple(S.WEATHER, "wx.", "worked out here"),
                Triple(S.CAELUM, "", "calculated here"),
                Triple(S.FIDELIS, "music.", "shares"),
                Triple(S.KALENDAE, "money.", "shares"),
                Triple(S.AUGUR, "augur.", "shares"),
            ).forEach { (app, prefix, how) ->
                val has = model.installed(app)
                val status = when {
                    prefix == "" -> tr("calculated here") to K.green
                    prefix == "wx." && model.hasShared(prefix) -> tr("same 13 models") to K.green
                    prefix == "wx." -> tr("fetched when online") to K.muted
                    !has -> tr("not installed") to K.muted
                    !model.canShare(app) -> tr("needs its update") to K.acc
                    model.hasShared(prefix) -> tr("connected") to K.green
                    else -> tr("ready · nothing yet") to K.green
                }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = has) {
                        ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.let { ctx.startActivity(it) }
                    }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                        Text(tr(app.what), style = SmallStyle)
                    }
                    Text(status.first, style = SmallStyle.copy(fontSize = 12.5.sp, color = status.second, fontWeight = FontWeight.Bold))
                    if (has) Text("  ›", style = BodyStyle.copy(color = K.muted))
                }
            }
            Text(
                tr("Weather is the Consensus Weather forecast, fetched from Open-Meteo for your post office's town (or the trip's city). Fidelis, Kalendae and Augur each hand Annales a one-line summary of the day; nothing leaves your phone."),
                style = SmallStyle, modifier = Modifier.padding(top = 6.dp),
            )
        }

        LanguageCard()

        Section(tr("Ritual & privacy"))
        Card {
            Toggle(tr("Evening reminder"), tr("A soft nudge at 21:30 if today is still unsealed"), model.reminder, model::changeReminder)
            Toggle(tr("Seal haptics"), tr("The satisfying thunk when you seal"), model.haptics, model::changeHaptics)
            Toggle(tr("On this phone only"), tr("No account, no cloud, no analytics. Internet only for the weather"), true, null)
            BackupRow(model)
        }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Text(
            "Annales " + (runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""),
            style = SmallStyle, modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

/** Annales' language: the phone's, or one of the 33. Picking one restarts the screen in it. */
@Composable
private fun LanguageCard() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    val chosen = remember { I18n.chosen(ctx) }
    val name = { code: String -> I18n.LANGS.firstOrNull { it.first == code }?.second }
    val pick = { code: String ->
        open = false
        if (code != chosen) { I18n.choose(ctx, code); (ctx as? android.app.Activity)?.recreate() }
    }
    Section(tr("Language"), name(I18n.lang) ?: "")
    Card {
        Box {
            Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name(chosen) ?: tr("Same as the phone"), style = BodyStyle.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("▾", style = BodyStyle.copy(color = K.muted))
            }
            androidx.compose.material3.DropdownMenu(open, { open = false }) {
                androidx.compose.material3.DropdownMenuItem({ Text(tr("Same as the phone")) }, { pick("") })
                I18n.LANGS.forEach { (code, n, _) -> androidx.compose.material3.DropdownMenuItem({ Text(n) }, { pick(code) }) }
            }
        }
        Text(
            tr("Annales reloads in the new language. Your journal stays as it is; postmarks and stamps are never translated."),
            style = SmallStyle, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Section(name: String, right: String = "") = Row(
    Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Text(I18n.upper(name), style = KickerStyle.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
    if (right.isNotBlank()) Text(right, style = SmallStyle.copy(fontSize = 12.5.sp))
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
    Text(tr(l.label), style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 15.sp, color = Color(l.ink)), modifier = Modifier.padding(top = 6.dp))
    Text(tr(l.about), style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(l.muted)))
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
    Text(name, style = TextStyle(fontSize = 12.sp, color = if (on) K.acc else K.ink, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1)
}

/**
 * "Encrypted backup file": save everything (journal, collections, settings, photos and stamps) to a file you keep, optionally
 * locked with a password, in the same format as Kalendae; or restore one, which replaces what's on this phone.
 */
@Composable
private fun BackupRow(model: AnnalesModel) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var ask by remember { mutableStateOf<String?>(null) }          // "save" or "open": which password dialog is up
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    var pw by remember { mutableStateOf("") }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) { model.backupTo(uri, pw.ifBlank { null }); pw = "" } }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (model.backupLocked(uri)) {
                false -> model.message = tr("That file isn't an Annales backup.")
                true -> { pending = uri; pw = ""; ask = "open" }
                null -> model.restoreFrom(uri, null) { }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(tr("Encrypted backup file"), style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
        Text(tr("Everything, photos included, in the same format as Kalendae. Lock it with a password (AES-256)."), style = SmallStyle)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pw = ""; ask = "save" }, enabled = !model.busy) { Text(tr("Save a backup"), color = K.ink) }
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }, enabled = !model.busy) {
                Text(tr("Restore…"), color = K.ink)
            }
        }
    }
    ask?.let { which ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(if (which == "save") tr("Lock the backup?") else tr("This backup is locked")) },
            text = {
                Column {
                    Text(
                        if (which == "save") tr("Type a password to lock it, or leave it empty. There's no way to open a locked backup without it.")
                        else tr("Type the password it was saved with. Restoring replaces everything on this phone."),
                        style = SmallStyle,
                    )
                    OutlinedTextField(
                        pw, { pw = it }, singleLine = true, label = { Text(tr("Password")) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    ask = null
                    if (which == "save") save.launch("annales-backup-${java.time.LocalDate.now()}.json")
                    else pending?.let { u -> model.restoreFrom(u, pw) { }; pw = "" }
                }) { Text(if (which == "save") tr("Choose where") else tr("Restore"), color = K.acc, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { ask = null }) { Text(tr("Cancel"), color = K.muted) } },
            containerColor = K.surface,
        )
    }
}
