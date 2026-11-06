package com.annales.journal.ui

import com.annales.journal.tr
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.annales.journal.data.Here
import kotlinx.coroutines.launch

/** True once Annales may ask for the phone's approximate location. */
fun canLocate(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * "Where was this?": type a town, pick one used before, or "Use where I am now". [home] is what an empty answer means
 * (the day's post office). Calls [onDone] with the town, or "" for the day's office.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceDialog(current: String, home: String, suggestions: List<String>, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(current) }
    var finding by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    fun locate() {
        finding = true; note = null
        scope.launch {
            val t = Here.town(context)
            finding = false
            if (t != null) text = t else note = tr("Couldn't tell where you are. Is location on?")
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) locate() else note = tr("Location wasn't allowed. You can still type the town.")
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = K.surface,
        title = { Text(tr("Where was this?"), style = TitleStyle.copy(fontSize = 22.sp)) },
        text = {
            Column {
                Text(tr("The town goes on this stamp's postmark. Leave it empty for {town}.", "town" to home), style = SmallStyle)
                OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(home) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                Text(
                    if (finding) tr("Finding where you are…") else "◎  " + tr("Use where I am now"),
                    style = BodyStyle.copy(fontWeight = FontWeight.Bold, color = K.acc),
                    modifier = Modifier.padding(top = 12.dp).clickable(enabled = !finding) { if (canLocate(context)) locate() else ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                        .padding(vertical = 6.dp),
                )
                note?.let { Text(it, style = SmallStyle) }
                if (suggestions.isNotEmpty()) FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s ->
                        val on = s.equals(text.trim(), ignoreCase = true)
                        Text(
                            s, style = SmallStyle.copy(color = if (on) K.surface else K.ink, fontWeight = FontWeight.Bold),
                            modifier = Modifier.background(if (on) K.ink else K.page, RoundedCornerShape(99.dp))
                                .border(1.dp, K.line, RoundedCornerShape(99.dp)).clickable { text = s }.padding(horizontal = 11.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(if (text.trim().equals(home, ignoreCase = true)) "" else text.trim()) }) { Text(tr("Save"), color = K.acc, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel"), color = K.muted) } },
    )
}
