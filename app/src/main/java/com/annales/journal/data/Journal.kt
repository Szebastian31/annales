package com.annales.journal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One stamp of a day. The cropped photo and its rendering live in files named after [id]. */
data class Stamp(val id: String, val title: String = "", val note: String = "")

/** A day in the journal: up to three stamps, how it felt, and whether it has been sealed. */
data class Day(
    val date: LocalDate,
    val stamps: List<Stamp> = emptyList(),
    val mood: Int? = null,
    val sealed: Boolean = false,
    val sealedAt: Long = 0L,
    val office: String = "",
) {
    val canAdd get() = !sealed && stamps.size < MAX_STAMPS
    companion object { const val MAX_STAMPS = 3 }
}

/** Keeps the journal on this phone only: journal.json plus a photo and a rendering per stamp. */
class JournalStore(context: Context) {
    private val root = context.filesDir
    private val file = File(root, "journal.json")
    private val photos = File(root, "photos").apply { mkdirs() }
    private val stamps = File(root, "stamps").apply { mkdirs() }

    fun photoFile(id: String) = File(photos, "$id.jpg")
    fun stampFile(id: String) = File(stamps, "$id.png")

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
        days.values.sortedBy { it.date }.filter { it.stamps.isNotEmpty() || it.mood != null }.forEach { arr.put(writeDay(it)) }
        val tmp = File(root, "journal.json.tmp")
        tmp.writeText(JSONObject().put("version", 1).put("days", arr).toString())
        tmp.renameTo(file)
    }

    fun delete(stamp: Stamp) {
        photoFile(stamp.id).delete()
        stampFile(stamp.id).delete()
    }

    private fun readDay(o: JSONObject): Day {
        val s = o.optJSONArray("stamps") ?: JSONArray()
        return Day(
            date = LocalDate.parse(o.getString("date")),
            stamps = (0 until s.length()).map {
                val j = s.getJSONObject(it)
                Stamp(j.getString("id"), j.optString("title"), j.optString("note"))
            },
            mood = if (o.has("mood")) o.getInt("mood") else null,
            sealed = o.optBoolean("sealed"),
            sealedAt = o.optLong("sealedAt"),
            office = o.optString("office"),
        )
    }

    private fun writeDay(d: Day) = JSONObject().apply {
        put("date", d.date.toString())
        put("stamps", JSONArray().apply { d.stamps.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("note", it.note)) } })
        d.mood?.let { put("mood", it) }
        put("sealed", d.sealed)
        put("sealedAt", d.sealedAt)
        put("office", d.office)
    }
}
