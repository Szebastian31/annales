package com.annales.journal.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annales.journal.AnnalesModel
import com.annales.journal.I18n
import com.annales.journal.tn
import com.annales.journal.tr
import com.annales.journal.data.Insights
import com.annales.journal.data.Moods
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(model: AnnalesModel, modifier: Modifier = Modifier) {
    val ins = remember(model.days, model.collections) { Insights(model.days.values, model.collections) }
    var range by rememberSaveable { mutableStateOf(Insights.Range.MONTH) }
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header(tr("State of mind × your stamps"), title(tr("Your *insights*")))

        // the summary row
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(K.surface, RoundedCornerShape(18.dp)).padding(vertical = 14.dp)) {
            listOf(ins.streak to tr("day streak"), ins.longest to tr("longest"), ins.stamps to tr("stamps"), ins.places to tr("places"), ins.words to tr("words"))
                .forEach { (n, l) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 22.sp, color = K.ink))
                        Text(l, style = SmallStyle.copy(fontSize = 12.sp), textAlign = TextAlign.Center)
                    }
                }
        }

        ins.note()?.let { note ->
            Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(Color(Moods.color(ins.average ?: 3)), CircleShape))
                Spacer(Modifier.size(10.dp))
                Text(note, style = BodyStyle.copy(color = K.muted))
            }
        }

        if (ins.felt.isEmpty()) {
            Card {
                Text(tr("Nothing to read yet"), style = TextStyle(fontWeight = FontWeight.SemiBold, fontFamily = K.serif, fontSize = 20.sp, color = K.ink))
                Text(
                    tr("Drag along the horizon on Today to say how a day felt. After a few days, this page shows how your moods move and what tends to come with the good ones."),
                    style = SmallStyle, modifier = Modifier.padding(top = 6.dp),
                )
            }
            return@Column
        }

        // state of mind
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(I18n.upper(tr("State of mind")), style = KickerStyle, modifier = Modifier.weight(1f))
                Insights.Range.entries.forEach { r -> Seg(tr(r.label), r == range) { range = r } }
            }
            MoodChart(ins.columns(range))
            MixBar(ins.mix(range))
        }

        // tags and connections
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(tr("Life factors"), tr("Feelings"), tr("Connections")).forEachIndexed { i, n -> Seg(n, i == tab) { tab = i } }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> TagRows(ins.factors(range), tr("No life factors tagged in this range yet. Tag them under the mood on Today."))
                1 -> TagRows(ins.feels(range), tr("No feelings tagged in this range yet."))
                else -> Connections(ins)
            }
        }

        // stamps per month
        Card {
            Text(I18n.upper(tr("Stamps per month, {year}", "year" to java.time.LocalDate.now().year)), style = KickerStyle)
            val mx = maxOf(1, ins.months.max())
            Row(Modifier.fillMaxWidth().height(90.dp).padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                ins.months.forEachIndexed { i, v ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(
                            Modifier.width(12.dp).height((60f * v / mx).dp.coerceAtLeast(if (v > 0) 3.dp else 1.dp))
                                .background(if (v > 0) K.acc else K.line, RoundedCornerShape(3.dp))
                        )
                        Text(if (I18n.lang == "en") "JFMAMJJASOND"[i].toString() else I18n.date(java.time.YearMonth.of(2026, i + 1), "LLLLL"), style = SmallStyle.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().background(K.surface, RoundedCornerShape(18.dp)).padding(16.dp)
) { content() }

@Composable
private fun Seg(label: String, on: Boolean, onClick: () -> Unit) = Text(
    label,
    style = TextStyle(fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) K.acc else K.muted),
    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) K.accSoft else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
)

/** Seven faint lines, one per mood; a dot per day, a gradient bar where a column holds more than one mood. */
@Composable
private fun MoodChart(cols: List<Insights.Column>) {
    Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp)) {
        Column(Modifier.fillMaxHeight().padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("Very Pleasant", "Neutral", "Very Unpleasant").forEach { Text(tr(it), style = SmallStyle.copy(fontSize = 11.sp)) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(Modifier.weight(1f).fillMaxWidth()) {
                val h = size.height; val pad = 6.dp.toPx()
                fun y(m: Int) = h - pad - (h - 2 * pad) * m / 6f
                for (m in 0..6) drawLine(K.line, Offset(0f, y(m)), Offset(size.width, y(m)), 1.dp.toPx())
                val cw = size.width / maxOf(1, cols.size)
                val r = minOf(5.dp.toPx(), cw * .38f)
                cols.forEachIndexed { i, c ->
                    val x = cw * (i + .5f)
                    if (c.trip) drawRoundRect(K.accSoft.copy(alpha = .6f), Offset(x - cw / 2 + 1, 0f), Size(cw - 2, h), CornerRadius(4f))
                    if (c.moods.size > 1) {
                        val lo = c.moods.min(); val hi = c.moods.max()
                        if (hi > lo) drawRoundRect(
                            Brush.verticalGradient(listOf(Color(Moods.color(hi)), Color(Moods.color(lo))), y(hi), y(lo)),
                            Offset(x - r, y(hi)), Size(2 * r, y(lo) - y(hi)), CornerRadius(r),
                        )
                    }
                    c.moods.distinct().forEach { m ->
                        drawCircle(Color.White, r + 1.5.dp.toPx(), Offset(x, y(m)))
                        drawCircle(Color(Moods.color(m)), r, Offset(x, y(m)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                cols.forEach { Text(it.label, style = SmallStyle.copy(fontSize = 11.sp), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun MixBar(mix: IntArray) {
    if (mix.sum() == 0) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(10.dp).clip(RoundedCornerShape(99.dp))) {
        mix.forEachIndexed { i, n -> if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(Color(Moods.color(i)))) }
    }
}

@Composable
private fun TagRows(rows: List<Insights.Tagged>, empty: String) {
    if (rows.isEmpty()) { Text(empty, style = SmallStyle); return }
    rows.take(10).forEach { t ->
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(tr(t.name), style = BodyStyle.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("${tn(t.days.size, "{n} day", "{n} days")} · ${tr("mostly {mood}", "mood" to tr(Moods.names[t.avg.roundToInt()]).lowercase(I18n.locale))}", style = SmallStyle)
            }
            Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(8.dp).clip(RoundedCornerShape(99.dp))) {
                t.spread.forEachIndexed { i, n -> if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(Color(Moods.color(i)))) }
            }
        }
    }
}

/** Each connection, most certain first; tap one to see both sides. */
@Composable
private fun Connections(ins: Insights) {
    var open by remember { mutableStateOf<String?>(null) }
    ins.links().forEach { l ->
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(K.page.copy(alpha = .55f))
                .clickable { open = if (open == l.name) null else l.name }.padding(12.dp).animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.glyph, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = K.acc), modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.name, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                    Text(l.sentence, style = SmallStyle)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        (1..3).forEach { i ->
                            Box(Modifier.padding(end = 3.dp).size(14.dp, 4.dp).background(if (i <= l.level) K.acc else K.line, RoundedCornerShape(2.dp)))
                        }
                        Text(l.label, style = SmallStyle.copy(fontSize = 12.sp), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                val d = l.diff
                Text(
                    if (l.level < 2) "≈" else (if (d > 0) "+" else "−") + String.format(java.util.Locale.ROOT, "%.1f", abs(d)),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = when { l.level < 2 -> K.muted; d > 0 -> K.green; else -> Color(0xFFA04234) }),
                )
            }
            if (open == l.name) {
                Spacer(Modifier.height(10.dp))
                Side(l.la, l.a.size, Insights.avg(l.a), l.a.isNotEmpty())
                Side(l.lb, l.b.size, Insights.avg(l.b), l.b.isNotEmpty())
            }
        }
    }
    Text(
        tr("Bars show how sure this is, based on how many days each side has. Hints from your own entries, not causes. Sunny days use Consensus Weather's models; music and money appear once Fidelis and Kalendae share their days."),
        style = SmallStyle, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun Side(label: String, n: Int, avg: Double, any: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label · ${tr("{n}d", "n" to n)}", style = SmallStyle, modifier = Modifier.width(150.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(K.line)) {
            if (any) Box(Modifier.fillMaxWidth((avg / 6).toFloat().coerceIn(.04f, 1f)).fillMaxHeight().background(Color(Moods.color(avg.roundToInt()))))
        }
    }
}
