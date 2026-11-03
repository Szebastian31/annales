package com.annales.journal.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    var back by rememberSaveable { mutableStateOf(0) }                 // Today tab: 0 = today, up to 3 days back
    var openDay by remember { mutableStateOf<LocalDate?>(null) }        // a past day, opened from the Journal or a collection
    var openColl by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Collection?>(null) }       // id 0 = new
    var viewing by remember { mutableStateOf<Viewing?>(null) }
    var cropping by remember { mutableStateOf<Cropping?>(null) }
    var replacing by remember { mutableStateOf<Stamp?>(null) }        // the stamp a picked photo will replace
    var pickFor by remember { mutableStateOf(LocalDate.now()) }
    var adding by remember { mutableStateOf<LocalDate?>(null) }       // the "photo or sky?" sheet is open for this day
    var addPlace by remember { mutableStateOf("") }                  // where the stamp being added was taken ("" = the day's office)
    val shot = remember { java.io.File(context.cacheDir, "camera/shot.jpg") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val date = pickFor
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { Photos.load(context, uri) }
            if (bmp == null) model.message = "Couldn't open that photo." else cropping = Cropping(date, bmp, replacing, addPlace)
            replacing = null
        }
    }
    fun add(date: LocalDate) { replacing = null; addPlace = ""; adding = date }
    fun replace(date: LocalDate, s: Stamp) { replacing = s; addPlace = s.place; adding = date }
    fun recrop(date: LocalDate, s: Stamp) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { model.original(s) }
            if (bmp == null) model.message = "The original photo isn't kept for this stamp." else cropping = Cropping(date, bmp, s, null)
        }
    }
    fun pickPhoto(date: LocalDate) {
        pickFor = date; adding = null // keeps [replacing] for the picker result
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val date = pickFor
        if (!ok) { replacing = null; return@rememberLauncherForActivityResult }
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { Photos.load(context, android.net.Uri.fromFile(shot)) }
            if (bmp == null) model.message = "Couldn't open the photo you took." else cropping = Cropping(date, bmp, replacing, addPlace)
            replacing = null
        }
    }
    fun takePhoto(date: LocalDate) {
        pickFor = date; adding = null
        shot.parentFile?.mkdirs()
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.annales.journal.camera", shot)
        runCatching { camera.launch(uri) }.onFailure { model.message = "No camera app on this phone."; replacing = null }
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
                        if (!edit && stamps.isNotEmpty()) Viewer(model, stamps.map { day to it }, 0, coll, onEdit = { edit = true }, canReorder = true) { openDay = null }
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
                    tab == Tab.TODAY -> Column {
                        // today, or one of the three days before it, for the days you didn't get to
                        val d = LocalDate.now().minusDays(back.toLong())
                        DaySwitch(model, back) { back = it }
                        DayPage(model, d, modifier = Modifier.weight(1f), onAdd = { add(d) }, onOpen = { viewDay(d, it) }, onOpenPast = { pd, s -> viewDay(pd, s) }, onReplace = { replace(d, it) }, onRecrop = { recrop(d, it) })
                    }
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
            AddSheet(
                model, date, replacing, addPlace, onPlace = { addPlace = it },
                suggested = model.day(date).extras["wx.code"]?.toIntOrNull()?.let { com.annales.journal.data.Siblings.describe(it).second },
                onPhoto = { pickPhoto(date) }, onCamera = { takePhoto(date) },
                onSky = { wx -> adding = null; model.addSky(date, wx, replacing, place = addPlace); replacing = null },
                onCancel = { adding = null; replacing = null },
            )
        }
        cropping?.let { (date, bmp, target, place) ->
            BackHandler { cropping = null }
            CropScreen(bmp, model.cut, startShape = target?.let { model.shapeOf(it) } ?: "portrait", onCancel = { cropping = null }) { cropped -> cropping = null; model.addStamp(date, cropped, replace = target, original = bmp, place = place) }
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

/** Today and the three days before it, so a day you didn't get to can still be added and sealed. A dot: sealed (filled) or started (ring). */
@Composable
private fun DaySwitch(model: AnnalesModel, back: Int, onBack: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        (3 downTo 0).forEach { b ->
            val date = LocalDate.now().minusDays(b.toLong())
            val day = model.day(date)
            val on = b == back
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) K.ink else K.surface)
                    .clickable { onBack(b) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when (b) { 0 -> "Today"; 1 -> "Yesterday"; else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.ENGLISH)) },
                    maxLines = 1, style = SmallStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) K.surface else K.ink),
                )
                val started = day.stamps.isNotEmpty() || day.mood != null
                Box(
                    Modifier.padding(top = 4.dp).size(6.dp)
                        .background(if (day.sealed) (if (on) K.surface else K.green) else Color.Transparent, CircleShape)
                        .border(1.5.dp, if (day.sealed || started) (if (on) K.surface else if (day.sealed) K.green else K.acc) else Color.Transparent, CircleShape),
                )
            }
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

/**
 * "Add a stamp": where it was taken, then a photo (taken now or from the library) or a drawn stamp of the day's sky.
 * The sky choices are the real drawings, in this day's mood colour.
 */
@Composable
private fun AddSheet(
    model: AnnalesModel, date: LocalDate, replacing: Stamp?, place: String, onPlace: (String) -> Unit,
    suggested: com.annales.journal.render.SkyStamp.Weather? = null,
    onPhoto: () -> Unit, onCamera: () -> Unit, onSky: (com.annales.journal.render.SkyStamp.Weather) -> Unit, onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val home = prettyOffice(model.postmarkFor(date).first).substringBefore(" ")
    var choosing by remember { mutableStateOf(false) }
    // today, with location already allowed: start from where you are
    LaunchedEffect(date) {
        if (replacing == null && place.isBlank() && date == LocalDate.now() && canLocate(context))
            com.annales.journal.data.Here.town(context)?.let { t -> if (!t.equals(home, ignoreCase = true)) onPlace(t) }
    }
    val mood = model.day(date).mood?.let { com.annales.journal.data.Moods.color(it) }
    val previews = remember(date, mood) { mutableStateMapOf<com.annales.journal.render.SkyStamp.Weather, ImageBitmap>() }
    LaunchedEffect(date, mood) {
        com.annales.journal.render.SkyStamp.Weather.entries.forEach { wx ->
            val b = withContext(Dispatchers.Default) {
                val full = com.annales.journal.render.SkyStamp.draw(date, wx, mood, (date.toEpochDay() * 31).toInt())
                Bitmap.createScaledBitmap(full, 200, 250, true).also { if (it != full) full.recycle() }
            }
            previews[wx] = b.asImageBitmap()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0x991F1A16)).clickable(onClick = onCancel),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().background(K.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(enabled = false) { }.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp).size(width = 36.dp, height = 4.dp).background(K.line, RoundedCornerShape(2.dp)))
            Text(if (replacing != null) "Replace this stamp" else "Add a stamp", style = TitleStyle.copy(fontSize = 24.sp, lineHeight = 28.sp))

            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().background(K.page, RoundedCornerShape(14.dp)).clickable { choosing = true }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📍", fontSize = 16.sp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("TAKEN IN", style = KickerStyle)
                    Text(place.ifBlank { home }, style = BodyStyle.copy(fontWeight = FontWeight.Bold))
                }
                Text("Change", style = SmallStyle.copy(color = K.acc, fontWeight = FontWeight.Bold))
            }

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                Source("◉", "Take a photo", "Right now", Modifier.weight(1f), onCamera)
                Source("▦", "From library", "Any photo", Modifier.weight(1f), onPhoto)
            }

            Text("OR THE DAY'S SKY", style = KickerStyle, modifier = Modifier.padding(top = 20.dp, bottom = 3.dp))
            Text(
                if (suggested != null) "Consensus Weather says ${suggested.label.lowercase()}. Drawn with that night's moon, in the day's mood colour."
                else "Drawn with that night's moon, in the day's mood colour.",
                style = SmallStyle,
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                com.annales.journal.render.SkyStamp.Weather.entries.forEach { wx ->
                    val on = wx == suggested
                    Column(Modifier.weight(1f).clickable { onSky(wx) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(8.dp)).background(Color(wx.mid))
                                .border(if (on) 2.dp else 0.dp, if (on) K.acc else Color.Transparent, RoundedCornerShape(8.dp)),
                        ) {
                            previews[wx]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Text(
                            wx.label.substringBefore(" "), maxLines = 1, modifier = Modifier.padding(top = 5.dp),
                            style = SmallStyle.copy(fontSize = 12.sp, color = if (on) K.acc else K.ink, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                        )
                    }
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Cancel", color = K.muted) }
        }
    }
    if (choosing) PlaceDialog(place, home, model.placeSuggestions(date), onDismiss = { choosing = false }) { onPlace(it); choosing = false }
}

@Composable
private fun Source(glyph: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) = Column(
    modifier.background(K.ink, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
) {
    Text(glyph, style = TextStyle(fontSize = 22.sp, color = K.surface, fontWeight = FontWeight.Bold))
    Text(title, style = BodyStyle.copy(color = K.surface, fontWeight = FontWeight.ExtraBold), modifier = Modifier.padding(top = 10.dp))
    Text(sub, style = SmallStyle.copy(color = K.surface.copy(alpha = .7f)))
}

/** A photo waiting to be framed: for [date], replacing [target] if set, taken in [place] (null: leave the stamp's place as it is). */
private data class Cropping(val date: LocalDate, val bitmap: Bitmap, val target: Stamp?, val place: String?)

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
