package com.annales.journal.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.data.Collection
import com.annales.journal.data.Stamp
import com.annales.journal.render.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class Tab(val label: String, val glyph: String) {
    TODAY("Today", "◎"), JOURNAL("Journal", "▦"), COLLECTIONS("Collections", "❖"), INSIGHTS("Insights", "∿"), PROFILE("Profile", "◯")
}

/** What the full-screen viewer is showing: a run of stamps, where to start, and the collection it came from. */
private data class Viewing(val items: List<Pair<LocalDate, Stamp>>, val start: Int, val inside: Collection?)

@Composable
fun AnnalesApp(model: AnnalesModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var openDay by remember { mutableStateOf<LocalDate?>(null) }        // a past day, opened from the Journal or a collection
    var openColl by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Collection?>(null) }       // id 0 = new
    var viewing by remember { mutableStateOf<Viewing?>(null) }
    var cropping by remember { mutableStateOf<Pair<LocalDate, Bitmap>?>(null) }
    var pickFor by remember { mutableStateOf(LocalDate.now()) }
    var adding by remember { mutableStateOf<LocalDate?>(null) }       // the "photo or sky?" sheet is open for this day

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val date = pickFor
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { Photos.load(context, uri) }
            if (bmp == null) model.message = "Couldn't open that photo." else cropping = date to bmp
        }
    }
    fun add(date: LocalDate) { adding = date }
    fun pickPhoto(date: LocalDate) {
        pickFor = date; adding = null
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun viewDay(date: LocalDate, s: Stamp) {
        val list = model.day(date).stamps.map { date to it }
        viewing = Viewing(list, list.indexOfFirst { it.second.id == s.id }, null)
    }

    // ask once for notifications, for the evening reminder (Android 13+)
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && model.reminder) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val overlay = viewing != null || cropping != null || editing != null || adding != null

    // light status bar icons on the Ink look, dark ones elsewhere
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !K.look.dark; isAppearanceLightNavigationBars = !K.look.dark
            }
        }
    }
    BackHandler(enabled = !overlay && (openDay != null || openColl != null)) { if (openDay != null) openDay = null else openColl = null }

    Box(Modifier.fillMaxSize().background(K.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                val day = openDay
                val coll = openColl?.let { id -> model.collections.firstOrNull { it.id == id } }
                when {
                    day != null -> Column {
                        TextButton(onClick = { openDay = null }, modifier = Modifier.padding(start = 8.dp)) {
                            Text("‹ ${coll?.name ?: "Journal"}", color = K.acc)
                        }
                        DayPage(model, day, onAdd = { add(day) }, onOpen = { viewDay(day, it) }, onOpenPast = { d, s -> viewDay(d, s) })
                    }
                    coll != null -> CollectionScreen(
                        model, coll,
                        onBack = { openColl = null }, onEdit = { editing = coll },
                        onOpenStamp = { _, s, all -> viewing = Viewing(all, all.indexOfFirst { it.second.id == s.id }, coll) },
                        onOpenDay = { if (it == LocalDate.now()) { openColl = null; tab = Tab.TODAY } else openDay = it },
                    )
                    tab == Tab.TODAY -> DayPage(model, LocalDate.now(), onAdd = { add(LocalDate.now()) }, onOpen = { viewDay(LocalDate.now(), it) }, onOpenPast = { d, s -> viewDay(d, s) })
                    tab == Tab.JOURNAL -> JournalScreen(model, onOpenDay = { if (it == LocalDate.now()) tab = Tab.TODAY else openDay = it })
                    tab == Tab.COLLECTIONS -> CollectionsScreen(
                        model, onOpen = { openColl = it.id },
                        onNew = { type -> editing = Collection(0, type, "", from = LocalDate.now(), to = LocalDate.now().plusDays(2)) },
                    )
                    tab == Tab.INSIGHTS -> InsightsScreen(model)
                    else -> ProfileScreen(model)
                }
            }
            TabBar(tab) { tab = it; openDay = null; openColl = null }
        }

        editing?.let { c ->
            CollectionForm(
                model, c, onClose = { editing = null },
                onSaved = { saved ->
                    editing = null; tab = Tab.COLLECTIONS; openColl = saved.id
                    model.message = if (c.id == 0L) "${saved.name} created." else "${saved.name} saved."
                },
                onDeleted = { editing = null; openColl = null; model.message = "Collection deleted. Its stamps are still in your journal." },
            )
        }
        viewing?.let { v -> Viewer(model, v.items, v.start, v.inside) { viewing = null } }
        adding?.let { date ->
            BackHandler { adding = null }
            AddSheet(onPhoto = { pickPhoto(date) }, onSky = { wx -> adding = null; model.addSky(date, wx) }, onCancel = { adding = null })
        }
        cropping?.let { (date, bmp) ->
            BackHandler { cropping = null }
            CropScreen(bmp, onCancel = { cropping = null }) { cropped -> cropping = null; model.addStamp(date, cropped) }
        }

        model.message?.let { msg ->
            LaunchedEffect(msg) { delay(3200); model.message = null }
            Text(
                msg, style = TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 86.dp)
                    .background(Color(0xE61F1A16), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TabBar(current: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(K.surface.copy(alpha = .96f)).navigationBarsPadding().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val on = t == current
            Column(
                Modifier.weight(1f).clickable { onTab(t) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t.glyph, style = TextStyle(fontSize = 18.sp, color = if (on) K.acc else K.muted))
                Text(t.label, maxLines = 1, style = TextStyle(fontSize = 10.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) K.acc else K.muted))
                Box(Modifier.padding(top = 3.dp).size(4.dp).background(if (on) K.acc else Color.Transparent, CircleShape))
            }
        }
    }
}

/** "Add a stamp": a photo from the library, or a drawn stamp of the day's sky in the weather you pick. */
@Composable
private fun AddSheet(onPhoto: () -> Unit, onSky: (com.annales.journal.render.SkyStamp.Weather) -> Unit, onCancel: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x991F1A16)).clickable(onClick = onCancel),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().background(K.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false) { }.navigationBarsPadding().padding(20.dp)
        ) {
            Text("Add a stamp", style = TitleStyle.copy(fontSize = 22.sp))
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp).background(K.page, RoundedCornerShape(16.dp)).clickable(onClick = onPhoto).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🖼", fontSize = 24.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Photo library", style = BodyStyle.copy(fontWeight = FontWeight.Bold))
                    Text("Pick any photo, then frame it", style = SmallStyle)
                }
            }
            Text("THE DAY'S SKY", style = KickerStyle, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            Text("A drawn stamp of the weather and tonight's moon, with hills in the day's mood colour.", style = SmallStyle)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                com.annales.journal.render.SkyStamp.Weather.entries.forEach { wx ->
                    Column(
                        Modifier.weight(1f).background(K.page, RoundedCornerShape(12.dp)).clickable { onSky(wx) }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(wx.glyph, fontSize = 22.sp, color = K.ink)
                        Text(wx.label.substringBefore(" "), style = SmallStyle.copy(fontSize = 10.sp), maxLines = 1)
                    }
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel", color = K.muted) }
        }
    }
}
