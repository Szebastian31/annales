'use strict';
// AnnalesModel.kt, data/Journal.kt, data/Collections.kt and data/Backup.kt for the web. Everything lives in this
// browser's IndexedDB: journal.json and collections.json as the same text Android writes, the settings, and every
// photo and stamp under the same names (photos/<id>.jpg, stamps/<id>.png, originals/<id>.jpg).

const DB = {
  db: null,
  open() {
    return new Promise((ok, no) => {
      const r = indexedDB.open('annales', 1);
      r.onupgradeneeded = () => { r.result.createObjectStore('kv'); r.result.createObjectStore('files'); };
      r.onsuccess = () => { this.db = r.result; ok(); };
      r.onerror = () => no(r.error);
    });
  },
  run(store, mode, fn) {
    return new Promise((ok, no) => {
      const t = this.db.transaction(store, mode), req = fn(t.objectStore(store));
      t.oncomplete = () => ok(req ? req.result : undefined);
      t.onerror = t.onabort = () => no(t.error);
    });
  },
  get: (s, k) => DB.run(s, 'readonly', o => o.get(k)),
  set: (s, k, v) => DB.run(s, 'readwrite', o => { o.put(v, k); }),
  del: (s, k) => DB.run(s, 'readwrite', o => { o.delete(k); }),
  keys: s => DB.run(s, 'readonly', o => o.getAllKeys()),
  clear: s => DB.run(s, 'readwrite', o => { o.clear(); }),
};

/** Files are kept as bytes plus their type: older iPhones lost Blobs stored in IndexedDB. */
const Files = {
  async put(path, blob) { await DB.set('files', path, { type: blob.type, data: await blob.arrayBuffer() }); },
  async get(path) { const f = await DB.get('files', path); return f ? new Blob([f.data], { type: f.type }) : null; },
  has: async path => (await DB.get('files', path)) != null,
  del: path => DB.del('files', path),
  list: () => DB.keys('files'),
};

const photoFile = id => `photos/${id}.jpg`;
const stampFile = id => `stamps/${id}.png`;
const originalFile = id => `originals/${id}.jpg`;

const SETTINGS = {
  office: 'GLIWICE 1', postcode: '44-100', ink: 'OFFICIAL', cut: 'classic', saveToPhotos: true, reminder: true,
  look: 'KRAFT', name: '', keepOriginals: true, haptics: true, cleanedGallery: false, since: '',
};
const BOOL_SETTINGS = ['saveToPhotos', 'reminder', 'keepOriginals', 'haptics', 'cleanedGallery'];

const newDay = date => ({ date, stamps: [], mood: null, sealed: false, sealedAt: 0, office: '', factors: [], feels: [], moodV: null, line: '', ink: null, bottom: '', dirty: false, extras: {} });

function readDay(o) {
  const has = k => Object.prototype.hasOwnProperty.call(o, k) && o[k] != null;
  return {
    date: o.date,
    stamps: (o.stamps || []).map(j => ({ id: j.id, title: j.title || '', note: j.note || '', gallery: j.gallery || '', place: j.place || '' })),
    mood: has('mood') ? o.mood : null,
    sealed: !!o.sealed, sealedAt: o.sealedAt || 0, office: o.office || '',
    factors: o.factors || [], feels: o.feels || [],
    moodV: has('moodV') ? o.moodV : has('mood') ? Math.trunc(o.mood * 100 / 6) : null,
    line: o.line || '', ink: has('ink') ? o.ink : null, bottom: o.bottom || '', dirty: !!o.dirty,
    extras: o.extras || {},
  };
}
function writeDay(d) {
  const o = { date: d.date, stamps: d.stamps.map(s => ({ id: s.id, title: s.title, note: s.note, gallery: s.gallery, place: s.place })) };
  if (d.mood != null) o.mood = d.mood;
  Object.assign(o, { sealed: d.sealed, sealedAt: d.sealedAt, office: d.office, factors: d.factors, feels: d.feels });
  if (d.moodV != null) o.moodV = d.moodV;
  o.line = d.line;
  if (d.ink != null) o.ink = d.ink;
  Object.assign(o, { bottom: d.bottom, dirty: d.dirty, extras: d.extras });
  return o;
}
function readColl(o) {
  const t = D.today();
  return {
    id: o.id, type: COLL_TYPES[o.type] ? o.type : 'THEME', name: o.name || '', place: o.place || '',
    from: o.from || t, to: o.to || t, month: o.month ?? 1, day: o.day ?? 1,
    ink: o.ink ?? null, note: o.note || '', ids: o.ids || [], cover: o.cover ?? null,
  };
}
function writeColl(c) {
  const o = { id: c.id, type: c.type, name: c.name, place: c.place, from: c.from, to: c.to, month: c.month, day: c.day };
  if (c.ink != null) o.ink = c.ink;
  o.note = c.note; o.ids = c.ids;
  if (c.cover != null) o.cover = c.cover;
  return o;
}
function collCovers(c, date) {
  if (c.type === 'TRIP' || c.type === 'EVENT') return date >= c.from && date <= c.to;
  if (c.type === 'ANNIV') return D.month(date) === c.month && D.dom(date) === c.day;
  return false;
}
const collInk = c => inkOf(c.ink) || INKS.OFFICIAL;
const copy = o => JSON.parse(JSON.stringify(o));

const M = {
  days: {}, collections: [], s: { ...SETTINGS }, busy: false,
  /** Bumped whenever a stamp's picture changes, so views reload it. */
  ver: {},
  urls: {},
  onChange: () => {},

  async load() {
    await DB.open();
    const saved = await DB.get('kv', 'settings');
    this.s = { ...SETTINGS, ...(saved || {}) };
    if (!this.s.since) { this.s.since = D.today(); this.saveSettings(); }
    try {
      const j = await DB.get('kv', 'journal.json');
      this.days = {};
      if (j) JSON.parse(j).days.forEach(o => { const d = readDay(o); this.days[d.date] = d; });
    } catch (e) { this.days = {}; }
    try {
      const c = await DB.get('kv', 'collections.json');
      this.collections = c ? JSON.parse(c).collections.map(readColl) : [];
    } catch (e) { this.collections = []; }
    applyLook(this.s.look);
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
  },

  journalJson() {
    const days = Object.values(this.days).sort((a, b) => (a.date < b.date ? -1 : 1))
      .filter(d => d.stamps.length || d.mood != null || d.factors.length || d.feels.length || d.line.trim());
    return JSON.stringify({ version: 1, days: days.map(writeDay) });
  },
  collectionsJson() { return JSON.stringify({ version: 1, collections: this.collections.map(writeColl) }); },
  saveDays() { return DB.set('kv', 'journal.json', this.journalJson()); },
  saveCollections() { return DB.set('kv', 'collections.json', this.collectionsJson()); },
  saveSettings() { return DB.set('kv', 'settings', this.s); },
  set(key, value) { this.s[key] = value; this.saveSettings(); },

  day(date) { return this.days[date] || newDay(date); },
  put(d) { this.days[d.date] = d; this.saveDays(); this.onChange(); },
  /** Any change after sealing marks the day, so the Seal button offers "Save & re-seal". */
  edit(date, change, quiet = false) {
    const d = this.day(date), n = change(copy(d));
    n.dirty = d.sealed;
    this.days[date] = n; this.saveDays();
    if (!quiet) this.onChange();
  },
  get since() { return this.s.since || D.today(); },

  // ---- the state of mind, tags and the line ----
  setMoodV(date, v) {
    this.edit(date, d => {
      const lv = Moods.levelOf(v);
      const keep = d.mood == null || Tags.feelsFor(d.mood) === Tags.feelsFor(lv);
      return Object.assign(d, { mood: lv, moodV: v, feels: keep ? d.feels : [] });
    });
  },
  toggleFactor(date, f) { this.edit(date, d => Object.assign(d, { factors: d.factors.includes(f) ? d.factors.filter(x => x !== f) : [...d.factors, f] })); },
  toggleFeel(date, f) { this.edit(date, d => Object.assign(d, { feels: d.feels.includes(f) ? d.feels.filter(x => x !== f) : [...d.feels, f] })); },
  setLine(date, text) { this.edit(date, d => Object.assign(d, { line: text.slice(0, 200) }), true); },
  setDayInk(date, ink) { this.edit(date, d => Object.assign(d, { ink })); },
  /** This day's ink, falling back to a trip's ink, then the default in Profile. */
  inkOn(date) { return inkOf(this.day(date).ink) || this.postmarkFor(date)[2]; },

  // ---- stamps ----
  newId() { return (crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx'.replace(/x/g, () => (Math.random() * 16 | 0).toString(16))).slice(0, 12); },

  async stampUrl(id) {
    const v = this.ver[id] || 0, c = this.urls[id];
    if (c && c.v === v) return c.url;
    const b = await Files.get(stampFile(id));
    if (!b) return null;
    if (c) URL.revokeObjectURL(c.url);
    const url = URL.createObjectURL(b);
    this.urls[id] = { v, url };
    return url;
  },
  bump(id) { this.ver[id] = (this.ver[id] || 0) + 1; },

  /** Saves the cropped photo and its perforated draft; [replace] swaps an existing stamp's picture instead of adding one. */
  async addStamp(date, cropped, replace = null, original = null, place = null) {
    const d = this.day(date);
    if (!replace && d.stamps.length >= 3) return;
    let s = replace ? { ...replace } : { id: this.newId(), title: '', note: '', gallery: '', place: '' };
    if (place != null) s.place = place;
    await this.work(async () => {
      await Files.put(photoFile(s.id), await Photos.jpeg(cropped));
      if (original && this.s.keepOriginals) await Files.put(originalFile(s.id), await Photos.jpeg(original));
      else await Files.del(originalFile(s.id));
      const draft = SR.render(cropped, this.s.cut, null, javaHash(s.id));
      await Files.put(stampFile(s.id), await Photos.png(draft));
      drop(draft);
    });
    this.bump(s.id);
    this.edit(date, x => Object.assign(x, { stamps: replace ? x.stamps.map(o => (o.id === s.id ? s : o)) : [...x.stamps, s] }));
  },

  /** A drawn stamp of the day's sky instead of a photo: the weather you pick, that night's moon, hills in the mood colour. */
  async addSky(date, wx, replace = null, place = null) {
    const d = this.day(date);
    const mood = d.mood != null ? Moods.color(d.mood) : null;
    const art = Sky.draw(date, wx, mood, (D.epochDay(date) * 31 + d.stamps.length) | 0);
    await this.addStamp(date, art, replace, null, place);
    drop(art);
  },

  /** Moves [s] to [to] (0 = first, the one the Journal and Collections show for the day). */
  moveStamp(date, s, to) {
    this.edit(date, d => {
      const rest = d.stamps.filter(x => x.id !== s.id), i = clamp(to, 0, rest.length);
      return Object.assign(d, { stamps: [...rest.slice(0, i), s, ...rest.slice(i)] });
    });
  },
  setPlace(date, s, town) { this.edit(date, d => Object.assign(d, { stamps: d.stamps.map(x => (x.id === s.id ? { ...x, place: town.trim() } : x)) })); },
  /** The town a stamp's postmark names: its own place, or the day's office. */
  placeOf(date, s) { return s.place.trim() || prettyPlace(this.day(date).office || this.postmarkFor(date)[0]); },
  placeSuggestions(date) {
    const out = [];
    this.day(date).stamps.forEach(s => s.place && out.push(s.place));
    const trip = this.tripOn(date); if (trip) out.push(trip.place);
    out.push(prettyPlace(this.s.office));
    Object.values(this.days).sort((a, b) => (a.date < b.date ? 1 : -1)).slice(0, 30).forEach(d => d.stamps.forEach(s => s.place && out.push(s.place)));
    return [...new Set(out)].slice(0, 6);
  },
  hasOriginal: s => Files.has(originalFile(s.id)),
  async original(s) { const b = await Files.get(originalFile(s.id)); return b ? Photos.canvasOf(b) : null; },
  async shapeOf(s) {
    const b = await Files.get(photoFile(s.id));
    if (!b) return 'portrait';
    const img = await Photos.image(b);
    return SR.shapeOf(img.naturalWidth, img.naturalHeight);
  },
  removeStamp(date, s) {
    this.edit(date, d => Object.assign(d, { stamps: d.stamps.filter(x => x.id !== s.id) }));
    if (this.collections.some(c => c.ids.includes(s.id) || c.cover === s.id)) {
      this.collections = this.collections.map(c => ({ ...c, ids: c.ids.filter(i => i !== s.id), cover: c.cover === s.id ? null : c.cover }));
      this.saveCollections();
    }
    [photoFile, stampFile, originalFile].forEach(f => Files.del(f(s.id)));
  },

  /**
   * Presses the postmark on every stamp of the day. Re-sealing an edited day keeps the postmark it was first given
   * (office, date and hour); only the ink and the stamps can change. Returns the clean stamps for Photos.
   */
  async seal(date) {
    const d = this.day(date);
    if (!d.stamps.length || d.mood == null || (d.sealed && !d.dirty)) return null;
    const first = !d.sealed;
    const hour = first ? new Date().getHours() : D.hourOf(d.sealedAt);
    const [pmMark, pmBottom] = this.postmarkFor(date);
    const mark = first ? pmMark : (d.office || pmMark);
    const bottom = first ? pmBottom : (d.bottom || pmBottom);
    const ink = this.inkOn(date);
    const clean = [];
    const ok = await this.work(async () => {
      for (const s of d.stamps) {
        const b = await Files.get(photoFile(s.id));
        if (!b) continue;
        const photo = await Photos.canvasOf(b);
        const p = s.place ? Places.of(s.place) : { mark, bottom };
        const seal = { date, hour, office: p.mark, postcode: p.bottom, ink: ink.rgb, seed: javaHash(s.id) & 0xFFFF };
        const out = SR.render(photo, this.s.cut, seal, javaHash(s.id));
        await Files.put(stampFile(s.id), await Photos.png(out));
        drop(out);
        if (this.s.saveToPhotos) {
          const c = SR.clean(photo, this.s.cut, javaHash(s.id));
          clean.push(new File([await Photos.png(c)], `annales-${date}-${s.id.slice(0, 4)}.png`, { type: 'image/png' }));
          drop(c);
        }
        drop(photo);
        this.bump(s.id);
      }
    });
    if (!ok) return null;
    const now = this.day(date);
    this.put(Object.assign(copy(now), { sealed: true, dirty: false, sealedAt: first ? Date.now() : now.sealedAt, office: mark, bottom, ink: ink.name }));
    return { first, clean, n: d.stamps.length };
  },

  /** A new perforation re-cuts the drafts; sealed stamps keep the edge they were sealed with. */
  async changeCut(v) {
    if (v === this.s.cut) return;
    this.set('cut', v);
    const drafts = Object.values(this.days).filter(d => !d.sealed).flatMap(d => d.stamps);
    await this.work(async () => {
      for (const s of drafts) {
        const b = await Files.get(photoFile(s.id));
        if (!b) continue;
        const photo = await Photos.canvasOf(b), out = SR.render(photo, v, null, javaHash(s.id));
        await Files.put(stampFile(s.id), await Photos.png(out));
        drop(out); drop(photo); this.bump(s.id);
      }
    });
    this.onChange();
  },

  /** Heavy drawing, with the busy flag up; false (and a message) if it failed. */
  async work(block) {
    this.busy = true; this.onChange();
    try { await block(); return true; } catch (e) { toast(`Something went wrong: ${e.message || e}`); return false; } finally { this.busy = false; this.onChange(); }
  },

  // ---- the weather, the Consensus Weather way ----
  /** An open day refreshes its weather each time you look at it; a sealed day keeps what it had. */
  async fillIn(date) {
    const d = this.day(date);
    if (d.sealed && Object.keys(d.extras).length) return;
    const trip = this.tripOn(date);
    const place = (trip && trip.place.trim()) || prettyPlace(this.s.office);
    let got = null;
    try { got = await Weather.day(place, date); } catch (e) { got = null; }
    if (!got) return;
    const now = this.day(date);
    if (now.sealed && Object.keys(now.extras).length) return;
    const merged = { ...now.extras, ...got };
    if (JSON.stringify(merged) === JSON.stringify(now.extras)) return;
    this.put(Object.assign(copy(now), { extras: merged })); // not an edit: no "re-seal" prompt
  },

  // ---- collections ----
  tripOn(date) { return this.collections.find(c => c.type === 'TRIP' && collCovers(c, date)) || null; },
  /** Office over the top, text underneath, and ink, for sealing [date]. */
  postmarkFor(date) {
    const trip = this.tripOn(date);
    if (!trip) return [this.s.office, this.s.postcode, inkOf(this.s.ink) || INKS.OFFICIAL];
    const p = Places.of(trip.place);
    return [p.mark, p.bottom, collInk(trip)];
  },
  /** A collection's days in order, each with the stamps it holds. Trips, events and anniversaries hold sealed days only. */
  entries(c) {
    const sorted = () => Object.values(this.days).sort((a, b) => (a.date < b.date ? -1 : 1));
    if (c.type === 'THEME') return sorted().map(d => [d, d.stamps.filter(s => c.ids.includes(s.id))]).filter(e => e[1].length);
    if (c.type === 'ANNIV') return sorted().filter(d => collCovers(c, d.date)).map(d => [d, d.sealed ? d.stamps : []]);
    const out = [];
    for (let date = c.from, i = 0; date <= c.to && i < 366; date = D.plus(date, 1), i++) { const d = this.day(date); out.push([d, d.sealed ? d.stamps : []]); }
    return out;
  },
  count(c) { return this.entries(c).reduce((s, e) => s + e[1].length, 0); },
  /** The chosen cover if it still belongs, else the first stamp. */
  cover(c) {
    const all = this.entries(c).flatMap(([d, ss]) => ss.map(s => [d, s]));
    return all.find(e => e[1].id === c.cover) || all[0] || null;
  },
  saveCollection(c) {
    const saved = c.id === 0 ? { ...c, id: Date.now() } : c;
    this.collections = [...this.collections.filter(x => x.id !== saved.id), saved];
    this.saveCollections(); this.onChange();
    return saved;
  },
  deleteCollection(c) { this.collections = this.collections.filter(x => x.id !== c.id); this.saveCollections(); this.onChange(); },
  toggleInTheme(t, id) {
    const ids = t.ids.includes(id) ? t.ids.filter(x => x !== id) : [...t.ids, id];
    this.saveCollection({ ...t, ids, cover: t.cover === id && !ids.includes(id) ? null : t.cover });
  },
  setCover(c, id) { this.saveCollection({ ...c, cover: id }); },
  /** The same date in earlier years, newest first, for "On this day". Only days with stamps. */
  onThisDay(date) {
    return Object.values(this.days).filter(d => D.year(d.date) < D.year(date) && d.date.slice(5) === date.slice(5) && d.stamps.length)
      .sort((a, b) => (a.date < b.date ? 1 : -1));
  },
  hasShared(prefix) { return Object.values(this.days).some(d => Object.keys(d.extras).some(k => k.startsWith(prefix))); },
};

/**
 * Backup files in Kalendae's format, the same as Android's: { app, schema, exported } and either the state in the clear,
 * or locked: AES-256-GCM with a key from PBKDF2-SHA256 (250 000 rounds, 16-byte salt, 12-byte IV), all base64.
 * The state holds journal.json, collections.json, the settings, and every photo and stamp as base64.
 */
const Backup = {
  DIRS: ['photos', 'stamps', 'originals'],
  b64: bytes => { let s = ''; const b = new Uint8Array(bytes); for (let i = 0; i < b.length; i += 0x8000) s += String.fromCharCode.apply(null, b.subarray(i, i + 0x8000)); return btoa(s); },
  unb64: s => { const bin = atob(s.replace(/\s+/g, '')); const out = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i); return out; },
  async key(password, salt, usage) {
    const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveKey']);
    return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: 250000 }, base, { name: 'AES-GCM', length: 256 }, false, [usage]);
  },

  /** The backup as a Blob. Unlocked ones are built from parts, so a big journal never has to fit in one string. */
  async write(password) {
    const parts = [];
    const q = JSON.stringify;
    parts.push(`"journal.json":${q(M.journalJson())}`);
    if (M.collections.length || (await DB.get('kv', 'collections.json'))) parts.push(`,"collections.json":${q(M.collectionsJson())}`);
    parts.push(`,"settings":${q(Object.fromEntries(Object.entries(M.s).map(([k, v]) => [k, String(v)])))}`, ',"files":{');
    let first = true;
    for (const path of await Files.list()) {
      if (!this.DIRS.some(d => path.startsWith(d + '/'))) continue;
      const f = await DB.get('files', path);
      if (!f) continue;
      parts.push(`${first ? '' : ','}${q(path)}:"`, this.b64(f.data), '"');
      first = false;
    }
    parts.push('}');
    const head = `{"app":"annales","schema":1,"exported":${q(new Date().toISOString())}`;
    if (!password) return new Blob([head, ',"state":{', ...parts, '}}'], { type: 'application/json' });
    const plain = new Uint8Array(await new Blob(['{', ...parts, '}']).arrayBuffer());
    const salt = crypto.getRandomValues(new Uint8Array(16)), iv = crypto.getRandomValues(new Uint8Array(12));
    const data = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, tagLength: 128 }, await this.key(password, salt, 'encrypt'), plain);
    return new Blob([`${head},"locked":true,"salt":"${this.b64(salt)}","iv":"${this.b64(iv)}","data":"${this.b64(data)}"}`], { type: 'application/json' });
  },

  /** The parsed file, or throws "not". */
  read(text) {
    let o;
    try { o = JSON.parse(text); } catch (e) { throw new Error('not'); }
    if (!o || o.app !== 'annales') throw new Error('not');
    return o;
  },

  /** Replaces everything here with the backup. Throws "wrong" or "not". */
  async restore(o, password) {
    let state;
    if (!o.locked) state = o.state;
    else {
      if (!password) throw new Error('wrong');
      try {
        const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: this.unb64(o.iv), tagLength: 128 }, await this.key(password, this.unb64(o.salt), 'decrypt'), this.unb64(o.data));
        state = JSON.parse(new TextDecoder().decode(plain));
      } catch (e) { throw new Error('wrong'); }
    }
    if (!state) throw new Error('not');
    await DB.clear('files');
    for (const [k, v] of Object.entries(state.files || {})) {
      if (!this.DIRS.some(d => k.startsWith(d + '/')) || k.includes('..')) continue;
      await DB.set('files', k, { type: k.endsWith('.png') ? 'image/png' : 'image/jpeg', data: this.unb64(v).buffer });
    }
    for (const f of ['journal.json', 'collections.json']) {
      if (state[f] != null) await DB.set('kv', f, state[f]); else await DB.del('kv', f);
    }
    if (state.settings) {
      const s = {};
      for (const [k, v] of Object.entries(state.settings)) s[k] = BOOL_SETTINGS.includes(k) ? String(v) === 'true' : String(v);
      await DB.set('kv', 'settings', s);
    }
    Object.values(M.urls).forEach(u => URL.revokeObjectURL(u.url));
    M.urls = {}; M.ver = {};
    await M.load();
  },
};
