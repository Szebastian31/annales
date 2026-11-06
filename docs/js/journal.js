'use strict';
// ui/JournalScreen.kt: a month calendar of sealed days, or "Pages", one page per month.

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'];

/**
 * Opens a day from the Journal or a collection: on its stamps, full screen, or on the day page if it has none yet.
 * Today opens the Today tab instead.
 */
function openDay(date, coll = null) {
  if (date === D.today()) { U.openColl = null; U.openDay = null; U.back = 0; U.tab = 'today'; App.render(); return; }
  U.openDay = date; U.dayColl = coll;
  const stamps = M.day(date).stamps;
  U.dayEdit = !stamps.length;
  App.render();
  if (stamps.length) showDayStamps(date, coll);
}
function showDayStamps(date, coll) {
  U.dayEdit = false;
  openViewer(M.day(date).stamps.map(s => [date, s]), 0, {
    inside: coll, canReorder: true,
    onEdit: () => { U.dayEdit = true; },
    onClose: () => { if (!U.dayEdit) U.openDay = null; App.render(); },
  });
}
/** The day page for a past day, under a back button. */
function openDayScreen() {
  const date = U.openDay, has = M.day(date).stamps.length > 0;
  return h('div', null,
    h('button.back', { onclick: () => { if (has) { App.render(); showDayStamps(date, U.dayColl); } else { U.openDay = null; App.render(); } } },
      has ? tr('‹ Stamp') : `‹ ${U.dayColl ? U.dayColl.name : tr('Journal')}`),
    dayPage(date));
}

function journalScreen() {
  const sealed = Object.values(M.days).filter(d => d.sealed);
  const count = sealed.reduce((s, d) => s + d.stamps.length, 0);
  return h('div', null,
    header(`${tn(count, '{n} stamp', '{n} stamps')} · ${tn(sealed.length, '{n} day', '{n} days')}`, tr('The *journal*')),
    h('div.pillbar', null, [[false, tr('Calendar')], [true, tr('Pages')]].map(([p, n]) =>
      h('button' + (U.pages === p ? '.on' : ''), { onclick: () => { U.pages = p; App.render(); } }, n))),
    U.pages ? pages() : calendar());
}

function calendar() {
  const now = D.ym(D.today());
  const ym = U.month || now;
  const sealedN = Object.values(M.days).filter(d => d.sealed && D.ym(d.date) === ym).length;
  const first = D.ym(M.since) > ym ? ym : D.ym(M.since);
  const y = +ym.slice(0, 4), m = +ym.slice(5, 7);
  const nav = (g, on, to) => h('button.nav', { disabled: !on, onclick: () => { U.month = to; App.render(); } }, g);

  const lead = D.dow(D.of(y, m, 1)) % 7; // Sunday first, as in the mockup
  const n = D.lengthOfMonth(y, m), tail = (7 - (lead + n) % 7) % 7;
  const start = D.plus(D.of(y, m, 1), -lead);
  const today = D.today();
  const cells = [];
  for (let i = 0; i < lead + n + tail; i++) {
    const date = D.plus(start, i);
    cells.push(cell(date, D.ym(date) === ym, today));
  }
  return [
    h('div.month', null,
      h('div.grow', null,
        h('div.row', { style: { alignItems: 'flex-start' } },
          h('span.serif', { style: { fontSize: '44px', lineHeight: '44px' } }, pad2(sealedN)),
          h('span.kick', { style: { fontSize: '11px', marginInlineStart: '4px', marginTop: '6px' } }, `${tn(sealedN, 'DAY', 'DAYS')} · ${y}`)),
        h('div.it.accent', { style: { fontSize: '20px' } }, monthName(m))),
      nav('‹', ym > first, D.ymPlus(ym, -1)), nav('›', ym < now, D.ymPlus(ym, 1))),
    h('div.cal', null,
      h('div.wk', null, [7, 1, 2, 3, 4, 5, 6].map(d => h('span', null, LANG === 'en' ? DAY_NAMES[d - 1].slice(0, 3).toUpperCase() : lweekday(d, 'short').toLocaleUpperCase(LOCALE)))),
      h('div.grid7', null, cells)),
    h('div.small', { style: { padding: '12px 22px' } }, tr('Coloured bars mark trips and events, in their postmark ink. Tap any day to open its stamps.')),
  ];
}

function cell(date, inMonth, today) {
  const d = M.days[date], future = date > today;
  const bar = M.collections.find(c => (c.type === 'TRIP' || c.type === 'EVENT') && collCovers(c, date));
  const st = d && d.stamps[0];
  const el = h((!inMonth || future) ? 'div.cell' : 'button.cell', { class: date === today ? 'today' : null, onclick: inMonth && !future ? () => openDay(date) : null });
  if (inMonth && d && d.sealed && st) {
    el.append(stampView(st.id, D.doy(date), d.mood, 30));
    if (d.stamps.length > 1) el.append(h('span.badge', null, String(d.stamps.length)));
  } else {
    const quiet = d && !d.sealed;
    el.append(h('span.n', { style: { color: !inMonth || future ? 'var(--line)' : quiet ? 'var(--ink)' : null } }, String(D.dom(date))));
    if (quiet && inMonth) el.append(h('i.qd'));
  }
  if (bar && inMonth) el.append(h('i.bar', { style: { background: rgb(collInk(bar).rgb) } }));
  return el;
}

/** One page per month, newest first: its sealed days as little stacks, and the quiet ones as dashed gaps. */
function pages() {
  const by = {};
  Object.values(M.days).filter(d => d.stamps.length || d.mood != null).forEach(d => (by[D.ym(d.date)] = by[D.ym(d.date)] || []).push(d));
  const months = Object.keys(by).sort().reverse();
  if (!months.length) return h('div.small', { style: { padding: '22px' } }, tr('Your pages fill up as you seal days.'));
  return h('div', { style: { paddingTop: '16px' } }, months.map(ym => {
    const arr = by[ym].sort((a, b) => (a.date < b.date ? 1 : -1));
    const ss = arr.filter(d => d.sealed), n = ss.reduce((s, d) => s + d.stamps.length, 0), quiet = arr.length - ss.length;
    return grained(h('div.mpage', null,
      h('div.row', { style: { alignItems: 'flex-end' } },
        h('span.serif', { style: { fontSize: '24px' } }, monthName(+ym.slice(5))),
        h('span.it.accent', { style: { fontSize: '16px', marginInlineStart: '8px', marginBottom: '2px' } }, ROMAN[+ym.slice(5) - 1])),
      h('div.small', { style: { marginTop: '2px', marginBottom: '12px' } }, tn(n, '{n} stamp over {days}', '{n} stamps over {days}', { days: tn(ss.length, '{n} day', '{n} days') }) + (quiet ? ` · ${tn(quiet, '{n} quiet', '{n} quiet')}` : '')),
      h('div.prow', null, arr.map(d => h('button', { onclick: () => openDay(d.date) }, stack(d))))));
  }));
}

/** A sealed day as a small pile: its first stamp, with paper edges behind it for each extra one. */
function stack(d) {
  const st = d.stamps[0];
  if (!d.sealed || !st) return h('div.qday', null,
    h('span.serif', { style: { fontSize: '15px', opacity: .6 } }, String(D.dom(d.date))),
    h('span.small.b', { style: { fontSize: '10.5px' } }, tr('quiet day')));
  return h('div.stack', null,
    d.stamps.length > 2 && h('i.ed', { style: { transform: 'rotate(-5deg)' } }),
    d.stamps.length > 1 && h('i.ed', { style: { transform: 'rotate(6deg)' } }),
    stampView(st.id, D.doy(d.date), d.mood, 52, { rotation: D.dom(d.date) % 2 === 0 ? -2 : 2 }));
}
