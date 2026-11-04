'use strict';
// ui/TodayScreen.kt, ui/MoodHorizon.kt, ui/PlaceDialog.kt and the add sheet from ui/AnnalesApp.kt.

const PROMPTS = ['What made you smile?', 'Where did the light look best?', 'Who did you talk to?',
  "What would you tell next year's you?", "One thing you'd keep from today?"];

/** The region shown on the envelope: the trip's, or the home office's (GLIWICE 1 → Silesia). */
function regionOf(date) {
  const trip = M.tripOn(date);
  if (trip) return Places.of(trip.place).region;
  return Places.of(prettyOffice(M.day(date).office || M.s.office).split(' ')[0]).region;
}

/** The short version of the postmark, for the row on the day page and the back of a stamp. */
function postmarkBits(day) {
  const x = day.extras, out = [];
  if (x['wx.code'] != null && !Number.isNaN(parseInt(x['wx.code'], 10))) out.push(`${Weather.describe(parseInt(x['wx.code'], 10))[1].glyph} ${x['wx.hi']}°`);
  if (x['music.minutes'] != null) out.push(`♪ ${x['music.minutes']} min`);
  if (x['money.spent'] != null) out.push(`¤ ${x['money.spent']}`);
  if (x['augur.tone'] != null) out.push(`✶ ${x['augur.tone']}${x['augur.number'] != null ? ` №${x['augur.number']}` : ''}`);
  return out;
}

/** Today and the three days before it, so a day you didn't get to can still be added and sealed. */
function daySwitch() {
  return h('div.sw', null, [3, 2, 1, 0].map(b => {
    const date = D.plus(D.today(), -b), day = M.day(date), on = b === U.back;
    const started = day.stamps.length || day.mood != null;
    const dot = h('b', { style: {
      background: day.sealed ? (on ? 'var(--surface)' : 'var(--green)') : 'transparent',
      borderColor: day.sealed || started ? (on ? 'var(--surface)' : day.sealed ? 'var(--green)' : 'var(--acc)') : 'transparent',
    } });
    return h('button' + (on ? '.on' : ''), { onclick: () => { U.back = b; App.render(); } },
      b === 0 ? 'Today' : b === 1 ? 'Yesterday' : fmt(date, 'EEE d'), dot);
  }));
}

/**
 * One day, stamp first: the envelope and its slots, then three rows (state of mind, one line, the postmark) that each
 * open a sheet, then the Seal button. Today and any past day share it.
 */
function dayPage(date) {
  const day = M.day(date), today = date === D.today();
  if (!U.filled[date] || Date.now() - U.filled[date] > 10 * 60e3) { U.filled[date] = Date.now(); M.fillIn(date); }
  let sel = Math.max(0, Math.min(U.sel[date] || 0, day.stamps.length - 1));
  const st = day.stamps[sel] || null;
  const doy = D.doy(date);

  const page = h('div', null,
    h('div', { style: { padding: '14px 18px 8px 22px' } },
      h('div.kick', null, `№${doy} · ${fmt(date, 'EEEE · d MMM yyyy')}`),
      title(today ? 'Today, ' : fmt(date, 'd MMMM, '), day.sealed ? 'sealed' : 'not sealed yet')),
    envelope(day, st),
    strip(day, sel));

  if (st) {
    const home = prettyOffice(day.office || M.postmarkFor(date)[0]).split(' ')[0];
    page.append(
      h('div.row', { style: { justifyContent: 'center', paddingTop: '6px' } },
        h('button.pill', { onclick: () => placeDialog(st.place, home, M.placeSuggestions(date), t => M.setPlace(date, st, t)) },
          '📍', M.placeOf(date, st), h('span.accent', null, ' ✎'))));
    const acts = h('div.row', { style: { justifyContent: 'center', padding: '2px 0' } });
    if (sel > 0) acts.append(h('button.quiet', { onclick: () => { M.moveStamp(date, st, 0); U.sel[date] = 0; App.render(); } }, '★ Make first'));
    const recrop = h('button.quiet', { hidden: true, onclick: () => recropStamp(date, st) }, 'Re-crop');
    M.hasOriginal(st).then(has => { recrop.hidden = !has; });
    acts.append(recrop,
      h('button.quiet', { onclick: () => openAdd(date, st) }, 'Replace'),
      h('button.quiet.warn', { onclick: () => M.removeStamp(date, st) }, 'Remove'));
    page.append(acts);
  }

  const mood = day.mood;
  const step = (lead, label, value, done, italic, which) => h('button.step', { onclick: () => openDaySheet(date, which) },
    h('div.lead', null, lead),
    h('div.grow', { style: { paddingLeft: '14px' } }, h('div.kick', null, label), h('div.v' + (italic ? '.it' : '') + (done ? '' : '.off'), null, value)),
    h('span.chev', null, '›'));
  page.append(h('div.steps', null,
    step(mood != null ? h('i', { style: { background: Moods.css(mood) } }) : '◐', 'State of mind',
      mood != null ? Moods.names[mood] + (day.feels.length ? ' · ' + day.feels.slice(0, 2).join(', ') : '') : `How did ${today ? 'today' : 'it'} feel?`,
      mood != null, false, 'mood'),
    h('div.stepline'),
    step('✎', 'One line', day.line.trim() || 'A line about the day', !!day.line.trim(), !!day.line.trim(), 'line'),
    h('div.stepline'),
    step('✉', 'The postmark', postmarkBits(day).join('  ·  ') || 'Ink, weather, music, money', true, false, 'postmark')));

  page.append(...sealButton(day, today));
  if (today) page.append(onThisDay(date) || '');
  if (today && !isStandalone() && isIOS() && !localStorage.getItem('hideInstall')) page.prepend(installBanner());
  return page;
}

function installBanner() {
  const el = h('div.banner', null,
    h('div.grow', null,
      h('div.b', null, 'Put Annales on your Home Screen'),
      h('div.small', null, 'Tap Share, then Add to Home Screen. There it opens like an app, and Safari never clears your journal.')),
    h('button.accent.b', { onclick: () => { localStorage.setItem('hideInstall', '1'); el.remove(); } }, '✕'));
  return el;
}

/** The envelope: the selected stamp on paper, the region pill, and the №/date caption with its line. */
function envelope(day, st) {
  const ink = M.inkOn(day.date), doy = D.doy(day.date);
  const env = grained(h('div.env'));
  if (st) {
    env.append(h('button', { style: { paddingBottom: '16px' }, onclick: () => viewDay(day.date, st) },
      stampView(st.id, doy, day.mood, 160, { rotation: day.sealed ? -1.5 : 0 })));
  } else {
    env.append(h('button.addbox', { onclick: () => openAdd(day.date) },
      h('b', null, '＋'), h('span', null, 'Add a stamp'), h('span.small', null, "a photo, or the day's sky")));
  }
  env.append(
    h('div.cap', null,
      h('div.kick', null, day.sealed ? `№${doy} · ${fmt(day.date, 'd MMM yyyy')}` : `№${doy} · not sealed yet`),
      day.line.trim() && h('p', null, day.line)),
    h('div.region', null, h('i', { style: { background: rgb(ink.rgb) } }), h('span', null, regionOf(day.date).toUpperCase())));
  return env;
}

/** The three slots under the envelope, and what to do next. */
function strip(day, sel) {
  const n = day.stamps.length, doy = D.doy(day.date);
  return h('div.strip', null,
    day.stamps.map((s, i) => h('button.slot' + (i === sel ? '.on' : ''), { onclick: () => { U.sel[day.date] = i; App.render(); } },
      stampView(s.id, doy, day.mood, 40))),
    n < 3 && h('button.slot.add', { onclick: () => openAdd(day.date) }, h('b', null, '＋'), n > 0 ? 'Add' : 'Add a stamp'),
    h('div.grow', null,
      h('div.b', { style: { fontSize: '13.5px' } }, `${n} of 3 stamps`),
      h('div.small', { style: { fontSize: '12.5px' } }, n > 0 ? 'Tap one to change it' : "No photo? The day's sky becomes the stamp")));
}

/** The dark button: it says what's missing until the day can be sealed, and offers re-sealing after edits. */
function sealButton(day, today) {
  const label = M.busy ? 'Sealing…'
    : !day.stamps.length ? 'Add a stamp to seal'
    : day.mood == null ? (today ? 'Choose your state of mind to seal' : 'Choose a state of mind to seal')
    : !day.sealed ? (today ? 'Seal today' : 'Seal this day')
    : day.dirty ? 'Save & re-seal'
    : today ? 'Sealed ✓ · change anything to edit' : 'Sealed ✓ · no changes yet';
  const ready = !M.busy && day.stamps.length > 0 && day.mood != null && (!day.sealed || day.dirty);
  const done = day.sealed && !day.dirty;
  return [
    h('button.btn' + (done ? '.done' : ''), { disabled: !ready, style: { height: '54px', fontSize: '15px' }, onclick: () => sealDay(day.date) }, label),
    ready && M.s.saveToPhotos && h('div.small.c', { style: { paddingTop: '8px' } },
      day.sealed ? 'Afterwards you can save the updated stamps to Photos.' : 'Afterwards you can save each stamp to Photos.'),
  ];
}

async function sealDay(date) {
  const r = await M.seal(date);
  if (!r) return;
  if (M.s.haptics && navigator.vibrate) navigator.vibrate(40);
  const word = r.first ? 'Sealed.' : 'Re-sealed.';
  if (r.clean.length) toast(`${word} Save ${r.clean.length === 1 ? 'the stamp' : `the ${r.clean.length} stamps`} to Photos?`, () => shareFiles(r.clean), 'Save');
  else toast(word);
}

/** The same date in an earlier year: its first stamp, how it felt, and its one line. */
function onThisDay(date) {
  const past = M.onThisDay(date)[0];
  if (!past) return null;
  const s = past.stamps[0], years = D.year(date) - D.year(past.date);
  return h('div', null, sec('On this day', fmt(past.date, 'd MMM yyyy')),
    grained(h('button.otd', { onclick: () => viewDay(past.date, s) },
      stampView(s.id, D.doy(past.date), past.mood, 72, { rotation: -2 }),
      h('div.grow', null,
        h('div.small', null, (years === 1 ? 'A year ago' : `${years} years ago`) + (past.mood != null ? ` · ${Moods.names[past.mood]}` : '')),
        h('div.serif', { style: { fontSize: '18px' } }, past.line.trim() || fmt(past.date, 'EEEE d MMMM yyyy')),
        past.stamps.length > 1 && h('div.small', null, `${past.stamps.length} stamps that day`)))));
}

// ---- the three sheets ----

function openDaySheet(date, which) {
  sheet('day', () => {
    const day = M.day(date), today = date === D.today();
    const st = (t, sub) => h('div.stitle', null, h('h2', null, t), h('div.small', { style: { marginTop: '3px' } }, sub));
    let body;
    if (which === 'mood') body = [st('State of mind', `how you felt overall ${today ? 'today' : 'that day'}`),
      moodHorizon(day.moodV ?? (day.mood != null ? Math.trunc(day.mood * 100 / 6) : null), v => M.setMoodV(date, v)),
      day.mood != null && feelings(day)];
    else if (which === 'line') body = [st('One line', 'up to 200 characters'), oneLine(day, today)];
    else body = [st('The postmark', day.sealed ? 'as it was stamped' : 'filled in for you'),
      sec('Ink', M.inkOn(date) === INKS.OFFICIAL ? 'Official' : M.inkOn(date).label),
      inks(day), h('div', { style: { height: '10px' } }), postmarkCard(day),
      day.sealed && !today && h('div.small', { style: { padding: '10px 22px' } },
        `🔒  The recorded postmark stays as it was on ${fmt(date, 'd MMM yyyy')}: the office, date and hour it actually said. You can still change its ink, stamps and words.`)];
    return [body, h('button.btn', { onclick: () => Layers.close('day') }, 'Done')];
  }, { onClose: () => App.render() });
}

/**
 * The state-of-mind card. The horizon is flat at Neutral and bends as you drag: a frown towards Very Unpleasant, a smile
 * towards Very Pleasant, with a glowing orb riding the line. The name is the mood colour mixed with ink.
 */
function moodHorizon(moodV, onMood) {
  let v = moodV ?? 50, touched = false;
  const has = () => moodV != null || touched;
  const name = h('div.grow', { style: { font: '600 23px/26px var(--serif)' } });
  const hint = h('span.small', null, 'drag along the line');
  const cv = h('canvas', { style: { width: '100%', height: '76px', display: 'block', touchAction: 'none' } });
  const lab = t => h('span.kick', { style: { fontSize: '10.5px', letterSpacing: '1px', flex: t === 'NEUTRAL' ? 'none' : 1, textAlign: t === 'VERY PLEASANT' ? 'right' : 'left' } }, t);
  const card = h('div', { style: { margin: '0 16px', borderRadius: '24px', padding: '14px 14px 12px', border: '1px solid var(--line)' } },
    h('div.row', { style: { padding: '0 4px', alignItems: 'flex-end' } }, name, hint), cv,
    h('div.row', { style: { padding: '0 4px' } }, lab('VERY UNPLEASANT'), lab('NEUTRAL'), lab('VERY PLEASANT')));

  const toV = x => { const w = cv.clientWidth, x0 = 18 / 320 * w, x1 = 302 / 320 * w; return clamp(Math.trunc((x - x0) / (x1 - x0) * 100), 0, 100); };
  const paint = () => {
    const lv = Moods.levelOf(v), col = Moods.at(v / 100), tint = has() ? col : 0x9AA0A6;
    name.textContent = has() ? Moods.names[lv] : 'How did it feel?';
    name.style.color = has() ? rgb(Moods.readable(lv, K.look.ink)) : 'var(--muted)';
    hint.hidden = has();
    card.style.background = `linear-gradient(color-mix(in srgb, var(--surface) 86%, ${rgb(tint)}), var(--surface))`;
    card.style.borderColor = `color-mix(in srgb, var(--line) 70%, ${rgb(tint)})`;
    const dpr = window.devicePixelRatio || 1, W = cv.clientWidth, H = 76;
    if (!W) return;
    if (cv.width !== Math.round(W * dpr)) { cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); }
    const g = cv.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, W, H);
    const sx = W / 320, sy = H / 76, x0 = 18 * sx, x1 = 302 * sx, y = 33 * sy, t = v / 100;
    const k = (t - .5) * 2 * 21 * sy, ctrl = y + 2 * k, ox = x0 + (x1 - x0) * t, oy = y + 4 * k * t * (1 - t);
    const glow = g.createRadialGradient(ox, oy, 0, ox, oy, W * .45);
    glow.addColorStop(0, rgb(col, has() ? .32 : .12)); glow.addColorStop(1, rgb(col, 0));
    g.fillStyle = glow; g.beginPath(); g.arc(ox, oy, W * .45, 0, Math.PI * 2); g.fill();
    const grad = g.createLinearGradient(x0, 0, x1, 0);
    Moods.stops.forEach((c, i) => grad.addColorStop(i / (Moods.stops.length - 1), rgb(c)));
    const curve = () => { g.beginPath(); g.moveTo(x0, y); g.quadraticCurveTo(W / 2, ctrl, x1, y); };
    g.lineCap = 'round'; g.strokeStyle = grad;
    g.globalAlpha = .14; g.lineWidth = 9 * sy; curve(); g.stroke();
    g.globalAlpha = 1; g.lineWidth = 2.2 * sy; curve(); g.stroke();
    for (let i = 0; i <= 6; i++) {
      const tx = x0 + (x1 - x0) * i / 6, on = has() && lv === i;
      g.strokeStyle = on ? rgb(col) : rgb(K.look.ink, .25); g.lineWidth = (on ? 2 : 1) * sy;
      g.beginPath(); g.moveTo(tx, (on ? 62 : 64) * sy); g.lineTo(tx, 68 * sy); g.stroke();
    }
    g.fillStyle = rgb(col, has() ? .4 : .15); g.beginPath(); g.arc(ox, oy, 16 * sy, 0, Math.PI * 2); g.fill();
    const orb = g.createRadialGradient(ox - 3 * sy, oy - 3.5 * sy, 0, ox - 3 * sy, oy - 3.5 * sy, 14 * sy);
    orb.addColorStop(0, '#fff'); orb.addColorStop(.5, rgb(col)); orb.addColorStop(1, rgb(col, .85));
    g.fillStyle = orb; g.beginPath(); g.arc(ox, oy, 10.5 * sy, 0, Math.PI * 2); g.fill();
    g.strokeStyle = '#fff'; g.lineWidth = 2.4 * sy; g.stroke();
  };
  let down = false;
  cv.addEventListener('pointerdown', e => { down = true; cv.setPointerCapture(e.pointerId); touched = true; v = toV(e.offsetX); paint(); });
  cv.addEventListener('pointermove', e => { if (!down) return; v = toV(e.offsetX); paint(); });
  const up = () => { if (!down) return; down = false; onMood(v); };
  cv.addEventListener('pointerup', up); cv.addEventListener('pointercancel', up);
  new ResizeObserver(paint).observe(cv);
  paint();
  return card;
}

/** The two questions under the horizon: feelings first (ten, then "Show more"), then what had the biggest impact. */
function feelings(day) {
  const fs = Tags.feelsFor(day.mood);
  const list = [...(U.moreFeels ? fs : fs.slice(0, 10)), ...day.feels.filter(f => !fs.includes(f))];
  const q = t => h('div.b', { style: { fontSize: '14px', padding: '16px 0 9px' } }, t);
  return h('div', { style: { padding: '0 18px' } },
    q('What best describes this feeling?'),
    h('div.wrap', null, list.map(f => h('button.tag' + (day.feels.includes(f) ? '.on' : ''), { onclick: () => M.toggleFeel(day.date, f) }, f)),
      fs.length > 10 && h('button.accent', { style: { fontSize: '13.5px', fontWeight: 800, padding: '8px 6px' }, onclick: () => { U.moreFeels = !U.moreFeels; Layers.refreshAll(); } }, U.moreFeels ? 'Show less' : 'Show more')),
    q("What's having the biggest impact?"),
    h('div.wrap', null, Tags.factors.map(f => h('button.tag' + (day.factors.includes(f) ? '.on' : ''), { onclick: () => M.toggleFactor(day.date, f) }, f))));
}

/** "One line": three prompts to start from, and up to 200 characters in italic serif. */
function oneLine(day, today) {
  const start = D.dom(day.date) % 3;
  const count = h('div.small', { style: { fontSize: '12px', textAlign: 'right', marginTop: '4px' } }, `${day.line.length} / 200`);
  const ta = h('textarea', {
    value: day.line, maxLength: 200, rows: 2,
    placeholder: today ? "Leaves everywhere, tea at Ola's…" : 'What do you remember about this day?',
    style: { width: '100%', border: 0, outline: 'none', background: 'transparent', resize: 'none', font: 'italic 600 18px/24px var(--serif)' },
    oninput: e => { M.setLine(day.date, e.target.value); count.textContent = `${e.target.value.length} / 200`; },
    onblur: () => App.render(),
  });
  return [
    h('div.hscroll', { style: { paddingBottom: '8px' } }, PROMPTS.slice(start, start + 3).map(p =>
      h('button.pill.it', { style: { fontSize: '14px', color: 'var(--muted)', fontWeight: 600, flex: 'none' }, onclick: () => { ta.placeholder = p; ta.focus(); } }, p))),
    h('div', { style: { margin: '0 16px', background: 'var(--surface)', borderRadius: '18px', padding: '16px' } }, ta, count),
  ];
}

/** "Official · Poczta Polska" (or the trip's post office), then the two reds. */
function inks(day) {
  const trip = M.tripOn(day.date), post = trip ? Places.of(trip.place).post : 'Poczta Polska', cur = M.inkOn(day.date);
  return [
    h('div.hscroll', { style: { gap: '8px' } }, Object.values(INKS).map(ink =>
      inkChip(ink, ink === cur, ink === INKS.OFFICIAL ? `Official · ${post}` : ink.label, () => M.setDayInk(day.date, ink.name)))),
    h('div.small', { style: { padding: '8px 22px' } }, cur === INKS.OFFICIAL ? 'Black, the way the post office itself would stamp it.' : 'Your own touch: the post office would use black.'),
  ];
}

function row1(ic, b, s, src, { faded = false, swatches = [] } = {}) {
  return h('div.r1' + (faded ? '.faded' : ''), null, h('span.g', null, ic),
    h('div.grow', null, h('div.bb', null, b), h('div.s', null, swatches.map(c => h('i.sw2', { style: { background: c } })), s)),
    h('span.src', null, src));
}

/** What the postmark says: the office and date bridge, the moon, the weather, and what the sibling apps shared. */
function postmarkCard(day) {
  const [mark, bottom] = M.postmarkFor(day.date);
  const office = day.sealed ? day.office || mark : mark, under = day.sealed ? day.bottom || bottom : bottom;
  const hour = day.sealed && day.sealedAt > 0 ? D.hourOf(day.sealedAt) : null;
  const x = day.extras, rows = [];
  rows.push(row1('✉', `${office}${under ? ' · ' + under : ''}`, fmt(day.date, 'dd.MM.yy') + (hour != null ? ` · hour ${hour}` : ' · the hour you seal it'), M.tripOn(day.date) ? 'Trip' : 'Profile'));
  rows.push(row1('☾', `${Moon.name(day.date)} · ${Math.round(Moon.lit(day.date) * 100)}% lit`, 'Calculated, exact', 'Caelum'));
  const code = parseInt(x['wx.code'], 10);
  if (!Number.isNaN(code)) {
    const [what, sky] = Weather.describe(code);
    const agree = { agree: 'models agree', roughly: 'models roughly aligned', diverge: 'models diverge' }[x['wx.agree']] || '';
    rows.push(row1(sky.glyph, `${what} · ${x['wx.hi']}° / ${x['wx.lo']}°`, `${x['wx.models']} models · ${agree}`, 'Consensus'));
  } else rows.push(row1('☁', 'Weather', D.between(day.date, D.today()) > 90 ? 'Only kept for the last three months' : 'Needs a connection to fetch', 'Consensus', { faded: true }));
  const android = 'Shared on Android only, for now';
  if (x['music.track'] != null) rows.push(row1('♪', `${x['music.track']}${x['music.artist'] != null ? ' · ' + x['music.artist'] : ''}`, `${x['music.minutes'] || '0'} min listened`, 'Fidelis'));
  else if (x['music.minutes'] != null) rows.push(row1('♪', `${x['music.minutes']} min of music`, 'no track played through yet', 'Fidelis'));
  else rows.push(row1('♪', 'Top track', android, 'Fidelis', { faded: true }));
  if (x['money.spent'] != null) rows.push(row1('¤', `${x['money.spent']} spent`, x['money.safe'] != null ? `safe to spend ${x['money.safe']} a day` : "safe to spend wasn't noted that day", 'Kalendae'));
  else rows.push(row1('¤', 'Spent vs safe-to-spend', android, 'Kalendae', { faded: true }));
  if (x['augur.tone'] != null) {
    const sub = [x['augur.colour'], x['augur.agree'] != null ? `${x['augur.agree']} systems agree` : null].filter(Boolean).join(' · ') || 'where the systems agree';
    const sw = (x['augur.colourHex'] || '').split(',').map(s => s.trim()).filter(s => /^#?[0-9a-f]{6,8}$/i.test(s)).slice(0, 3).map(s => (s.startsWith('#') ? s : '#' + s));
    rows.push(row1('✶', x['augur.tone'] + (x['augur.number'] != null ? ` · №${x['augur.number']}` : ''), sub, 'Augur', { swatches: sw }));
  } else rows.push(row1('✶', "The day's tone", android, 'Augur', { faded: true }));
  return h('div.pmc', null, rows);
}

// ---- adding a stamp ----

/**
 * "Add a stamp": where it was taken, then a photo (taken now or from the library) or a drawn stamp of the day's sky.
 * The sky choices are the real drawings, in this day's mood colour.
 */
function openAdd(date, replacing = null) {
  const state = { place: replacing ? replacing.place : '' };
  const home = prettyOffice(M.postmarkFor(date)[0]).split(' ')[0];
  const day = M.day(date);
  const code = parseInt(day.extras['wx.code'], 10);
  const suggested = Number.isNaN(code) ? null : Weather.describe(code)[1];
  const mood = day.mood != null ? Moods.color(day.mood) : null;
  const previews = {};
  const picked = async e => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    Layers.close('add');
    try { openCrop(date, await Photos.load(file), replacing, state.place); } catch (err) { toast("Couldn't open that photo."); }
  };

  sheet('add', layer => [
    h('div.stitle', { style: { padding: '0 0 0 0' } }, h('h2', { style: { fontSize: '24px', lineHeight: '28px' } }, replacing ? 'Replace this stamp' : 'Add a stamp')),
    h('button.row', { style: { marginTop: '12px', width: '100%', background: 'var(--page)', borderRadius: '14px', padding: '11px 14px', textAlign: 'left' },
      onclick: () => placeDialog(state.place, home, M.placeSuggestions(date), t => { state.place = t; layer.refresh(); }) },
      h('span', { style: { fontSize: '16px' } }, '📍'),
      h('div.grow', { style: { paddingLeft: '10px' } }, h('div.kick', null, 'Taken in'), h('div.b', null, state.place || home)),
      h('span.small.accent.b', null, 'Change')),
    h('div.row', { style: { gap: '10px', marginTop: '10px', alignItems: 'stretch' } },
      h('label.src2', null, h('b', null, '◉'), h('strong', null, 'Take a photo'), h('span', null, 'Right now'),
        h('input', { type: 'file', accept: 'image/*', capture: 'environment', onchange: picked })),
      h('label.src2', null, h('b', null, '▦'), h('strong', null, 'From library'), h('span', null, 'Any photo'),
        h('input', { type: 'file', accept: 'image/*', onchange: picked }))),
    h('div.kick', { style: { marginTop: '20px', marginBottom: '3px' } }, "Or the day's sky"),
    h('div.small', null, suggested ? `Consensus Weather says ${suggested.label.toLowerCase()}. Drawn with that night's moon, in the day's mood colour.`
      : "Drawn with that night's moon, in the day's mood colour."),
    h('div.row', { style: { gap: '8px', marginTop: '12px', alignItems: 'flex-start' } }, SKY_LIST.map(wx => {
      const box = h('div.p', { style: { background: css(wx.mid) } });
      if (previews[wx.name]) box.append(previews[wx.name]);
      return h('button.sky' + (wx === suggested ? '.on' : ''), {
        onclick: async () => { Layers.close('add'); await M.addSky(date, wx, replacing, state.place); },
      }, box, h('span', null, wx.label.split(' ')[0]));
    })),
    h('button', { style: { width: '100%', padding: '14px', marginTop: '6px', color: 'var(--muted)' }, onclick: () => Layers.close('add') }, 'Cancel'),
  ], { surface: true });

  // the previews, drawn one by one so the sheet opens at once
  (async () => {
    for (const wx of SKY_LIST) {
      await new Promise(r => setTimeout(r, 16));
      if (!Layers.has('add')) return;
      previews[wx.name] = Sky.draw(date, wx, mood, (D.epochDay(date) * 31) | 0, 200, 250);
      const l = Layers.list.find(x => x.key === 'add'); if (l) l.refresh();
    }
  })();
  // today, with location already allowed: start from where you are
  if (!replacing && !state.place && date === D.today() && navigator.permissions) {
    navigator.permissions.query({ name: 'geolocation' }).then(p => {
      if (p.state !== 'granted') return;
      hereTown().then(t => {
        if (t && t.toLowerCase() !== home.toLowerCase() && !state.place) { state.place = t; const l = Layers.list.find(x => x.key === 'add'); if (l) l.refresh(); }
      }).catch(() => {});
    }).catch(() => {});
  }
}

async function recropStamp(date, s) {
  const photo = await M.original(s);
  if (!photo) { toast("The original photo isn't kept for this stamp."); return; }
  openCrop(date, photo, s, null);
}

/**
 * "Where was this?": type a town, pick one used before, or "Use where I am now". [home] is what an empty answer means
 * (the day's post office). Calls [done] with the town, or "" for the day's office.
 */
function placeDialog(current, home, suggestions, done) {
  const st = { text: current, finding: false, note: null };
  const input = h('input.field', { value: st.text, placeholder: home, oninput: e => { st.text = e.target.value; } });
  const save = () => { Layers.close('place'); done(st.text.trim().toLowerCase() === home.toLowerCase() ? '' : st.text.trim()); };
  dialog('place', 'Where was this?', () => {
    input.value = st.text;
    return h('div', null,
      h('div.small', null, `The town goes on this stamp's postmark. Leave it empty for ${home}.`),
      h('div', { style: { marginTop: '12px' } }, input),
      h('button.b.accent', {
        style: { padding: '12px 0 6px' }, disabled: st.finding,
        onclick: async () => {
          st.finding = true; st.note = null; refresh();
          try { const t = await hereTown(); if (t) st.text = t; else st.note = 'Couldn\'t tell where you are.'; }
          catch (e) { st.note = e && e.code === 1 ? "Location wasn't allowed. You can still type the town." : "Couldn't tell where you are. Is location on?"; }
          st.finding = false; refresh();
        },
      }, st.finding ? 'Finding where you are…' : '◎  Use where I am now'),
      st.note && h('div.small', null, st.note),
      suggestions.length > 0 && h('div.wrap', { style: { marginTop: '10px' } }, suggestions.map(s => {
        const on = s.toLowerCase() === st.text.trim().toLowerCase();
        return h('button.small.b', { style: { color: on ? 'var(--surface)' : 'var(--ink)', background: on ? 'var(--ink)' : 'var(--page)', border: '1px solid var(--line)', borderRadius: '99px', padding: '7px 11px' },
          onclick: () => { st.text = s; refresh(); } }, s);
      })));
  }, [['Cancel', () => Layers.close('place'), 'var(--muted)'], ['Save', save]]);
  const refresh = () => { const l = Layers.list.find(x => x.key === 'place'); if (l) { document.activeElement && document.activeElement.blur(); l.refresh(); } };
}
