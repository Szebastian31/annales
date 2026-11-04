'use strict';
// ui/CollectionsScreen.kt and ui/CollectionForm.kt: trips, events, anniversaries and themes.

function rangeText(c) {
  if (c.from === c.to) return fmt(c.from, 'd MMM yyyy');
  if (D.ym(c.from) === D.ym(c.to)) return `${D.dom(c.from)} – ${fmt(c.to, 'd MMM yyyy')}`;
  if (D.year(c.from) === D.year(c.to)) return `${fmt(c.from, 'd MMM')} – ${fmt(c.to, 'd MMM yyyy')}`;
  return `${fmt(c.from, 'd MMM yyyy')} – ${fmt(c.to, 'd MMM yyyy')}`;
}

function collLines(c) {
  const n = M.count(c), stamps = plural(n, 'stamp');
  switch (c.type) {
    case 'TRIP': { const p = Places.of(c.place); return [`${rangeText(c)} · ${p.region}`, `${stamps} · postmarked ${p.mark}`]; }
    case 'EVENT': return [rangeText(c), stamps];
    case 'ANNIV': {
      const years = M.entries(c).filter(e => e[1].length).length;
      return [`Every ${c.day} ${MONTH_NAMES[c.month - 1]} · ${plural(years, 'year')} so far`, stamps];
    }
    default: return [`${n} hand-picked stamp${n === 1 ? '' : 's'}`, "Add from any stamp's page"];
  }
}

const typeTag = (c, withInk = false) => h('div.tt', null, h('i', { style: { background: rgb(collInk(c).rgb) } }),
  COLL_TYPES[c.type].label + (withInk && c.type !== 'THEME' ? ` · ${collInk(c).label} ink` : ''));
const ghost = (type, label) => h('div.ghost', null, h('b', null, COLL_TYPES[type].glyph), label);

function collectionsScreen() {
  const all = [...M.collections].sort((a, b) => {
    const k = c => (c.type === 'THEME' || c.type === 'ANNIV' ? '' : c.from);
    return k(a) < k(b) ? 1 : k(a) > k(b) ? -1 : 0;
  });
  const f = U.collFilter, list = all.filter(c => !f || c.type === f);
  const chip = (label, on, fn) => h('button.chip' + (on ? '.on' : ''), { onclick: fn }, label);
  return h('div', null,
    header(plural(all.length, 'collection'), 'Your ', 'collections'),
    h('div.hscroll', null,
      chip(`All ${all.length}`, !f, () => { U.collFilter = null; App.render(); }),
      Object.values(COLL_TYPES).map(t => chip(`${t.plural} ${all.filter(c => c.type === t.name).length}`, f === t.name, () => { U.collFilter = t.name; App.render(); }))),
    h('div', { style: { height: '10px' } }),
    !list.length && h('div.small', { style: { padding: '12px 22px' } },
      f ? `No ${COLL_TYPES[f].plural.toLowerCase()} yet.` : 'Nothing here yet. A trip, a wedding, an anniversary or a theme like Coffee: make one below.'),
    list.map(c => {
      const cv = M.cover(c), [l1, l2] = collLines(c);
      return h('button.coll', { onclick: () => { U.openColl = c.id; App.render(); document.getElementById('main').scrollTop = 0; } },
        h('div.cv', null, cv ? stampView(cv[1].id, D.doy(cv[0].date), cv[0].mood, 64, { rotation: -2 }) : ghost(c.type, 'empty')),
        h('div.grow', null, typeTag(c), h('h3', null, c.name), h('div.small', null, l1), h('div.small', null, l2)));
    }),
    h('button.newc', { onclick: () => openCollForm(blankColl(f || 'TRIP')) }, '＋ New collection'));
}

const blankColl = type => ({ id: 0, type, name: '', place: '', from: D.today(), to: D.plus(D.today(), 2), month: D.month(D.today()), day: D.dom(D.today()), ink: null, note: '', ids: [], cover: null });

/** One collection: a header card, then each day with its stamps, or the hand-picked grid for a theme. */
function collectionScreen(c) {
  const entries = M.entries(c), all = entries.flatMap(([d, ss]) => ss.map(s => [d.date, s]));
  const cv = M.cover(c), [l1, l2] = collLines(c), today = D.today();
  const open = s => openViewer(all, all.findIndex(e => e[1].id === s.id), { inside: c });
  const out = h('div', null,
    h('div.row', { style: { padding: '0 8px' } },
      h('button.back', { onclick: () => { U.openColl = null; App.render(); } }, '‹ Collections'), h('div.grow'),
      h('button.back.b', { onclick: () => openCollForm(c) }, 'Edit')),
    h('div.row', { style: { margin: '0 16px', background: 'var(--page)', borderRadius: '18px', padding: '16px' } },
      h('div', { style: { width: '100px', height: '124px', display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' } },
        cv ? stampView(cv[1].id, D.doy(cv[0].date), cv[0].mood, 94, { rotation: -2 }) : ghost(c.type, 'cover')),
      h('div.grow', { style: { marginLeft: '14px' } }, typeTag(c, true),
        h('div.serif', { style: { fontSize: '24px', lineHeight: '29px' } }, c.name), h('div.small', null, l1), h('div.small', null, l2))),
    c.note.trim() && h('div.body.it', { style: { color: 'var(--muted)', padding: '12px 22px', whiteSpace: 'pre-wrap' } }, c.note));

  if (c.type === 'THEME') {
    out.append(all.length
      ? h('div.sgrid', null, all.map(([date, s]) => h('button', { onclick: () => open(s) }, stampView(s.id, D.doy(date), M.day(date).mood, 96))))
      : h('div.small', { style: { padding: '22px' } }, `Empty for now. Open any stamp and tap ＋ ${c.name} to add it here.`));
  } else {
    entries.forEach(([d, ss], i) => {
      const label = c.type === 'ANNIV' ? `${D.year(d.date)} · ${DAY_NAMES[D.dow(d.date) - 1]}` : `Day ${i + 1} · ${fmt(d.date, 'EEE d MMM')}`;
      const row = h('div.row', { style: { gap: '10px', marginTop: '6px', flexWrap: 'wrap' } },
        ss.map(s => h('button', { onclick: () => open(s) }, stampView(s.id, D.doy(d.date), d.mood, 66))));
      if (d.date > today) row.append(h('span.small', null, 'Not yet. Stamps you seal that day land here.'));
      else if (!d.sealed && d.stamps.length) row.append(h('button.quiet', { style: { padding: '10px 0' }, onclick: () => openDay(d.date, c) }, 'Seal this day to add it here'));
      else if (!d.sealed) row.append(h('button.quiet', { style: { padding: '10px 0', fontWeight: 600 }, onclick: () => openDay(d.date, c) }, '＋ Add a stamp'));
      out.append(h('div', { style: { padding: '8px 22px' } }, h('div.kick', null, label), row));
    });
    if (c.type === 'ANNIV' && !entries.length) out.append(h('div.small', { style: { padding: '22px' } }, 'Seal a day on this date and it lands here, every year.'));
  }
  return out;
}

/** New or edit collection. [start] is the existing one, or a blank of the chosen type. */
function openCollForm(start) {
  const isNew = start.id === 0;
  const f = { ...start };
  let confirmDelete = false;
  const HINT = { TRIP: 'Weekend in Kraków', EVENT: "Ola's wedding", ANNIV: 'Our anniversary', THEME: 'Coffee' };
  const layer = () => Layers.list.find(l => l.key === 'form');
  const redo = () => { if (document.activeElement) document.activeElement.blur(); layer().refresh(); };
  const ok = () => f.name.trim() && (f.type !== 'TRIP' || f.place.trim()) && (!['TRIP', 'EVENT'].includes(f.type) || f.to >= f.from);
  let saveBtn;
  const check = () => { if (saveBtn) saveBtn.disabled = !ok(); };

  const textField = (label, key, ph, multi = false) => h('div', { style: { marginTop: '10px' } },
    h('label.lab', null, label),
    h(multi ? 'textarea.field' : 'input.field', { value: f[key], placeholder: ph, rows: multi ? 3 : null, style: multi ? { resize: 'none' } : null,
      oninput: e => { f[key] = e.target.value; check(); } }));
  const dateField = (label, value, iso, onPick, error = false) => h('label.dfield', null,
    h('div.small', { style: { fontSize: '12.5px', color: error ? 'var(--warn)' : null } }, label),
    h('div.body', null, value),
    h('input', { type: 'date', value: iso, onchange: e => { if (e.target.value) { onPick(e.target.value); redo(); } } }));

  Layers.open('form', () => {
    const p = Places.of(f.type === 'TRIP' ? (f.place.trim() || 'Somewhere') : 'Gliwice'), ink = collInk(f);
    const annIso = D.of(D.year(D.today()), f.month, Math.min(f.day, D.lengthOfMonth(D.year(D.today()), f.month)));
    saveBtn = h('button.btn.acc', { disabled: !ok(), style: { width: '100%', margin: '18px 0 0' }, onclick: () => {
      const saved = M.saveCollection({ ...f, name: f.name.trim(), place: f.place.trim() });
      Layers.close('form'); U.tab = 'collections'; U.openColl = saved.id; App.render();
    } }, isNew ? 'Create collection' : 'Save');
    return [
      h('div.row', { style: { padding: '0 8px' } },
        h('button.back', { onclick: () => Layers.close('form') }, '‹ Back'),
        h('div.grow.c.serif', { style: { fontSize: '18px' } }, isNew ? 'New collection' : 'Edit collection'),
        h('div', { style: { width: '72px' } })),
      h('div.scroll', { style: { padding: '8px 16px 30px' } },
        h('div.types', null, Object.values(COLL_TYPES).map(t => h('button' + (t.name === f.type ? '.on' : ''), {
          disabled: !isNew && t.name !== f.type, onclick: () => { f.type = t.name; redo(); },
        }, h('div.body.b', null, `${t.glyph} ${t.label}`), h('div.small', { style: { fontSize: '12.5px', lineHeight: '17px' } }, t.about)))),
        textField('Name', 'name', HINT[f.type]),
        f.type === 'TRIP' && [
          textField('Where', 'place', 'City'),
          h('div.hscroll', { style: { padding: '8px 0' } }, Places.suggestions.map(s => h('button.chip' + (f.place === s ? '.on' : ''), {
            onclick: () => { f.place = s; if (!f.name.trim()) f.name = s; redo(); },
          }, s))),
        ],
        (f.type === 'TRIP' || f.type === 'EVENT') && h('div.row', { style: { gap: '8px', marginTop: '8px', alignItems: 'stretch' } },
          dateField('From', fmt(f.from, 'EEE d MMM yyyy'), f.from, v => { f.from = v; if (f.to < v) f.to = v; }),
          dateField('To', fmt(f.to, 'EEE d MMM yyyy'), f.to, v => { f.to = v; }, f.to < f.from)),
        f.type === 'ANNIV' && h('div.row', { style: { marginTop: '8px' } },
          dateField('Date · repeats every year', `${f.day} ${MONTH_NAMES[f.month - 1]}`, annIso, v => { f.month = D.month(v); f.day = D.dom(v); })),
        f.type !== 'THEME' ? [
          h('div.kick', { style: { padding: '16px 0 6px' } }, 'Postmark ink'),
          h('div.row', { style: { gap: '8px', flexWrap: 'wrap' } }, Object.values(INKS).map(i => inkChip(i, i === ink, null, () => { f.ink = i === INKS.OFFICIAL ? null : i.name; redo(); }))),
          h('div.small', { style: { marginTop: '6px' } },
            f.type !== 'TRIP' ? 'Changes how this collection is labelled. Days are stamped by your own post office.'
              : ink === INKS.OFFICIAL ? `Days in this trip are stamped ${p.mark}${p.bottom ? ' · ' + p.bottom : ''}, in black, like ${p.post} would.`
                : `Days in this trip are stamped ${p.mark}, in ${ink.label.toLowerCase()}: your own touch.`),
        ] : h('div.small', { style: { marginTop: '12px' } }, `Themes don't change postmarks. Add stamps from any stamp's page with ＋ ${f.name.trim() || 'this theme'}.`),
        textField('Story', 'note', 'What is this collection about?', true),
        saveBtn,
        !isNew && [
          h('button.b.warn', { style: { width: '100%', padding: '14px 0 6px' }, onclick: () => {
            if (!confirmDelete) { confirmDelete = true; redo(); return; }
            M.deleteCollection(start); Layers.close('form'); U.openColl = null; App.render();
            toast('Collection deleted. Its stamps are still in your journal.');
          } }, confirmDelete ? 'Tap again to delete' : 'Delete collection'),
          h('div.small.c', null, 'Deleting a collection keeps every stamp in your journal.'),
        ]),
    ];
  }, { className: 'full' });
}
