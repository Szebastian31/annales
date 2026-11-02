package com.annales.journal

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.annales.journal.data.CollType
import com.annales.journal.data.Collection
import com.annales.journal.data.CollectionStore
import com.annales.journal.data.Day
import com.annales.journal.data.Ink
import com.annales.journal.data.JournalStore
import com.annales.journal.data.Moods
import com.annales.journal.data.Places
import com.annales.journal.data.Settings
import com.annales.journal.data.Stamp
import com.annales.journal.render.Photos
import com.annales.journal.render.Seal
import com.annales.journal.render.StampRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class AnnalesModel(app: Application) : AndroidViewModel(app) {
    private val store = JournalStore(app)
    private val collStore = CollectionStore(app)
    val settings = Settings(app)

    var days by mutableStateOf<Map<LocalDate, Day>>(emptyMap()); private set
    var collections by mutableStateOf<List<Collection>>(emptyList()); private set
    /** Bumped whenever a stamp's picture changes, so screens reload it. */
    var version by mutableIntStateOf(0); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    // settings mirrored as state so the Profile screen redraws
    var office by mutableStateOf(settings.office); private set
    var postcode by mutableStateOf(settings.postcode); private set
    var ink by mutableStateOf(settings.ink); private set
    var cut by mutableStateOf(settings.cut); private set
    var saveToPhotos by mutableStateOf(settings.saveToPhotos); private set
    var reminder by mutableStateOf(settings.reminder); private set
    var name by mutableStateOf(settings.name); private set
    var keepOriginals by mutableStateOf(settings.keepOriginals); private set
    var haptics by mutableStateOf(settings.haptics); private set
    /** Bumped on each seal, so the screen can play the thunk. */
    var sealedTick by mutableIntStateOf(0); private set

    init {
        if (settings.since.isEmpty()) settings.since = LocalDate.now().toString()
        com.annales.journal.ui.K.look = runCatching { com.annales.journal.ui.Look.valueOf(settings.look) }.getOrDefault(com.annales.journal.ui.Look.KRAFT)
        viewModelScope.launch {
            days = withContext(Dispatchers.IO) { store.load() }
            redrawOldSeals()
            cleanGallery()
        }
        viewModelScope.launch { collections = withContext(Dispatchers.IO) { collStore.load() } }
    }

    /**
     * Stamps sealed before 0.5.0 were saved without a margin, so their postmarks stop at the paper's edge. Redraw them once
     * with the postmark they were given (office, text underneath, date, hour, ink), so the ring and wavy lines hang off the edges.
     */
    private suspend fun redrawOldSeals() {
        val old = withContext(Dispatchers.IO) {
            days.values.filter { it.sealed }.flatMap { d -> d.stamps.map { d to it } }.filter { (_, s) ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(store.stampFile(s.id).path, o)
                o.outWidth in 1..StampRenderer.W
            }
        }
        if (old.isEmpty()) return
        withContext(Dispatchers.Default) {
            old.forEach { (d, s) ->
                val photo = BitmapFactory.decodeFile(store.photoFile(s.id).path) ?: return@forEach
                val hour = if (d.sealedAt > 0) java.time.Instant.ofEpochMilli(d.sealedAt).atZone(java.time.ZoneId.systemDefault()).hour else 12
                val sealInk = d.ink?.let { runCatching { Ink.valueOf(it) }.getOrNull() } ?: Ink.OFFICIAL
                val seal = Seal(d.date, hour, d.office.ifBlank { office }, d.bottom.ifBlank { postcode }, sealInk.argb, s.id.hashCode() and 0xFFFF)
                Photos.writePng(StampRenderer.render(photo, cut, seal, s.id.hashCode()), store.stampFile(s.id))
                photo.recycle()
            }
        }
        version++
    }

    /** Once, after 0.5.1: swap the inked copies already in Photos for clean stamps, in place (no new copies). */
    private suspend fun cleanGallery() {
        if (settings.cleanedGallery) return
        withContext(Dispatchers.Default) {
            days.values.flatMap { it.stamps }.filter { it.gallery.isNotBlank() }.forEach { s ->
                val photo = BitmapFactory.decodeFile(store.photoFile(s.id).path) ?: return@forEach
                val clean = StampRenderer.clean(photo, cut, s.id.hashCode())
                val tmp = java.io.File(getApplication<Application>().cacheDir, "${s.id}.png")
                Photos.writePng(clean, tmp)
                Photos.overwrite(getApplication<Application>(), tmp, s.gallery)
                tmp.delete(); clean.recycle(); photo.recycle()
            }
        }
        settings.cleanedGallery = true
    }

    val since: LocalDate get() = runCatching { LocalDate.parse(settings.since) }.getOrDefault(LocalDate.now())
    fun day(date: LocalDate) = days[date] ?: Day(date)

    private fun put(d: Day) {
        days = days + (d.date to d)
        val snapshot = days
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    /** Any change after sealing marks the day, so the Seal button offers "Save & re-seal". */
    private fun edit(date: LocalDate, change: (Day) -> Day) = day(date).let { d -> put(change(d).copy(dirty = d.sealed)) }

    fun setMood(date: LocalDate, mood: Int) = setMoodV(date, mood * 100 / 6)

    /** Sets the slider position. Moving into a different group of feelings (unpleasant, neutral, pleasant) clears the old ones. */
    fun setMoodV(date: LocalDate, v: Int) = edit(date) { d ->
        val lv = Moods.levelOf(v)
        val keep = d.mood == null || com.annales.journal.data.Tags.feelsFor(d.mood) === com.annales.journal.data.Tags.feelsFor(lv)
        d.copy(mood = lv, moodV = v, feels = if (keep) d.feels else emptyList())
    }

    fun toggleFactor(date: LocalDate, f: String) = edit(date) { d -> d.copy(factors = if (f in d.factors) d.factors - f else d.factors + f) }

    fun toggleFeel(date: LocalDate, f: String) = edit(date) { d -> d.copy(feels = if (f in d.feels) d.feels - f else d.feels + f) }

    fun setLine(date: LocalDate, text: String) = edit(date) { it.copy(line = text.take(120)) }

    /** This day's ink, falling back to a trip's ink, then the default in Profile. */
    fun inkOn(date: LocalDate): Ink = day(date).ink?.let { runCatching { Ink.valueOf(it) }.getOrNull() } ?: postmarkFor(date).third

    fun setDayInk(date: LocalDate, v: Ink) = edit(date) { it.copy(ink = v.name) }

    /** Saves the cropped photo and its perforated draft; [replace] swaps an existing stamp's picture instead of adding one. */
    fun addStamp(date: LocalDate, cropped: Bitmap, replace: Stamp? = null, original: Bitmap? = null) {
        val d = day(date)
        if (replace == null && !d.canAdd) return
        val s = replace ?: Stamp(UUID.randomUUID().toString().take(12))
        work({
            Photos.writeJpeg(cropped, store.photoFile(s.id))
            if (original != null && keepOriginals) Photos.writeJpeg(original, store.originalFile(s.id)) else store.originalFile(s.id).delete() // a drawn sky has nothing to re-crop
            val draft = StampRenderer.render(cropped, cut, null, s.id.hashCode())
            Photos.writePng(draft, store.stampFile(s.id))
        }, done = {
            edit(date) { it.copy(stamps = if (replace != null) it.stamps else it.stamps + s) }
            version++
        })
    }

    /** The full photo a stamp was cut from, for re-cropping. */
    fun original(s: Stamp): Bitmap? = BitmapFactory.decodeFile(store.originalFile(s.id).path)
    fun hasOriginal(s: Stamp) = store.originalFile(s.id).exists()

    fun removeStamp(date: LocalDate, s: Stamp) {
        edit(date) { d -> d.copy(stamps = d.stamps.filterNot { it.id == s.id }) }
        if (collections.any { s.id in it.ids || it.cover == s.id })
            putCollections(collections.map { it.copy(ids = it.ids - s.id, cover = it.cover.takeIf { c -> c != s.id }) })
        viewModelScope.launch(Dispatchers.IO) { store.delete(s) }
    }

    fun updateStamp(date: LocalDate, s: Stamp) {
        val d = day(date)
        put(d.copy(stamps = d.stamps.map { if (it.id == s.id) s else it }))
    }

    /**
     * Presses the postmark on every stamp of the day and puts each one into Photos. Re-sealing an edited day keeps the
     * postmark it was first given (office, date and hour: what actually happened); only the ink and the stamps can change.
     */
    fun seal(date: LocalDate) {
        val d = day(date)
        if (d.stamps.isEmpty() || d.mood == null || (d.sealed && !d.dirty)) return
        val first = !d.sealed
        val hour = if (first) LocalTime.now().hour
            else java.time.Instant.ofEpochMilli(d.sealedAt).atZone(java.time.ZoneId.systemDefault()).hour
        val (pmMark, pmBottom, _) = postmarkFor(date)
        val mark = if (first) pmMark else d.office.ifBlank { pmMark }
        val bottom = if (first) pmBottom else d.bottom.ifBlank { pmBottom }
        val sealInk = inkOn(date)
        val links = mutableMapOf<String, String>()
        work({
            d.stamps.forEach { s ->
                val photo = BitmapFactory.decodeFile(store.photoFile(s.id).path) ?: return@forEach
                val seal = Seal(date, hour, mark, bottom, sealInk.argb, s.id.hashCode() and 0xFFFF)
                val png = store.stampFile(s.id)
                Photos.writePng(StampRenderer.render(photo, cut, seal, s.id.hashCode()), png)
                if (saveToPhotos) {
                    // Photos gets the clean stamp: perforated paper only, no postmark ink, no margin
                    val clean = StampRenderer.clean(photo, cut, s.id.hashCode())
                    val tmp = java.io.File(getApplication<Application>().cacheDir, "${s.id}.png")
                    Photos.writePng(clean, tmp)
                    clean.recycle()
                    Photos.saveToGallery(getApplication<Application>(), tmp, "annales-$date-${s.id.take(4)}", s.gallery)?.let { links[s.id] = it }
                    tmp.delete()
                }
                photo.recycle()
            }
        }, done = {
            val now = day(date)
            put(now.copy(
                sealed = true, dirty = false, sealedAt = if (first) System.currentTimeMillis() else now.sealedAt,
                office = mark, bottom = bottom, ink = sealInk.name,
                stamps = now.stamps.map { s -> links[s.id]?.let { s.copy(gallery = it) } ?: s },
            ))
            version++
            val n = d.stamps.size
            sealedTick++
            message = when {
                !saveToPhotos -> if (first) "Sealed." else "Re-sealed."
                links.size == n -> "$n stamp${if (n > 1) "s" else ""} ${if (first) "saved to" else "updated in"} Photos › Annales"
                else -> "Sealed. Some stamps couldn't go to Photos on this phone."
            }
        })
    }

    // ---- collections ----

    /** The trip this day falls in, if any: its city's post office stamps the day. */
    fun tripOn(date: LocalDate) = collections.firstOrNull { it.type == CollType.TRIP && it.covers(date) }

    /** Office over the top, text underneath, and ink, for sealing [date]. */
    fun postmarkFor(date: LocalDate): Triple<String, String, Ink> {
        val trip = tripOn(date) ?: return Triple(office, postcode, ink)
        val p = Places.of(trip.place)
        val tripInk = trip.ink?.let { runCatching { Ink.valueOf(it) }.getOrNull() } ?: Ink.OFFICIAL
        return Triple(p.mark, p.bottom, tripInk)
    }

    /** A collection's days in order, each with the stamps it holds. Trips, events and anniversaries hold sealed days only. */
    fun entries(c: Collection): List<Pair<Day, List<Stamp>>> = when (c.type) {
        CollType.THEME -> days.values.sortedBy { it.date }
            .map { d -> d to d.stamps.filter { it.id in c.ids } }.filter { it.second.isNotEmpty() }
        CollType.ANNIV -> days.values.filter { c.covers(it.date) }.sortedBy { it.date }
            .map { d -> d to if (d.sealed) d.stamps else emptyList() }
        else -> generateSequence(c.from) { it.plusDays(1) }.takeWhile { !it.isAfter(c.to) }.take(366)
            .map { date -> day(date).let { d -> d to if (d.sealed) d.stamps else emptyList() } }.toList()
    }

    fun count(c: Collection) = entries(c).sumOf { it.second.size }

    /** The chosen cover if it still belongs, else the first stamp. */
    fun cover(c: Collection): Pair<Day, Stamp>? {
        val all = entries(c).flatMap { (d, ss) -> ss.map { d to it } }
        return all.firstOrNull { it.second.id == c.cover } ?: all.firstOrNull()
    }

    fun findStamp(id: String): Pair<Day, Stamp>? =
        days.values.firstNotNullOfOrNull { d -> d.stamps.firstOrNull { it.id == id }?.let { d to it } }

    private fun putCollections(list: List<Collection>) {
        collections = list
        viewModelScope.launch(Dispatchers.IO) { collStore.save(list) }
    }

    /** Creates or replaces; returns the saved collection. */
    fun saveCollection(c: Collection): Collection {
        val saved = if (c.id == 0L) c.copy(id = System.currentTimeMillis()) else c
        putCollections(collections.filterNot { it.id == saved.id } + saved)
        return saved
    }

    fun deleteCollection(c: Collection) = putCollections(collections.filterNot { it.id == c.id })

    /** Adds or removes a stamp from a theme. */
    fun toggleInTheme(theme: Collection, stampId: String) {
        val ids = if (stampId in theme.ids) theme.ids - stampId else theme.ids + stampId
        saveCollection(theme.copy(ids = ids, cover = if (theme.cover == stampId && stampId !in ids) null else theme.cover))
    }

    fun setCover(c: Collection, stampId: String) = saveCollection(c.copy(cover = stampId))

    fun stampImage(id: String): ImageBitmap? =
        BitmapFactory.decodeFile(store.stampFile(id).path)?.asImageBitmap()

    // ---- settings ----
    fun changeLook(v: com.annales.journal.ui.Look) { com.annales.journal.ui.K.look = v; settings.look = v.name }

    /** A drawn stamp of the day's sky instead of a photo: the weather you pick, that night's moon, hills in the mood colour. */
    fun addSky(date: LocalDate, wx: com.annales.journal.render.SkyStamp.Weather, replace: Stamp? = null) {
        val mood = day(date).mood?.let { com.annales.journal.data.Moods.color(it) }
        val art = com.annales.journal.render.SkyStamp.draw(date, wx, mood, (date.toEpochDay() * 31 + day(date).stamps.size).toInt())
        addStamp(date, art, replace)
    }

    /** The same date in earlier years, newest first, for "On this day". Only days with stamps. */
    fun onThisDay(date: LocalDate): List<Day> = days.values
        .filter { it.date.year < date.year && it.date.monthValue == date.monthValue && it.date.dayOfMonth == date.dayOfMonth && it.stamps.isNotEmpty() }
        .sortedByDescending { it.date }

    fun changeOffice(v: String) { office = v; settings.office = v }
    fun changePostcode(v: String) { postcode = v; settings.postcode = v }
    fun changeInk(v: Ink) { ink = v; settings.ink = v }
    fun changeName(v: String) { name = v.take(40); settings.name = name }
    fun changeKeepOriginals(v: Boolean) { keepOriginals = v; settings.keepOriginals = v }
    fun changeHaptics(v: Boolean) { haptics = v; settings.haptics = v }
    fun changeSaveToPhotos(v: Boolean) { saveToPhotos = v; settings.saveToPhotos = v }
    fun changeReminder(v: Boolean) { reminder = v; settings.reminder = v; com.annales.journal.reminder.Reminder.schedule(getApplication<Application>()) }

    /** A new perforation re-cuts the drafts; sealed stamps keep the edge they were sealed with. */
    fun changeCut(v: String) {
        if (v == cut) return
        cut = v; settings.cut = v
        val drafts = days.values.filter { !it.sealed }.flatMap { it.stamps }
        work({
            drafts.forEach { s ->
                val photo = BitmapFactory.decodeFile(store.photoFile(s.id).path) ?: return@forEach
                Photos.writePng(StampRenderer.render(photo, v, null, s.id.hashCode()), store.stampFile(s.id))
                photo.recycle()
            }
        }, done = { version++ })
    }

    /** Heavy drawing off the main thread; [done] runs back on it, only if the work succeeded. */
    private fun work(block: suspend () -> Unit, done: () -> Unit) = viewModelScope.launch {
        busy = true
        val ok = try { withContext(Dispatchers.Default) { block() }; true } catch (e: Exception) {
            message = "Something went wrong: ${e.message ?: e.javaClass.simpleName}"; false
        } finally { busy = false }
        if (ok) done()
    }
}
