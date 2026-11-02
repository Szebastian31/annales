package com.annales.journal.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * What the sibling apps add to a day. Weather is worked out here the way Consensus Weather does it (the same thirteen
 * Open-Meteo models, temperatures averaged, the condition most models vote for). The moon is calculated, as in Caelum.
 * Music, money and the day's tone come from Fidelis, Kalendae and Augur, which each share a small daily summary.
 */
object Siblings {
    data class App(val pkg: String, val name: String, val what: String)

    val FIDELIS = App("com.fidelis.player", "Fidelis Music", "Top track and listening time")
    val KALENDAE = App("com.kalendae.money", "Kalendae", "Spent vs safe-to-spend")
    val AUGUR = App("com.augur.stars", "Augur", "Daily tone, just for fun")
    val WEATHER = App("com.consensus.weather", "Consensus Weather", "Weather, temperature, model agreement")
    val CAELUM = App("com.caelum.sky", "Caelum Sky", "Moon phase, what was overhead")
    val sharing = listOf(FIDELIS, KALENDAE, AUGUR)

    fun installed(context: Context, app: App) =
        runCatching { context.packageManager.getPackageInfo(app.pkg, 0); true }.getOrDefault(false)

    /**
     * Asks a sibling for its summary of [date]: content://<package>.annales/day/2026-11-02, one row per key/value.
     * Returns an empty map when the app isn't installed, doesn't share yet, or had nothing that day.
     */
    fun shared(context: Context, app: App, date: LocalDate): Map<String, String> = runCatching {
        val uri = Uri.parse("content://${app.pkg}.annales/day/$date")
        context.contentResolver.query(uri, arrayOf("key", "value"), null, null, null)?.use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1) ?: "") }
        } ?: emptyMap()
    }.getOrDefault(emptyMap())

    // ---- weather, the Consensus Weather way ----

    private val MODELS = listOf("ecmwf_ifs025", "icon_seamless", "gfs_seamless", "metno_seamless", "meteofrance_seamless",
        "jma_seamless", "gem_seamless", "ukmo_seamless", "knmi_seamless", "dmi_seamless", "kma_seamless", "cma_grapes_global", "bom_access_global")

    /** Places looked up once and remembered; Gliwice ships built in. */
    private val coords = mutableMapOf("Gliwice" to (50.2945 to 18.6714))

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 12000
        return try { JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
    }

    private fun locate(place: String): Pair<Double, Double>? {
        val name = place.trim().split(" ").first().replaceFirstChar { it.titlecase() }
        coords[name]?.let { return it }
        val r = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=${URLEncoder.encode(name, "UTF-8")}")
        val hit = r.optJSONArray("results")?.optJSONObject(0) ?: return null
        return (hit.getDouble("latitude") to hit.getDouble("longitude")).also { coords[name] = it }
    }

    /**
     * The consensus for one day at [place]: code (WMO), high, low, and how far apart the models' highs were. Open-Meteo keeps
     * about three months of past forecasts, so older days return null.
     */
    fun weather(place: String, date: LocalDate): Map<String, String>? {
        val back = ChronoUnit.DAYS.between(date, LocalDate.now())
        if (back > 90 || back < -6) return null
        val (lat, lon) = locate(place) ?: return null
        val r = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto" +
            "&start_date=$date&end_date=$date&daily=weather_code,temperature_2m_max,temperature_2m_min&models=${MODELS.joinToString(",")}")
        val d = r.optJSONObject("daily") ?: return null
        fun vals(key: String) = MODELS.mapNotNull { m ->
            d.optJSONArray("${key}_$m")?.let { a -> if (a.length() > 0 && !a.isNull(0)) a.getDouble(0) else null }
        }
        val hi = vals("temperature_2m_max"); val lo = vals("temperature_2m_min")
        val codes = vals("weather_code").map { it.toInt() }
        if (hi.isEmpty() || codes.isEmpty()) return null
        val code = codes.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        val spread = hi.max() - hi.min()
        return mapOf(
            "wx.code" to "$code", "wx.hi" to "${hi.average().roundToInt()}", "wx.lo" to "${lo.average().roundToInt()}",
            "wx.models" to "${hi.size}", "wx.agree" to when { spread <= 2.5 -> "agree"; spread <= 5 -> "roughly"; else -> "diverge" },
        )
    }

    /** WMO code → a short name, and the sky stamp it matches. */
    fun describe(code: Int): Pair<String, com.annales.journal.render.SkyStamp.Weather> {
        val W = com.annales.journal.render.SkyStamp.Weather
        return when (code) {
            0 -> "Clear" to W.CLEAR
            1 -> "Mostly clear" to W.CLEAR
            2 -> "Partly cloudy" to W.PARTLY
            3 -> "Overcast" to W.CLOUDY
            45, 48 -> "Fog" to W.FOG
            in 51..57 -> "Drizzle" to W.RAIN
            in 61..67 -> "Rain" to W.RAIN
            in 71..77 -> "Snow" to W.CLOUDY
            in 80..82 -> "Showers" to W.RAIN
            85, 86 -> "Snow showers" to W.CLOUDY
            in 95..99 -> "Thunderstorm" to W.RAIN
            else -> "Mixed" to W.PARTLY
        }
    }
}
