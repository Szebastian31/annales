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
import com.annales.journal.data.Day
import com.annales.journal.data.Ink
import com.annales.journal.data.JournalStore
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
    val settings = Settings(app)

    var days by mutableStateOf<Map<LocalDate, Day>>(emptyMap()); private set
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

    init {
        if (settings.since.isEmpty()) settings.since = LocalDate.now().toString()
        viewModelScope.launch { days = withContext(Dispatchers.IO) { store.load() } }
    }

    val since: LocalDate get() = runCatching { LocalDate.parse(settings.since) }.getOrDefault(LocalDate.now())
    fun day(date: LocalDate) = days[date] ?: Day(date)

    private fun put(d: Day) {
        days = days + (d.date to d)
        val snapshot = days
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    fun setMood(date: LocalDate, mood: Int) = put(day(date).copy(mood = mood))

    /** Saves the cropped photo and its perforated draft. */
    fun addStamp(date: LocalDate, cropped: Bitmap) {
        val d = day(date)
        if (!d.canAdd) return
        val s = Stamp(UUID.randomUUID().toString().take(12))
        work({
            Photos.writeJpeg(cropped, store.photoFile(s.id))
            val draft = StampRenderer.render(cropped, cut, null)
            Photos.writePng(draft, store.stampFile(s.id))
        }, done = { put(day(date).let { it.copy(stamps = it.stamps + s) }); version++ })
    }

    fun removeStamp(date: LocalDate, s: Stamp) {
        val d = day(date)
        if (d.sealed) return
        put(d.copy(stamps = d.stamps.filterNot { it.id == s.id }))
        viewModelScope.launch(Dispatchers.IO) { store.delete(s) }
    }

    fun updateStamp(date: LocalDate, s: Stamp) {
        val d = day(date)
        put(d.copy(stamps = d.stamps.map { if (it.id == s.id) s else it }))
    }

    /** Presses the postmark on every stamp of the day, and puts each one into Photos. */
    fun seal(date: LocalDate) {
        val d = day(date)
        if (d.sealed || d.stamps.isEmpty()) return
        val hour = LocalTime.now().hour
        var saved = 0
        work({
            d.stamps.forEach { s ->
                val photo = BitmapFactory.decodeFile(store.photoFile(s.id).path) ?: return@forEach
                val seal = Seal(date, hour, office, postcode, ink.argb, s.id.hashCode() and 0xFFFF)
                val png = store.stampFile(s.id)
                Photos.writePng(StampRenderer.render(photo, cut, seal), png)
                photo.recycle()
                if (saveToPhotos && Photos.saveToGallery(getApplication<Application>(), png, "annales-$date-${s.id.take(4)}")) saved++
            }
        }, done = {
            put(day(date).copy(sealed = true, sealedAt = System.currentTimeMillis(), office = office))
            version++
            message = when {
                !saveToPhotos -> "Sealed."
                saved == d.stamps.size -> "Sealed, and saved to Photos › Annales."
                else -> "Sealed. Some stamps couldn't go to Photos on this phone."
            }
        })
    }

    fun stampImage(id: String): ImageBitmap? =
        BitmapFactory.decodeFile(store.stampFile(id).path)?.asImageBitmap()

    // ---- settings ----
    fun changeOffice(v: String) { office = v; settings.office = v }
    fun changePostcode(v: String) { postcode = v; settings.postcode = v }
    fun changeInk(v: Ink) { ink = v; settings.ink = v }
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
                Photos.writePng(StampRenderer.render(photo, v, null), store.stampFile(s.id))
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
