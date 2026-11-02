package com.annales.journal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One stamp of a day. The cropped photo and its rendering live in files named after [id]. */
data class Stamp(val id: String, val title: String = "", val note: String = "", val gallery: String = "")

/** A day in the journal: up to three stamps, how it felt, and whether it has been sealed. */
data class Day(
    val date: LocalDate,
    val stamps: List<Stamp> = emptyList(),
    val mood: Int? = null,
    val sealed: Boolean = false,
    val sealedAt: Long = 0L,
    val office: String = "",
    val factors: List<String> = emptyList(),
    val feels: List<String> = emptyList(),
    /** Where the slider sits, 0..100; [mood] is the nearest of the seven steps. */
    val moodV: Int? = null,
    /** "One line" about the day, up to 120 characters. */
    val line: String = "",
    /** This day's postmark ink ([Ink] name), or null for the default from Profile. */
    val ink: String? = null,
    /** What was printed under the office name when it was sealed. */
    val bottom: String = "",
    /** Changed since it was sealed: the Seal button offers "Save & re-seal". */
    val dirty: Boolean = false,
    /** What the sibling apps added: wx.* (weather), music.*, money.*, augur.* — frozen when the day is sealed. */
    val extras: Map<String, String> = emptyMap(),
) {
    val canAdd get() = stamps.size < MAX_STAMPS
    companion object { const val MAX_STAMPS = 3 }
}

/** Keeps the journal on this phone only: journal.json plus a photo and a rendering per stamp. */
class JournalStore(context: Context) {
    private val root = context.filesDir
    private val file = File(root, "journal.json")
    private val photos = File(root, "photos").apply { mkdirs() }
    private val stamps = File(root, "stamps").apply { mkdirs() }
    private val originals = File(root, "originals").apply { mkdirs() }

    fun photoFile(id: String) = File(photos, "$id.jpg")
    fun stampFile(id: String) = File(stamps, "$id.png")
    fun originalFile(id: String) = File(originals, "$id.jpg")

    fun load(): Map<LocalDate, Day> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val arr = JSONObject(file.readText()).getJSONArray("days")
            (0 until arr.length()).map { readDay(arr.getJSONObject(it)) }.associateBy { it.date }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun save(days: Map<LocalDate, Day>) {
        val arr = JSONArray()
        days.values.sortedBy { it.date }.filter { it.stamps.isNotEmpty() || it.mood != null || it.factors.isNotEmpty() || it.feels.isNotEmpty() || it.line.isNotBlank() }.forEach { arr.put(writeDay(it)) }
        val tmp = File(root, "journal.json.tmp")
        tmp.writeText(JSONObject().put("version", 1).put("days", arr).toString())
        tmp.renameTo(file)
    }

    fun delete(stamp: Stamp) {
        photoFile(stamp.id).delete()
        stampFile(stamp.id).delete()
        originalFile(stamp.id).delete()
    }

    private fun readDay(o: JSONObject): Day {
        val s = o.optJSONArray("stamps") ?: JSONArray()
        return Day(
            date = LocalDate.parse(o.getString("date")),
            stamps = (0 until s.length()).map {
                val j = s.getJSONObject(it)
                Stamp(j.getString("id"), j.optString("title"), j.optString("note"), j.optString("gallery"))
            },
            mood = if (o.has("mood")) o.getInt("mood") else null,
            sealed = o.optBoolean("sealed"),
            sealedAt = o.optLong("sealedAt"),
            office = o.optString("office"),
            factors = o.optJSONArray("factors").strings(),
            feels = o.optJSONArray("feels").strings(),
            moodV = if (o.has("moodV")) o.getInt("moodV") else if (o.has("mood")) o.getInt("mood") * 100 / 6 else null,
            line = o.optString("line"),
            ink = if (o.has("ink")) o.getString("ink") else null,
            bottom = o.optString("bottom"),
            dirty = o.optBoolean("dirty"),
            extras = o.optJSONObject("extras")?.let { e -> e.keys().asSequence().associateWith { e.getString(it) } } ?: emptyMap(),
        )
    }

    private fun writeDay(d: Day) = JSONObject().apply {
        put("date", d.date.toString())
        put("stamps", JSONArray().apply { d.stamps.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("note", it.note).put("gallery", it.gallery)) } })
        d.mood?.let { put("mood", it) }
        put("sealed", d.sealed)
        put("sealedAt", d.sealedAt)
        put("office", d.office)
        put("factors", JSONArray(d.factors))
        put("feels", JSONArray(d.feels))
        d.moodV?.let { put("moodV", it) }
        put("line", d.line)
        d.ink?.let { put("ink", it) }
        put("bottom", d.bottom)
        put("dirty", d.dirty)
        put("extras", JSONObject(d.extras))
    }
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
