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

    /** The step nearest a slider position 0..100. */
    fun levelOf(v: Int) = Math.round(v / 100f * 6).coerceIn(0, 6)

    /** The mood colour mixed with ink, so the name stays readable on any paper (the mockup mixes 55% mood, 45% ink). */
    fun readable(mood: Int, ink: Int): Int {
        val m = color(mood)
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun mix(s: Int) = (ch(m, s) * .55f + ch(ink, s) * .45f).toInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
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
    var look: String
        get() = p.getString("look", "KRAFT") ?: "KRAFT"
        set(v) = p.edit().putString("look", v).apply()
    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) = p.edit().putString("name", v).apply()
    var keepOriginals: Boolean
        get() = p.getBoolean("keepOriginals", true)
        set(v) = p.edit().putBoolean("keepOriginals", v).apply()
    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit().putBoolean("haptics", v).apply()
    var cleanedGallery: Boolean
        get() = p.getBoolean("cleanedGallery", false)
        set(v) = p.edit().putBoolean("cleanedGallery", v).apply()
    var since: String
        get() = p.getString("since", "") ?: ""
        set(v) = p.edit().putString("since", v).apply()
}

/** The tags you can put on a day, as in Apple's State of Mind: what shaped it, and how it felt. */
object Tags {
    val factors = listOf("Community", "Current events", "Dating", "Education", "Family", "Fitness", "Friends", "Health", "Hobbies",
        "Identity", "Money", "Partner", "Self-care", "Spirituality", "Tasks", "Travel", "Weather", "Work")
    private val neg = listOf("Angry", "Anxious", "Ashamed", "Disappointed", "Discouraged", "Drained", "Frustrated", "Guilty", "Hopeless",
        "Irritated", "Jealous", "Lonely", "Overwhelmed", "Sad", "Scared", "Stressed", "Worried")
    private val mid = listOf("Calm", "Content", "Indifferent", "Peaceful", "Satisfied", "Thoughtful", "Tired", "Drained", "Hopeful", "Relieved")
    private val pos = listOf("Amazed", "Amused", "Brave", "Calm", "Confident", "Content", "Excited", "Grateful", "Happy", "Hopeful",
        "Joyful", "Passionate", "Peaceful", "Proud", "Relieved", "Satisfied", "Surprised")
    /** The feelings that fit a mood: unpleasant ones below Neutral, pleasant ones above. */
    fun feelsFor(mood: Int) = when { mood <= 2 -> neg; mood == 3 -> mid; else -> pos }
}
