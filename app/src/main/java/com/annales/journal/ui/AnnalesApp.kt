package com.annales.journal.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.drawscope.scale
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
    var cropping by remember { mutableStateOf<Cropping?>(null) }
    var replacing by remember { mutableStateOf<Stamp?>(null) }        // the stamp a picked photo will replace
    var pickFor by remember { mutableStateOf(LocalDate.now()) }
    var adding by remember { mutableStateOf<LocalDate?>(null) }       // the "photo or sky?" sheet is open for this day

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val date = pickFor
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { Photos.load(context, uri) }
            if (bmp == null) model.message = "Couldn't open that photo." else cropping = Cropping(date, bmp, replacing)
            replacing = null
        }
    }
    fun add(date: LocalDate) { replacing = null; adding = date }
    fun replace(date: LocalDate, s: Stamp) { replacing = s; adding = date }
    fun recrop(date: LocalDate, s: Stamp) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { model.original(s) }
            if (bmp == null) model.message = "The original photo isn't kept for this stamp." else cropping = Cropping(date, bmp, s)
        }
    }
    fun pickPhoto(date: LocalDate) {
        pickFor = date; adding = null // keeps [replacing] for the picker result
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
    // the seal thunk
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(model.sealedTick) {
        if (model.sealedTick > 0 && model.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }

    BackHandler(enabled = !overlay && (openDay != null || openColl != null)) { if (openDay != null) openDay = null else openColl = null }

    Box(Modifier.fillMaxSize().background(K.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                val day = openDay
                val coll = openColl?.let { id -> model.collections.firstOrNull { it.id == id } }
                when {
                    day != null -> {
                        // the Journal opens a day on its stamps, full screen; Edit (or a day with no stamps yet) shows the day page
                        val stamps = model.day(day).stamps
                        var edit by remember(day) { mutableStateOf(stamps.isEmpty()) }
                        if (!edit && stamps.isNotEmpty()) Viewer(model, stamps.map { day to it }, 0, coll, onEdit = { edit = true }) { openDay = null }
                        else Column {
                            BackHandler(enabled = stamps.isNotEmpty() && !overlay) { edit = false }
                            TextButton(onClick = { if (stamps.isNotEmpty()) edit = false else openDay = null }, modifier = Modifier.padding(start = 8.dp)) {
                                Text(if (stamps.isNotEmpty()) "‹ Stamp" else "‹ ${coll?.name ?: "Journal"}", color = K.acc)
                            }
                            DayPage(model, day, onAdd = { add(day) }, onOpen = { viewDay(day, it) }, onOpenPast = { d, s -> viewDay(d, s) }, onReplace = { replace(day, it) }, onRecrop = { recrop(day, it) })
                        }
                    }
                    coll != null -> CollectionScreen(
                        model, coll,
                        onBack = { openColl = null }, onEdit = { editing = coll },
                        onOpenStamp = { _, s, all -> viewing = Viewing(all, all.indexOfFirst { it.second.id == s.id }, coll) },
                        onOpenDay = { if (it == LocalDate.now()) { openColl = null; tab = Tab.TODAY } else openDay = it },
                    )
                    tab == Tab.TODAY -> DayPage(model, LocalDate.now(), onAdd = { add(LocalDate.now()) }, onOpen = { viewDay(LocalDate.now(), it) }, onOpenPast = { d, s -> viewDay(d, s) }, onReplace = { replace(LocalDate.now(), it) }, onRecrop = { recrop(LocalDate.now(), it) })
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
            AddSheet(suggested = model.day(date).extras["wx.code"]?.toIntOrNull()?.let { com.annales.journal.data.Siblings.describe(it).second }, onPhoto = { pickPhoto(date) }, onSky = { wx -> adding = null; model.addSky(date, wx, replacing); replacing = null }, onCancel = { adding = null; replacing = null })
        }
        cropping?.let { (date, bmp, target) ->
            BackHandler { cropping = null }
            CropScreen(bmp, model.cut, startShape = target?.let { model.shapeOf(it) } ?: "portrait", onCancel = { cropping = null }) { cropped -> cropping = null; model.addStamp(date, cropped, replace = target, original = bmp) }
        }

        model.message?.let { msg ->
            LaunchedEffect(msg) { delay(3200); model.message = null }
            Text(
                msg, style = TextStyle(fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp),
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
                TabIcon(t, if (on) K.acc else K.muted)
                Text(t.label, maxLines = 1, style = TextStyle(fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) K.acc else K.muted))
                Box(Modifier.padding(top = 3.dp).size(4.dp).background(if (on) K.acc else Color.Transparent, CircleShape))
            }
        }
    }
}

/** "Add a stamp": a photo from the library, or a drawn stamp of the day's sky in the weather you pick. */
@Composable
private fun AddSheet(suggested: com.annales.journal.render.SkyStamp.Weather? = null, onPhoto: () -> Unit, onSky: (com.annales.journal.render.SkyStamp.Weather) -> Unit, onCancel: () -> Unit) {
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
            Text(if (suggested != null) "Consensus Weather says ${suggested.label.lowercase()} that day. A drawn stamp of the weather and that night's moon, with hills in the mood colour." else "A drawn stamp of the weather and tonight's moon, with hills in the day's mood colour.", style = SmallStyle)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                com.annales.journal.render.SkyStamp.Weather.entries.forEach { wx ->
                    Column(
                        Modifier.weight(1f).background(if (wx == suggested) K.accSoft else K.page, RoundedCornerShape(12.dp))
                            .border(if (wx == suggested) 1.5.dp else 0.dp, if (wx == suggested) K.acc else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onSky(wx) }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(wx.glyph, fontSize = 22.sp, color = K.ink)
                        Text(wx.label.substringBefore(" "), style = SmallStyle.copy(fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel", color = K.muted) }
        }
    }
}

/** A photo waiting to be framed: for [date], replacing [target] if set. */
private data class Cropping(val date: LocalDate, val bitmap: Bitmap, val target: Stamp?)

/** The mockup's tab icons, drawn as 1.6-wide lines on a 24 grid. Dashed outlines stand for perforated stamps. */
@Composable
private fun TabIcon(t: Tab, c: Color) = androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
    val s = size.width / 24f
    scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
        val line = androidx.compose.ui.graphics.drawscope.Stroke(1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun dash(on: Float, off: Float) = androidx.compose.ui.graphics.drawscope.Stroke(1.6f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(on, off)))
        fun p(d: String) = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, style: androidx.compose.ui.graphics.drawscope.DrawStyle = line) =
            drawRoundRect(c, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(w, h),
                androidx.compose.ui.geometry.CornerRadius(r), style = style)
        fun ring(x: Float, y: Float, r: Float) = drawCircle(c, r, androidx.compose.ui.geometry.Offset(x, y), style = line)
        when (t) {
            Tab.TODAY -> { rect(5f, 4f, 14f, 17f, 1.5f, dash(2f, 2f)); ring(12f, 12f, 3.2f) }
            Tab.JOURNAL -> { rect(4f, 5f, 16f, 15f, 2f); drawPath(p("M4 9.5h16M9 3v4M15 3v4"), c, style = line); rect(7f, 12f, 3f, 3.5f, 0f); rect(13.5f, 12f, 3f, 3.5f, 0f) }
            Tab.COLLECTIONS -> { rect(7f, 3f, 13f, 15f, 1.5f, dash(2f, 1.6f)); drawPath(p("M4 7v13.5c0 .3.2.5.5.5H16"), c, style = line) }
            Tab.INSIGHTS -> drawPath(p("M4 19h16M6 16c2-6 4-9 6-6s4 1 6-5"), c, style = line)
            Tab.PROFILE -> { ring(12f, 8.5f, 3.8f); drawPath(p("M4.5 20c1.3-3.6 4-5.3 7.5-5.3s6.2 1.7 7.5 5.3"), c, style = line) }
        }
    }
}
