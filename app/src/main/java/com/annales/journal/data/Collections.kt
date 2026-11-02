package com.annales.journal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

enum class CollType(val label: String, val plural: String, val glyph: String, val about: String) {
    TRIP("Trip", "Trips", "✈", "Dates and a place. Stamps get that city's postmark."),
    EVENT("Event", "Events", "✦", "A day or a few: a wedding, a launch, a concert."),
    ANNIV("Anniversary", "Anniversaries", "↻", "One date, every year. Grows over time."),
    THEME("Theme", "Themes", "❖", "Stamps you pick by hand, like Coffee."),
}

/**
 * A collection. Trips and events cover [from]..[to]; an anniversary is [month]/[day] every year; a theme holds the
 * stamps in [ids]. [ink] is an [Ink] name, or null for the local post office's own (black).
 */
data class Collection(
    val id: Long,
    val type: CollType,
    val name: String,
    val place: String = "",
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val month: Int = LocalDate.now().monthValue,
    val day: Int = LocalDate.now().dayOfMonth,
    val ink: String? = null,
    val note: String = "",
    val ids: List<String> = emptyList(),
    val cover: String? = null,
) {
    fun covers(date: LocalDate) = when (type) {
        CollType.TRIP, CollType.EVENT -> !date.isBefore(from) && !date.isAfter(to)
        CollType.ANNIV -> date.monthValue == month && date.dayOfMonth == day
        CollType.THEME -> false
    }
}

/** What a post office prints round its date stamp. Unknown places get their name in capitals. */
data class Place(val mark: String, val bottom: String, val region: String, val post: String)

object Places {
    private val known = mapOf(
        "Gliwice" to Place("GLIWICE 1", "44-100", "Silesia", "Poczta Polska"),
        "Kraków" to Place("KRAKÓW 1", "POCZTA POLSKA", "Lesser Poland", "Poczta Polska"),
        "Gdańsk" to Place("GDAŃSK 1", "POCZTA POLSKA", "Pomerania", "Poczta Polska"),
        "Lisboa" to Place("LISBOA", "CTT", "Lisbon", "CTT Correios"),
        "Roma" to Place("ROMA", "POSTE ITALIANE", "Lazio", "Poste Italiane"),
        "Paris" to Place("PARIS", "LA POSTE", "Île-de-France", "La Poste"),
        "Kyoto" to Place("KYOTO", "JAPAN POST", "Kansai", "Japan Post"),
        "Reykjavík" to Place("REYKJAVÍK", "PÓSTURINN", "Iceland", "Pósturinn"),
    )
    private val alias = mapOf("Lisbon" to "Lisboa", "Rome" to "Roma", "Krakow" to "Kraków", "Gdansk" to "Gdańsk", "Reykjavik" to "Reykjavík")
    val suggestions = listOf("Kraków", "Gdańsk", "Roma", "Paris", "Kyoto", "Reykjavík")

    fun of(name: String): Place {
        val n = name.trim()
        val key = alias[n] ?: known.keys.firstOrNull { it.equals(n, ignoreCase = true) }
        return key?.let { known[it] } ?: Place(n.uppercase(), "", n, "local post office")
    }
}

class CollectionStore(context: Context) {
    private val file = File(context.filesDir, "collections.json")

    fun load(): List<Collection> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(file.readText()).getJSONArray("collections")
            (0 until arr.length()).map { read(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(list: List<Collection>) {
        val arr = JSONArray().apply { list.forEach { put(write(it)) } }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject().put("version", 1).put("collections", arr).toString())
        tmp.renameTo(file)
    }

    private fun read(o: JSONObject): Collection {
        val ids = o.optJSONArray("ids") ?: JSONArray()
        return Collection(
            id = o.getLong("id"),
            type = runCatching { CollType.valueOf(o.getString("type")) }.getOrDefault(CollType.THEME),
            name = o.optString("name"),
            place = o.optString("place"),
            from = runCatching { LocalDate.parse(o.getString("from")) }.getOrDefault(LocalDate.now()),
            to = runCatching { LocalDate.parse(o.getString("to")) }.getOrDefault(LocalDate.now()),
            month = o.optInt("month", 1),
            day = o.optInt("day", 1),
            ink = if (o.has("ink")) o.getString("ink") else null,
            note = o.optString("note"),
            ids = (0 until ids.length()).map { ids.getString(it) },
            cover = if (o.has("cover")) o.getString("cover") else null,
        )
    }

    private fun write(c: Collection) = JSONObject().apply {
        put("id", c.id); put("type", c.type.name); put("name", c.name); put("place", c.place)
        put("from", c.from.toString()); put("to", c.to.toString()); put("month", c.month); put("day", c.day)
        c.ink?.let { put("ink", it) }
        put("note", c.note)
        put("ids", JSONArray().apply { c.ids.forEach { put(it) } })
        c.cover?.let { put("cover", it) }
    }
}
