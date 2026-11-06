package com.annales.journal

import android.content.Context
import android.content.res.Configuration
import android.icu.text.DateIntervalFormat
import android.icu.text.PluralRules
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import android.text.format.DateFormat
import java.text.FieldPosition
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * Languages, the same 33 as the web app and the rest of the family. The texts are written in English in the code,
 * tr("Seal today"), and the translations are the web app's own files (docs/i18n/xx.js, copied into the app when it's
 * built), so the iPhone and Android apps always say the same thing. A line that isn't translated shows the English.
 * Counts: tn(n, "{n} stamp", "{n} stamps"); a language file gives that one { one, few, many, other } as it needs.
 * What's stored stays English (moods, feelings, factors), and postmarks are never translated.
 */
object I18n {
    /** code → name, locale; ar and fa run right to left */
    val LANGS: List<Triple<String, String, String>> = listOf(
        Triple("en", "English", "en-GB"), Triple("pl", "Polski", "pl-PL"), Triple("de", "Deutsch", "de-DE"),
        Triple("uk", "Українська", "uk-UA"), Triple("es", "Español", "es-ES"), Triple("fr", "Français", "fr-FR"),
        Triple("ru", "Русский", "ru-RU"), Triple("zh", "中文", "zh-CN"), Triple("ja", "日本語", "ja-JP"),
        Triple("pt", "Português", "pt-BR"), Triple("nl", "Nederlands", "nl-NL"), Triple("it", "Italiano", "it-IT"),
        Triple("hi", "हिन्दी", "hi-IN"), Triple("ar", "العربية", "ar"), Triple("el", "Ελληνικά", "el-GR"),
        Triple("cs", "Čeština", "cs-CZ"), Triple("sv", "Svenska", "sv-SE"), Triple("ko", "한국어", "ko-KR"),
        Triple("id", "Bahasa Indonesia", "id-ID"), Triple("bn", "বাংলা", "bn-BD"), Triple("tr", "Türkçe", "tr-TR"),
        Triple("te", "తెలుగు", "te-IN"), Triple("ro", "Română", "ro-RO"), Triple("hu", "Magyar", "hu-HU"),
        Triple("fi", "Suomi", "fi-FI"), Triple("no", "Norsk", "nb-NO"), Triple("sk", "Slovenčina", "sk-SK"),
        Triple("sw", "Kiswahili", "sw-KE"), Triple("fa", "فارسی", "fa-IR"), Triple("da", "Dansk", "da-DK"),
        Triple("th", "ไทย", "th-TH"), Triple("vi", "Tiếng Việt", "vi-VN"), Triple("bg", "Български", "bg-BG"),
    )
    private val ALIAS = mapOf("nb" to "no", "nn" to "no", "in" to "id")

    var lang = "en"; private set
    /** for dates, months and weekdays: the language, always Gregorian with Western digits like the postmarks */
    var locale: Locale = Locale.UK; private set
    private var table: Map<String, Any> = emptyMap()
    private var plural: PluralRules? = null

    private fun prefs(c: Context) = c.getSharedPreferences("annales", Context.MODE_PRIVATE)

    /** "" while Annales follows the phone */
    fun chosen(c: Context): String = prefs(c).getString("lang", "") ?: ""
    fun choose(c: Context, code: String) = prefs(c).edit().putString("lang", code).commit()

    private fun pick(c: Context): String {
        chosen(c).takeIf { code -> LANGS.any { it.first == code } }?.let { return it }
        val phone = c.resources.configuration.locales
        for (i in 0 until phone.size()) {
            val l = phone[i].language.lowercase().let { ALIAS[it] ?: it }
            if (LANGS.any { it.first == l }) return l
        }
        return "en"
    }

    /** c in Annales' language, for MainActivity.attachBaseContext: strings, layout direction (Arabic, Persian) and all. */
    fun wrap(c: Context): Context {
        load(c)
        val conf = Configuration(c.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(LANGS.first { it.first == lang }.third))
        return c.createConfigurationContext(conf)
    }

    @Synchronized fun load(c: Context) {
        val code = pick(c)
        if (code == lang && (code == "en" || table.isNotEmpty())) return
        lang = code
        val tag = LANGS.first { it.first == code }.third
        locale = Locale.Builder().setLanguageTag(tag).setUnicodeLocaleKeyword("nu", "latn").setUnicodeLocaleKeyword("ca", "gregory").build()
        plural = runCatching { PluralRules.forLocale(Locale.forLanguageTag(tag)) }.getOrNull()
        table = if (code == "en") emptyMap() else runCatching {
            c.assets.open("i18n/$code.js").bufferedReader().use { parse(it.readText()) }
        }.getOrDefault(emptyMap())
    }

    // The files are the web app's: one `"English": "translation",` or `"English": { one: "…", other: "…" },` a line.
    private val LINE = Regex("""^\s*"((?:[^"\\]|\\.)*)":\s*(.+?),?\s*$""")
    private val STR = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val FORM = Regex("""(\w+):\s*"((?:[^"\\]|\\.)*)"""")
    private fun unq(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")

    private fun parse(js: String): Map<String, Any> {
        val out = HashMap<String, Any>()
        for (line in js.lineSequence()) {
            val m = LINE.matchEntire(line) ?: continue
            val key = unq(m.groupValues[1]); val v = m.groupValues[2]
            if (v.startsWith("{")) out[key] = FORM.findAll(v).associate { it.groupValues[1] to unq(it.groupValues[2]) }
            else STR.matchEntire(v)?.let { out[key] = unq(it.groupValues[1]) }
        }
        return out
    }

    private fun fill(s: String, vars: Array<out Pair<String, Any?>>): String {
        var out = s
        for ((k, v) in vars) out = out.replace("{$k}", v.toString())
        return out
    }

    /** tr("Sealed {when}", "when" to at) */
    fun tr(s: String, vararg vars: Pair<String, Any?>): String {
        val e = table[s]
        val t = when (e) { is String -> e; is Map<*, *> -> e["other"] as? String; else -> null } ?: s
        return fill(t, vars)
    }

    /** tn(3, "{n} stamp", "{n} stamps"): the language's own plural; the English plural is the key */
    fun tn(n: Int, one: String, other: String, vararg vars: Pair<String, Any?>): String {
        val all = arrayOf("n" to n, *vars)
        val t = when (val e = table[other]) {
            is Map<*, *> -> (e[plural?.select(n.toDouble()) ?: if (n == 1) "one" else "other"] ?: e["other"]) as? String
            is String -> e
            else -> null
        } ?: if (n == 1) one else other
        return fill(t, all)
    }

    /**
     * A date in the language. [pattern] is the English layout ("EEE d MMM"); other languages get their own order of
     * the same parts (as the web app's Intl does), Gregorian, with Western digits (java.time's default).
     */
    fun date(t: TemporalAccessor, pattern: String): String =
        if (lang == "en") DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(t)
        else DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, pattern.filter { it.isLetter() }), locale).format(t)

    /** A date range the language's own way ("3–5 lis 2026", "2026年11月3日～5日"); null in English, which keeps its layout. */
    fun range(a: LocalDate, b: LocalDate): String? {
        if (lang == "en" || a == b) return null
        return runCatching {
            val f = DateIntervalFormat.getInstance("dMMMy", locale)
            f.timeZone = TimeZone.GMT_ZONE
            val ms = { d: LocalDate -> d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            f.format(DateInterval(ms(a), ms(b)), StringBuffer(), FieldPosition(0)).toString()
        }.getOrNull()
    }

    /** Sentence case for names some languages write in lower case ("listopad" → "Listopad") when they stand alone. */
    fun cap(s: String) = s.replaceFirstChar { it.titlecase(locale) }
    fun upper(s: String) = s.uppercase(locale)
}

fun tr(s: String, vararg vars: Pair<String, Any?>) = I18n.tr(s, *vars)
fun tn(n: Int, one: String, other: String, vararg vars: Pair<String, Any?>) = I18n.tn(n, one, other, *vars)
