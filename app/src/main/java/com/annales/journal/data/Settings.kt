package com.annales.journal.data

import android.content.Context

/** The state-of-mind scale, coloured along the same five-stop gradient as the mockup. */
object Moods {
    val names = listOf("Very Unpleasant", "Unpleasant", "Slightly Unpleasant", "Neutral", "Slightly Pleasant", "Pleasant", "Very Pleasant")
    private val grad = longArrayOf(0xFF8E82CF, 0xFFD9908E, 0xFFCBC4B8, 0xFF9EC4A7, 0xFF709FD8)
    val stops: List<Long> = grad.toList()

    /** Colour at t in 0..1, as ARGB. */
    fun at(t: Float): Int {
        val x = t.coerceIn(0f, 1f) * (grad.size - 1)
        val i = minOf(grad.size - 2, x.toInt())
        val f = x - i
        val a = grad[i]; val b = grad[i + 1]
        fun ch(v: Long, s: Int) = ((v shr s) and 0xFF).toInt()
        fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * f).toInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    fun color(mood: Int) = at(mood / 6f)
}

/** The postmark inks. Official is what the post office itself uses: black. */
enum class Ink(val label: String, val argb: Int) {
    OFFICIAL("Official", 0xFF26252A.toInt()),
    POST_RED("Post red", 0xFFB8322F.toInt()),
    CARMINE("Carmine", 0xFF9B2F35.toInt());
}

/** Small preferences, kept in SharedPreferences. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("annales", Context.MODE_PRIVATE)

    var office: String
        get() = p.getString("office", "GLIWICE 1") ?: "GLIWICE 1"
        set(v) = p.edit().putString("office", v).apply()
    var postcode: String
        get() = p.getString("postcode", "44-100") ?: "44-100"
        set(v) = p.edit().putString("postcode", v).apply()
    var ink: Ink
        get() = runCatching { Ink.valueOf(p.getString("ink", null) ?: "") }.getOrDefault(Ink.OFFICIAL)
        set(v) = p.edit().putString("ink", v.name).apply()
    var cut: String
        get() = p.getString("cut", "classic") ?: "classic"
        set(v) = p.edit().putString("cut", v).apply()
    var saveToPhotos: Boolean
        get() = p.getBoolean("saveToPhotos", true)
        set(v) = p.edit().putBoolean("saveToPhotos", v).apply()
    var reminder: Boolean
        get() = p.getBoolean("reminder", true)
        set(v) = p.edit().putBoolean("reminder", v).apply()
    var since: String
        get() = p.getString("since", "") ?: ""
        set(v) = p.edit().putString("since", v).apply()
}
