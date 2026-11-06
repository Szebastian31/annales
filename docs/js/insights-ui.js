'use strict';
// ui/InsightsScreen.kt: how your moods move, and what tends to come with the good ones.

const RANGES = [['WEEK', tr('Week')], ['MONTH', tr('Month')], ['ALL', tr('All')]];

function insightsScreen() {
  const ins = new Insights(Object.values(M.days), M.collections);
  const range = U.range, card = (...k) => h('div.card', null, ...k);
  const seg = (label, on, fn) => h('button.seg' + (on ? '.on' : ''), { onclick: fn }, label);
  const note = ins.note();
  const out = h('div', null,
    header(tr('State of mind × your stamps'), tr('Your *insights*')),
    h('div.sum', null, [[ins.streak, tr('day streak')], [ins.longest, tr('longest')], [ins.stamps, tr('stamps')], [ins.places, tr('places')], [ins.words, tr('words')]]
      .map(([n, l]) => h('div', null, h('b', null, String(n)), h('span', null, l)))),
    note && h('div.row', { style: { padding: '12px 22px', gap: '10px' } },
      h('i', { style: { width: '14px', height: '14px', borderRadius: '50%', flex: 'none', background: Moods.css(ins.average ?? 3) } }),
      h('div.body', { style: { color: 'var(--muted)' } }, note)));

  if (!ins.felt.length) {
    out.append(card(h('div.serif', { style: { fontSize: '20px' } }, tr('Nothing to read yet')),
      h('div.small', { style: { marginTop: '6px' } }, tr('Drag along the horizon on Today to say how a day felt. After a few days, this page shows how your moods move and what tends to come with the good ones.'))));
    return out;
  }
  const tabs = [tr('Life factors'), tr('Feelings'), tr('Connections')];
  out.append(
    card(h('div.row', null, h('div.kick.grow', null, tr('State of mind')), RANGES.map(([k, l]) => seg(l, k === range, () => { U.range = k; App.render(); }))),
      moodChart(ins.columns(range)), mixBar(ins.mix(range), 'bar7')),
    card(h('div.row', { style: { gap: '6px' } }, tabs.map((n, i) => seg(n, i === U.insTab, () => { U.insTab = i; App.render(); }))),
      h('div', { style: { height: '12px' } }),
      U.insTab === 0 ? tagRows(ins.factors(range), tr('No life factors tagged in this range yet. Tag them under the mood on Today.'))
        : U.insTab === 1 ? tagRows(ins.feels(range), tr('No feelings tagged in this range yet.')) : connections(ins)),
    card(h('div.kick', null, tr('Stamps per month, {year}', { year: D.year(D.today()) })), monthsBars(ins.months)));
  return out;
}

/**
 * Seven faint lines, one per mood; a dot per day, a gradient bar where a column holds more than one mood.
 * Drawn on a canvas at the chart's real size (never stretched), so lines stay one pixel and dots stay round.
 */
function moodChart(cols) {
  const cv = h('canvas', { style: { width: '100%', height: '100%', display: 'block' } });
  const box = h('div', { style: { height: '172px', marginTop: '12px', position: 'relative' } }, cv);
  const paint = () => {
    const W = box.clientWidth, H = box.clientHeight;
    if (!W || !H) return;
    const dpr = window.devicePixelRatio || 1;
    if (cv.width !== Math.round(W * dpr) || cv.height !== Math.round(H * dpr)) { cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); }
    const g = cv.getContext('2d'), l = K.look;
    g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, W, H);
    const font = getComputedStyle(document.body).fontFamily;
    g.font = `500 11px ${font}`; g.textBaseline = 'middle';
    const names = [Moods.label(6), Moods.label(3), Moods.label(0)];
    const gutter = Math.ceil(Math.max(...names.map(t => g.measureText(t).width))) + 10;
    const top = 8, bottom = H - 22, x0 = gutter, cw0 = W - gutter;
    const y = m => Math.round(bottom - (bottom - top) * m / 6) + .5; // on the pixel grid: crisp 1px lines
    // the mood lines; the three named ones a little stronger
    for (let m = 0; m <= 6; m++) {
      g.strokeStyle = css(l.line); g.lineWidth = 1;
      g.globalAlpha = m % 3 === 0 ? 1 : .55;
      g.beginPath(); g.moveTo(x0, y(m)); g.lineTo(W, y(m)); g.stroke();
    }
    g.globalAlpha = 1;
    g.fillStyle = rgb(l.muted); g.textAlign = 'left';
    [[6, names[0]], [3, names[1]], [0, names[2]]].forEach(([m, t]) => g.fillText(t, 0, y(m)));

    const cw = cw0 / Math.max(1, cols.length), r = Math.max(2, Math.min(5, cw * .36));
    let lastRight = -1e9;
    cols.forEach((c, i) => {
      const x = x0 + cw * (i + .5);
      if (c.trip) { g.fillStyle = rgb(l.accSoft, .6); roundRect(g, x - cw / 2 + 1, top - 6, cw - 2, bottom - top + 12, 4); g.fill(); }
      if (c.moods.length > 1) {
        const lo = Math.min(...c.moods), hi = Math.max(...c.moods);
        if (hi > lo) {
          const gr = g.createLinearGradient(0, y(hi), 0, y(lo));
          gr.addColorStop(0, Moods.css(hi)); gr.addColorStop(1, Moods.css(lo));
          g.fillStyle = gr; roundRect(g, x - r, y(hi) - r, 2 * r, y(lo) - y(hi) + 2 * r, r); g.fill();
        }
      }
      [...new Set(c.moods)].forEach(m => {
        g.fillStyle = rgb(l.surface); g.beginPath(); g.arc(x, y(m), r + 1.5, 0, Math.PI * 2); g.fill();
        g.fillStyle = Moods.css(m); g.beginPath(); g.arc(x, y(m), r, 0, Math.PI * 2); g.fill();
      });
      // day labels under their column, skipped where they would run into the previous one
      if (c.label) {
        const w = g.measureText(c.label).width;
        if (x - w / 2 >= lastRight + 4 && x + w / 2 <= W) {
          g.fillStyle = rgb(l.muted); g.textAlign = 'center'; g.fillText(c.label, x, H - 8);
          lastRight = x + w / 2;
        }
      }
    });
  };
  new ResizeObserver(paint).observe(box);
  requestAnimationFrame(paint);
  return box;
}
function roundRect(g, x, y, w, hh, r) {
  r = Math.min(r, w / 2, hh / 2);
  g.beginPath();
  g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + hh, r); g.arcTo(x + w, y + hh, x, y + hh, r);
  g.arcTo(x, y + hh, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
}

function mixBar(mix, cls) {
  if (!mix.some(n => n > 0)) return null;
  return h('div.' + cls, null, mix.map((n, i) => n > 0 && h('i', { style: { flex: String(n), background: Moods.css(i) } })));
}

function tagRows(rows, empty) {
  if (!rows.length) return h('div.small', null, empty);
  return rows.slice(0, 10).map(t => h('div', { style: { padding: '6px 0' } },
    h('div.row', { style: { alignItems: 'flex-end' } },
      h('span.body.grow', null, tr(t.name)),
      h('span.small', null, `${tn(t.days.length, '{n} day', '{n} days')} · ${tr('mostly {mood}', { mood: Moods.label(Math.round(t.avg)).toLocaleLowerCase(LOCALE) })}`)),
    mixBar(t.spread, 'bar7.thin')));
}

/** Each connection, most certain first; tap one to see both sides. */
function connections(ins) {
  const side = (label, ds) => h('div.row', { style: { padding: '3px 0' } },
    h('span.small', { style: { width: '150px', flex: 'none', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' } }, `${label} · ${tr('{n}d', { n: ds.length })}`),
    h('div.sbar', null, ds.length > 0 && h('i', { style: { display: 'block', height: '100%', width: clamp(Insights.avg(ds) / 6, .04, 1) * 100 + '%', background: Moods.css(Math.round(Insights.avg(ds))) } })));
  return [
    ins.links().map(l => {
      const open = U.openLink === l.name;
      return h('button.link', { onclick: () => { U.openLink = open ? null : l.name; App.render(); } },
        h('div.row', null,
          h('span.g', null, l.glyph),
          h('div.grow', null,
            h('div.body', null, l.name), h('div.small', null, l.sentence),
            h('div.lv', null, [1, 2, 3].map(i => h('i' + (i <= l.level ? '.on' : ''))), h('span.small', { style: { fontSize: '12px', marginInlineStart: '4px' } }, l.label))),
          h('b', { style: { fontSize: '15px', color: l.level < 2 ? 'var(--muted)' : l.diff > 0 ? 'var(--green)' : '#A04234' } },
            l.level < 2 ? '≈' : (l.diff > 0 ? '+' : '−') + Math.abs(l.diff).toFixed(1))),
        open && h('div', { style: { marginTop: '10px' } }, side(l.la, l.a), side(l.lb, l.b)));
    }),
    h('div.small', { style: { marginTop: '10px' } },
      tr("Bars show how sure this is, based on how many days each side has. Hints from your own entries, not causes. Sunny days use Consensus Weather's models; music and money appear for days restored from an Android backup where Fidelis and Kalendae shared them.")),
  ];
}

function monthsBars(months) {
  const mx = Math.max(1, ...months);
  return h('div.months', null, months.map((v, i) => h('div', null,
    h('i', { style: { height: Math.max(v > 0 ? 3 : 1, 60 * v / mx) + 'px', background: v > 0 ? 'var(--acc)' : 'var(--line)' } }),
    h('span.small', { style: { fontSize: '12px', marginTop: '4px' } }, (LANG === 'en' ? 'JFMAMJJASOND'[i] : lmonth(i + 1, 'narrow'))))));
}
