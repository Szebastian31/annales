'use strict';
// ui/InsightsScreen.kt: how your moods move, and what tends to come with the good ones.

const RANGES = [['WEEK', 'Week'], ['MONTH', 'Month'], ['ALL', 'All']];

function insightsScreen() {
  const ins = new Insights(Object.values(M.days), M.collections);
  const range = U.range, card = (...k) => h('div.card', null, ...k);
  const seg = (label, on, fn) => h('button.seg' + (on ? '.on' : ''), { onclick: fn }, label);
  const note = ins.note();
  const out = h('div', null,
    header('State of mind × your stamps', 'Your ', 'insights'),
    h('div.sum', null, [[ins.streak, 'day streak'], [ins.longest, 'longest'], [ins.stamps, 'stamps'], [ins.places, 'places'], [ins.words, 'words']]
      .map(([n, l]) => h('div', null, h('b', null, String(n)), h('span', null, l)))),
    note && h('div.row', { style: { padding: '12px 22px', gap: '10px' } },
      h('i', { style: { width: '14px', height: '14px', borderRadius: '50%', flex: 'none', background: Moods.css(ins.average ?? 3) } }),
      h('div.body', { style: { color: 'var(--muted)' } }, note)));

  if (!ins.felt.length) {
    out.append(card(h('div.serif', { style: { fontSize: '20px' } }, 'Nothing to read yet'),
      h('div.small', { style: { marginTop: '6px' } }, 'Drag along the horizon on Today to say how a day felt. After a few days, this page shows how your moods move and what tends to come with the good ones.')));
    return out;
  }
  const tabs = ['Life factors', 'Feelings', 'Connections'];
  out.append(
    card(h('div.row', null, h('div.kick.grow', null, 'State of mind'), RANGES.map(([k, l]) => seg(l, k === range, () => { U.range = k; App.render(); }))),
      moodChart(ins.columns(range)), mixBar(ins.mix(range), 'bar7')),
    card(h('div.row', { style: { gap: '6px' } }, tabs.map((n, i) => seg(n, i === U.insTab, () => { U.insTab = i; App.render(); }))),
      h('div', { style: { height: '12px' } }),
      U.insTab === 0 ? tagRows(ins.factors(range), 'No life factors tagged in this range yet. Tag them under the mood on Today.')
        : U.insTab === 1 ? tagRows(ins.feels(range), 'No feelings tagged in this range yet.') : connections(ins)),
    card(h('div.kick', null, `Stamps per month, ${D.year(D.today())}`), monthsBars(ins.months)));
  return out;
}

/** Seven faint lines, one per mood; a dot per day, a gradient bar where a column holds more than one mood. */
function moodChart(cols) {
  const W = 300, H = 150, pad = 6, y = m => H - pad - (H - 2 * pad) * m / 6;
  const cw = W / Math.max(1, cols.length), r = Math.min(5, cw * .38);
  let svg = '<defs>';
  const body = [];
  for (let m = 0; m <= 6; m++) body.push(`<line x1="0" x2="${W}" y1="${y(m)}" y2="${y(m)}" stroke="var(--line)" stroke-width="1"/>`);
  cols.forEach((c, i) => {
    const x = cw * (i + .5);
    if (c.trip) body.push(`<rect x="${x - cw / 2 + 1}" y="0" width="${cw - 2}" height="${H}" rx="4" fill="var(--accSoft)" opacity=".6"/>`);
    if (c.moods.length > 1) {
      const lo = Math.min(...c.moods), hi = Math.max(...c.moods);
      if (hi > lo) {
        svg += `<linearGradient id="g${i}" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="${Moods.css(hi)}"/><stop offset="1" stop-color="${Moods.css(lo)}"/></linearGradient>`;
        body.push(`<rect x="${x - r}" y="${y(hi)}" width="${2 * r}" height="${y(lo) - y(hi)}" rx="${r}" fill="url(#g${i})"/>`);
      }
    }
    [...new Set(c.moods)].forEach(m => body.push(`<circle cx="${x}" cy="${y(m)}" r="${r + 1.5}" fill="#fff"/><circle cx="${x}" cy="${y(m)}" r="${r}" fill="${Moods.css(m)}"/>`));
  });
  svg += '</defs>' + body.join('');
  return h('div.row', { style: { height: '170px', paddingTop: '12px', alignItems: 'stretch' } },
    h('div', { style: { display: 'flex', flexDirection: 'column', justifyContent: 'space-between', paddingRight: '6px', paddingBottom: '18px' } },
      ['Very Pleasant', 'Neutral', 'Very Unpleasant'].map(t => h('span.small', { style: { fontSize: '11px', lineHeight: '13px' } }, t))),
    h('div.grow', { style: { display: 'flex', flexDirection: 'column' } },
      h('div', { style: { flex: 1 }, html: `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="width:100%;height:100%;display:block;overflow:visible">${svg}</svg>` }),
      h('div.row', { style: { paddingTop: '4px' } }, cols.map(c => h('span.small', { style: { flex: 1, minWidth: 0, fontSize: '11px', textAlign: 'center', whiteSpace: 'nowrap' } }, c.label)))));
}

function mixBar(mix, cls) {
  if (!mix.some(n => n > 0)) return null;
  return h('div.' + cls, null, mix.map((n, i) => n > 0 && h('i', { style: { flex: String(n), background: Moods.css(i) } })));
}

function tagRows(rows, empty) {
  if (!rows.length) return h('div.small', null, empty);
  return rows.slice(0, 10).map(t => h('div', { style: { padding: '6px 0' } },
    h('div.row', { style: { alignItems: 'flex-end' } },
      h('span.body.grow', null, t.name),
      h('span.small', null, `${plural(t.days.length, 'day')} · mostly ${Moods.names[Math.round(t.avg)].toLowerCase()}`)),
    mixBar(t.spread, 'bar7.thin')));
}

/** Each connection, most certain first; tap one to see both sides. */
function connections(ins) {
  const side = (label, ds) => h('div.row', { style: { padding: '3px 0' } },
    h('span.small', { style: { width: '150px', flex: 'none', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' } }, `${label} · ${ds.length}d`),
    h('div.sbar', null, ds.length > 0 && h('i', { style: { display: 'block', height: '100%', width: clamp(Insights.avg(ds) / 6, .04, 1) * 100 + '%', background: Moods.css(Math.round(Insights.avg(ds))) } })));
  return [
    ins.links().map(l => {
      const open = U.openLink === l.name;
      return h('button.link', { onclick: () => { U.openLink = open ? null : l.name; App.render(); } },
        h('div.row', null,
          h('span.g', null, l.glyph),
          h('div.grow', null,
            h('div.body', null, l.name), h('div.small', null, l.sentence),
            h('div.lv', null, [1, 2, 3].map(i => h('i' + (i <= l.level ? '.on' : ''))), h('span.small', { style: { fontSize: '12px', marginLeft: '4px' } }, l.label))),
          h('b', { style: { fontSize: '15px', color: l.level < 2 ? 'var(--muted)' : l.diff > 0 ? 'var(--green)' : '#A04234' } },
            l.level < 2 ? '≈' : (l.diff > 0 ? '+' : '−') + Math.abs(l.diff).toFixed(1))),
        open && h('div', { style: { marginTop: '10px' } }, side(l.la, l.a), side(l.lb, l.b)));
    }),
    h('div.small', { style: { marginTop: '10px' } },
      "Bars show how sure this is, based on how many days each side has. Hints from your own entries, not causes. Sunny days use Consensus Weather's models; music and money appear for days restored from an Android backup where Fidelis and Kalendae shared them."),
  ];
}

function monthsBars(months) {
  const mx = Math.max(1, ...months);
  return h('div.months', null, months.map((v, i) => h('div', null,
    h('i', { style: { height: Math.max(v > 0 ? 3 : 1, 60 * v / mx) + 'px', background: v > 0 ? 'var(--acc)' : 'var(--line)' } }),
    h('span.small', { style: { fontSize: '12px', marginTop: '4px' } }, 'JFMAMJJASOND'[i]))));
}
