package com.annales.journal.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Everything the Insights tab shows, worked out from your own entries only. Connections compare the mood on two kinds
 * of day and say how sure that is, the same way the mockup does: under 5 days on either side is "too early to tell".
 */
class Insights(days: Iterable<Day>, private val trips: List<Collection>, private val today: LocalDate = LocalDate.now()) {
    private val all = days.sortedBy { it.date }
    val felt = all.filter { it.mood != null }
    private val sealed = all.filter { it.sealed }

    // ---- the summary row ----
    val stamps = all.sumOf { it.stamps.size }
    val places = sealed.map { it.office }.filter { it.isNotBlank() }.toSet().size
    val words = all.sumOf { d -> d.stamps.sumOf { words(it.title) + words(it.note) } }
    private fun words(s: String) = s.split(Regex("\\s+")).count { it.isNotBlank() }

    /** Days sealed in a row up to today (an unsealed today doesn't break it yet), and the longest run ever. */
    val streak: Int
    val longest: Int
    init {
        val set = sealed.map { it.date }.toSet()
        var d = if (today in set) today else today.minusDays(1)
        var cur = 0
        while (d in set) { cur++; d = d.minusDays(1) }
        streak = cur
        var best = 0; var run = 0; var prev: LocalDate? = null
        set.sorted().forEach { x -> run = if (prev != null && ChronoUnit.DAYS.between(prev, x) == 1L) run + 1 else 1; best = maxOf(best, run); prev = x }
        longest = best
    }

    // ---- state of mind over time ----
    enum class Range(val label: String) { WEEK("Week"), MONTH("Month"), ALL("All") }
    data class Column(val label: String, val moods: List<Int>, val trip: Boolean)

    fun columns(r: Range): List<Column> = when (r) {
        Range.WEEK, Range.MONTH -> {
            val n = if (r == Range.WEEK) 7 else 30
            (n - 1 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                val label = if (r == Range.WEEK) date.dayOfWeek.name.take(1) else if (date.dayOfMonth % 5 == 1) "${date.dayOfMonth}" else ""
                Column(label, listOfNotNull(felt.firstOrNull { it.date == date }?.mood), onTrip(date))
            }
        }
        Range.ALL -> {
            val first = felt.firstOrNull()?.date ?: today
            val start = first.with(DayOfWeek.MONDAY)
            generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(today) }.map { w ->
                val end = w.plusDays(6)
                Column("${w.dayOfMonth}.${w.monthValue}", felt.filter { !it.date.isBefore(w) && !it.date.isAfter(end) }.mapNotNull { it.mood }, false)
            }.toList()
        }
    }

    fun inRange(r: Range): List<Day> = when (r) {
        Range.WEEK -> felt.filter { !it.date.isBefore(today.minusDays(6)) }
        Range.MONTH -> felt.filter { !it.date.isBefore(today.minusDays(29)) }
        Range.ALL -> felt
    }

    /** How many days landed on each of the seven moods. */
    fun mix(r: Range): IntArray = IntArray(7).also { a -> inRange(r).forEach { a[it.mood!!]++ } }

    // ---- tags ----
    data class Tagged(val name: String, val days: List<Day>) {
        val avg get() = avg(days)
        /** Days per mood, for the little stacked bar. */
        val spread get() = IntArray(7).also { a -> days.forEach { a[it.mood!!]++ } }
    }
    fun factors(r: Range) = tally(inRange(r)) { it.factors }
    fun feels(r: Range) = tally(inRange(r)) { it.feels }
    private fun tally(ds: List<Day>, of: (Day) -> List<String>) =
        ds.flatMap { d -> of(d).map { it to d } }.groupBy({ it.first }, { it.second }).map { Tagged(it.key, it.value) }.sortedByDescending { it.days.size }

    // ---- connections ----
    data class Link(
        val glyph: String, val name: String, val a: List<Day>, val b: List<Day>, val la: String, val lb: String,
    ) {
        val diff get() = avg(a) - avg(b)
        private val n get() = minOf(a.size, b.size)
        /** 0 too early, 1 no clear difference, 2 weak hint, 3 clear hint. */
        val level get() = when { n < 5 -> 0; abs(diff) < .4 -> 1; n >= 15 && abs(diff) >= .9 -> 3; else -> 2 }
        val label get() = listOf("Too early to tell", "No clear difference", "Weak hint", "Clear hint")[level]
        val sentence get() = when (level) {
            0 -> "Only $n day${if (n == 1) "" else "s"} so far in one group"
            1 -> "About the same either way"
            else -> "About ${"%.1f".format(abs(diff))} steps ${if (diff > 0) "more pleasant" else "less pleasant"}"
        }
    }

    fun links(): List<Link> {
        val (onTrip, home) = felt.partition { onTrip(it.date) }
        val (weekend, weekday) = felt.partition { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val (full, other) = felt.partition { moonLight(it.date) > .9 }
        val (friends, noFriends) = felt.partition { "Friends" in it.factors || "Family" in it.factors || "Partner" in it.factors }
        val (stamped, bare) = felt.partition { it.stamps.size >= 2 }
        return listOf(
            Link("✈", "Trips", onTrip, home, "On a trip", "At home"),
            Link("♡", "Your people", friends, noFriends, "Friends, family, partner", "Other days"),
            Link("☕", "Weekends", weekend, weekday, "Sat · Sun", "Weekdays"),
            Link("❏", "Busy days", stamped, bare, "2–3 stamps", "0–1 stamp"),
            Link("☾", "Full moon", full, other, "Near full", "Other nights"),
        ).sortedWith(compareByDescending<Link> { it.level }.thenByDescending { abs(it.diff) })
    }

    /** A gentle note from your own entries: never a lecture, just what your good days tend to have. */
    fun note(): String? {
        val recent = felt.takeLast(4)
        val low = recent.count { it.mood!! <= 2 } >= 3
        val top = felt.filter { it.mood!! >= 5 }.flatMap { it.factors }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(2).map { it.key }
        return when {
            low && top.isNotEmpty() -> "A few heavier days lately. On your more pleasant days you often tagged ${top.joinToString(" and ")}."
            low -> "A few heavier days lately. Be gentle with yourself."
            top.isNotEmpty() -> "Your most pleasant days often involve ${top.joinToString(" and ")}."
            else -> null
        }
    }

    /** Stamps per month this year. */
    val months: IntArray = IntArray(12).also { a -> all.filter { it.date.year == today.year }.forEach { a[it.date.monthValue - 1] += it.stamps.size } }

    val average get() = if (felt.isEmpty()) null else avg(felt).roundToInt()

    private fun onTrip(d: LocalDate) = trips.any { it.type == CollType.TRIP && it.covers(d) }

    companion object {
        fun avg(ds: List<Day>) = if (ds.isEmpty()) 0.0 else ds.sumOf { it.mood!! }.toDouble() / ds.size

        /** How lit the moon is that night, 0 to 1 (the mockup's formula). */
        fun moonLight(d: LocalDate): Double {
            val jd = d.toEpochDay() + 2440587.5
            var age = ((jd - 2451550.1) / 29.530588853) % 1.0
            if (age < 0) age += 1
            return (1 - cos(2 * PI * age)) / 2
        }
    }
}
