'use strict';
// ui/ProfileScreen.kt: the look, the perforation, the postmark, photos, sibling sources and the backup file.

function profileScreen() {
  const ins = new Insights(Object.values(M.days), M.collections);
  const sealed = Object.values(M.days).filter(d => d.sealed);
  const stamps = sealed.reduce((s, d) => s + d.stamps.length, 0);
  const card = (...k) => h('div.card', { style: { margin: '0 16px' } }, ...k);
  const av = h('div.av', null, (M.s.name.trim().charAt(0) || 'A').toUpperCase());
  const cutAbout = SR.cuts.find(c => c[0] === M.s.cut) || SR.cuts[0];
  const lookTile = (key, l) => h('button' + (key === K.name ? '.on' : ''), { style: { background: rgb(l.bg) }, onclick: () => { M.set('look', key); applyLook(key); App.render(); } },
    h('div.row', { style: { height: '26px', background: rgb(l.surface), borderRadius: '6px', padding: '6px', gap: '4px' } },
      h('i', { style: { width: '8px', height: '8px', borderRadius: '50%', background: rgb(l.acc), flex: 'none' } }),
      h('i', { style: { flex: 1, height: '4px', borderRadius: '2px', background: rgb(l.ink, .5) } })),
    h('div.serif', { style: { fontSize: '15px', color: rgb(l.ink), marginTop: '6px' } }, l.label),
    h('div', { style: { fontSize: '12px', color: rgb(l.muted) } }, l.about));
  const cutTile = ([k, n]) => {
    const on = k === M.s.cut, dpr = window.devicePixelRatio || 1;
    const c = SR.silhouette(k, 36 * dpr, 45 * dpr, on ? 'rgb(0,0,0)' : 'rgb(0,0,0)');
    // tinted with the look: the silhouette is a mask, painted in the current accent or muted colour
    const g = c.getContext('2d'); g.globalCompositeOperation = 'source-in'; g.fillStyle = rgb(on ? K.look.acc : K.look.muted); g.fillRect(0, 0, c.width, c.height);
    return h('button' + (on ? '.on' : ''), { onclick: () => M.changeCut(k) }, c, h('span', { style: { marginTop: '4px', whiteSpace: 'nowrap' } }, n));
  };
  const field = (label, key) => h('div', { style: { marginTop: '12px' } }, h('label.lab', null, label),
    h('input.field', { value: M.s[key], autocapitalize: key === 'office' ? 'characters' : null, oninput: e => M.set(key, e.target.value), onblur: () => App.render() }));

  const sources = [
    ['Consensus Weather', 'Weather, temperature, model agreement', M.hasShared('wx.') ? ['same 13 models', 'var(--green)'] : ['fetched when online', 'var(--muted)']],
    ['Caelum Sky', 'Moon phase, what was overhead', ['calculated here', 'var(--green)']],
    ['Fidelis Music', 'Top track and listening time', M.hasShared('music.') ? ['from an Android backup', 'var(--green)'] : ['Android only', 'var(--muted)']],
    ['Kalendae', 'Spent vs safe-to-spend', M.hasShared('money.') ? ['from an Android backup', 'var(--green)'] : ['Android only', 'var(--muted)']],
    ['Augur', 'Daily tone, just for fun', M.hasShared('augur.') ? ['from an Android backup', 'var(--green)'] : ['Android only', 'var(--muted)']],
  ];

  return h('div', null,
    header('Profile', 'Your ', 'annales'),
    card(
      h('div.row', null, av, h('div.grow', { style: { marginLeft: '14px' } },
        h('input.nameIn', { value: M.s.name, placeholder: 'Your name', maxLength: 40, oninput: e => { M.set('name', e.target.value.slice(0, 40)); av.textContent = (e.target.value.trim().charAt(0) || 'A').toUpperCase(); } }),
        h('div.small', null, `${prettyOffice(M.s.office).split(' ')[0]} · keeping annales since ${fmt(M.since, 'MMM yyyy')}`))),
      h('div.sum', { style: { margin: '14px 0 0', padding: 0 } }, [[stamps, 'stamps'], [sealed.length, 'days'], [ins.streak, 'streak'], [M.collections.length, 'collections']]
        .map(([n, l]) => h('div', null, h('b', null, String(n)), h('span', null, l))))),

    sec('Look', K.look.label),
    card(h('div.looks', null, Object.entries(LOOKS).map(([k, l]) => lookTile(k, l)))),

    sec('Perforation', cutAbout[1]),
    card(h('div.cuts', null, SR.cuts.map(cutTile)),
      h('div.small', { style: { marginTop: '10px' } }, `${cutAbout[1]}: ${cutAbout[2]}. Unsealed stamps are re-cut straight away; sealed ones keep their edge.`)),

    sec('Default postmark ink'),
    card(h('div.row', { style: { gap: '8px', flexWrap: 'wrap' } }, Object.values(INKS).map(i =>
        inkChip(i, i.name === M.s.ink, i === INKS.OFFICIAL ? 'Official · Poczta Polska' : i.label, () => { M.set('ink', i.name); App.render(); }))),
      h('div.small', { style: { marginTop: '10px' } }, "Official stamps it the way the local post office would: black ink and their own layout. A Poczta Polska datownik at home, the city's own office on a trip. Any other ink is your own touch."),
      field('Your post office', 'office'), field('Postcode', 'postcode'),
      h('div.small', { style: { marginTop: '8px' } }, 'Printed round your datownik: the office over the top, the postcode underneath.')),

    sec('Photos'),
    card(
      toggleRow('Offer sealed stamps for Photos', 'After sealing, a Save button hands each clean perforated stamp to the share sheet, where Save Image puts it in Photos', M.s.saveToPhotos, v => M.set('saveToPhotos', v)),
      toggleRow('Keep original photos', 'So you can re-crop a stamp any time', M.s.keepOriginals, v => M.set('keepOriginals', v))),

    sec('Postmark sources', 'sibling apps'),
    card(sources.map(([n, what, [status, col]]) => h('div.row', { style: { padding: '7px 0' } },
        h('div.grow', null, h('div.body', null, n), h('div.small', null, what)),
        h('span.small.b', { style: { fontSize: '12.5px', color: col } }, status))),
      h('div.small', { style: { marginTop: '6px' } }, "Weather is the Consensus Weather forecast, fetched from Open-Meteo for your post office's town (or the trip's city). On iPhone, apps can't pass data to each other, so music, money and Augur's tone only appear on days restored from an Android backup.")),

    sec('Ritual & privacy'),
    card(
      toggleRow('Seal haptics', 'A short buzz when you seal, where the browser allows it', M.s.haptics, v => M.set('haptics', v)),
      toggleRow('On this device only', 'No account, no cloud, no analytics. Internet only for the weather', true, null),
      !isStandalone() && toggleRow('Add to Home Screen', 'In Safari: Share, then Add to Home Screen. It opens like an app, and Safari never clears its journal there', false, null),
      backupRow()),
    h('div.small', { style: { padding: '14px 22px' } }, 'Annales 1.0.0 · web. The same backup files as the Android app.'));
}

/** "Encrypted backup file": save everything to a file you keep, optionally locked with a password; or restore one. */
function backupRow() {
  const pick = h('input', { type: 'file', accept: 'application/json,.json,*/*', hidden: true, onchange: async e => {
    const file = e.target.files && e.target.files[0];
    e.target.value = '';
    if (!file) return;
    let o;
    try { o = Backup.read(await file.text()); } catch (err) { toast("That file isn't an Annales backup."); return; }
    if (o.locked) askPassword('open', pw => restoreBackup(o, pw));
    else confirmRestore(() => restoreBackup(o, null));
  } });
  return h('div', { style: { padding: '6px 0' } },
    h('div.body', null, 'Encrypted backup file'),
    h('div.small', null, 'Everything, photos included, in the same format as the Android app and Kalendae. Lock it with a password (AES-256).'),
    h('div.row', { style: { gap: '8px', marginTop: '8px' } },
      h('button.out', { disabled: M.busy, onclick: () => askPassword('save', saveBackup) }, 'Save a backup'),
      h('button.out', { disabled: M.busy, onclick: () => pick.click() }, 'Restore…'), pick));
}

async function saveBackup(pw) {
  let blob;
  const ok = await M.work(async () => { blob = await Backup.write(pw || null); });
  if (!ok) return;
  const file = new File([blob], `annales-backup-${D.today()}.json`, { type: 'application/json' });
  // the share sheet needs a fresh tap after the slow part, so it's offered from the message
  toast(pw ? 'Backup ready, locked with your password.' : 'Backup ready.', () => shareFiles([file]), 'Save file');
}

async function restoreBackup(o, pw) {
  let msg;
  M.busy = true; App.render();
  try {
    await Backup.restore(o, pw);
    msg = `Restored ${Object.values(M.days).reduce((s, d) => s + d.stamps.length, 0)} stamps from the backup.`;
  } catch (e) {
    msg = e.message === 'wrong' ? "That password doesn't open this backup."
      : e.message === 'not' ? "That file isn't an Annales backup." : `Couldn't restore it: ${e.message || e}`;
  } finally { M.busy = false; U.openDay = null; U.openColl = null; App.render(); }
  toast(msg);
}

function confirmRestore(go) {
  dialog('restore', 'Replace this journal?', () => h('div.small', null, 'Restoring replaces everything here with the backup: days, stamps, collections and settings.'),
    [['Cancel', () => Layers.close('restore'), 'var(--muted)'], ['Restore', () => { Layers.close('restore'); go(); }]]);
}

function askPassword(which, done) {
  const input = h('input.field', { type: 'password', placeholder: 'Password', autocomplete: which === 'save' ? 'new-password' : 'current-password', style: { marginTop: '10px' } });
  dialog('pw', which === 'save' ? 'Lock the backup?' : 'This backup is locked', () => h('div', null,
    h('div.small', null, which === 'save' ? "Type a password to lock it, or leave it empty. There's no way to open a locked backup without it."
      : 'Type the password it was saved with. Restoring replaces everything on this device.'),
    input),
  [['Cancel', () => Layers.close('pw'), 'var(--muted)'], [which === 'save' ? 'Make the file' : 'Restore', () => { const pw = input.value; Layers.close('pw'); done(pw); }]]);
  setTimeout(() => input.focus(), 50);
}
