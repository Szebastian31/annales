'use strict';
// Annales' fixed data, the same as data/Settings.kt, data/Collections.kt, data/Insights.kt (Moon) and ui/Theme.kt.

/** The state-of-mind scale, coloured along the same five-stop gradient as the Android app. */
const Moods = {
  names: ['Very Unpleasant', 'Unpleasant', 'Slightly Unpleasant', 'Neutral', 'Slightly Pleasant', 'Pleasant', 'Very Pleasant'],
  /** The name shown for step [i], in the app's language. */
  label(i) { return tr(this.names[i]); },
  stops: [0x8E82CF, 0xD9908E, 0xCBC4B8, 0x9EC4A7, 0x709FD8],
  /** Colour at t in 0..1, as an RGB int. */
  at(t) {
    const g = this.stops;
    const x = clamp(t, 0, 1) * (g.length - 1);
    const i = Math.min(g.length - 2, Math.trunc(x));
    const f = x - i, a = g[i], b = g[i + 1];
    const ch = (v, s) => (v >> s) & 255;
    const mix = s => Math.trunc(ch(a, s) + (ch(b, s) - ch(a, s)) * f);
    return (mix(16) << 16) | (mix(8) << 8) | mix(0);
  },
  color(mood) { return this.at(mood / 6); },
  css(mood) { return rgb(this.color(mood)); },
  /** The step nearest a slider position 0..100. */
  levelOf: v => clamp(Math.round(v / 100 * 6), 0, 6),
  /** The mood colour mixed with ink (55 / 45), so the name reads on any paper. */
  readable(mood, ink) {
    const m = this.color(mood);
    const ch = (c, s) => (c >> s) & 255;
    const mix = s => Math.trunc(ch(m, s) * .55 + ch(ink, s) * .45);
    return (mix(16) << 16) | (mix(8) << 8) | mix(0);
  },
};

/** The postmark inks. Official is what the post office itself uses: black. */
const INKS = {
  OFFICIAL: { name: 'OFFICIAL', label: tr('Official'), rgb: 0x26252A },
  POST_RED: { name: 'POST_RED', label: tr('Post red'), rgb: 0xB8322F },
  CARMINE: { name: 'CARMINE', label: tr('Carmine'), rgb: 0x9B2F35 },
};
const inkOf = name => INKS[name] || null;

/** The tags you can put on a day, as in Apple's State of Mind. */
const Tags = {
  factors: ['Community', 'Current events', 'Dating', 'Education', 'Family', 'Fitness', 'Friends', 'Health', 'Hobbies',
    'Identity', 'Money', 'Partner', 'Self-care', 'Spirituality', 'Tasks', 'Travel', 'Weather', 'Work'],
  neg: ['Angry', 'Anxious', 'Ashamed', 'Disappointed', 'Discouraged', 'Drained', 'Frustrated', 'Guilty', 'Hopeless',
    'Irritated', 'Jealous', 'Lonely', 'Overwhelmed', 'Sad', 'Scared', 'Stressed', 'Worried'],
  mid: ['Calm', 'Content', 'Indifferent', 'Peaceful', 'Satisfied', 'Thoughtful', 'Tired', 'Drained', 'Hopeful', 'Relieved'],
  pos: ['Amazed', 'Amused', 'Brave', 'Calm', 'Confident', 'Content', 'Excited', 'Grateful', 'Happy', 'Hopeful',
    'Joyful', 'Passionate', 'Peaceful', 'Proud', 'Relieved', 'Satisfied', 'Surprised'],
  /** The feelings that fit a mood: unpleasant ones below Neutral, pleasant ones above. */
  feelsFor(mood) { return mood <= 2 ? this.neg : mood === 3 ? this.mid : this.pos; },
};

const COLL_TYPES = {
  TRIP: { name: 'TRIP', label: tr('Trip'), plural: tr('Trips'), glyph: '✈', about: tr("Dates and a place. Stamps get that city's postmark.") },
  EVENT: { name: 'EVENT', label: tr('Event'), plural: tr('Events'), glyph: '✦', about: tr('A day or a few: a wedding, a launch, a concert.') },
  ANNIV: { name: 'ANNIV', label: tr('Anniversary'), plural: tr('Anniversaries'), glyph: '↻', about: tr('One date, every year. Grows over time.') },
  THEME: { name: 'THEME', label: tr('Theme'), plural: tr('Themes'), glyph: '❖', about: tr('Stamps you pick by hand, like Coffee.') },
};

/** What a post office prints round its date stamp. Unknown places get their name in capitals. */
const Places = {
  known: {
    'Gliwice': { mark: 'GLIWICE 1', bottom: '44-100', region: tr('Silesia'), post: 'Poczta Polska' },
    'Kraków': { mark: 'KRAKÓW 1', bottom: 'POCZTA POLSKA', region: tr('Lesser Poland'), post: 'Poczta Polska' },
    'Gdańsk': { mark: 'GDAŃSK 1', bottom: 'POCZTA POLSKA', region: tr('Pomerania'), post: 'Poczta Polska' },
    'Lisboa': { mark: 'LISBOA', bottom: 'CTT', region: tr('Lisbon'), post: 'CTT Correios' },
    'Roma': { mark: 'ROMA', bottom: 'POSTE ITALIANE', region: tr('Lazio'), post: 'Poste Italiane' },
    'Paris': { mark: 'PARIS', bottom: 'LA POSTE', region: tr('Île-de-France'), post: 'La Poste' },
    'Kyoto': { mark: 'KYOTO', bottom: 'JAPAN POST', region: tr('Kansai'), post: 'Japan Post' },
    'Reykjavík': { mark: 'REYKJAVÍK', bottom: 'PÓSTURINN', region: tr('Iceland'), post: 'Pósturinn' },
  },
  alias: { Lisbon: 'Lisboa', Rome: 'Roma', Krakow: 'Kraków', Gdansk: 'Gdańsk', Reykjavik: 'Reykjavík' },
  suggestions: ['Kraków', 'Gdańsk', 'Roma', 'Paris', 'Kyoto', 'Reykjavík'],
  of(name) {
    const n = name.trim();
    const key = this.alias[n] || Object.keys(this.known).find(k => k.toLowerCase() === n.toLowerCase());
    return key ? this.known[key] : { mark: n.toUpperCase(), bottom: '', region: n, post: tr('the local post office') };
  },
};

/** The six looks. [line] is ARGB; the rest are RGB. */
const LOOKS = {
  KRAFT: { label: tr('Kraft'), about: tr('brown envelope'), bg: 0xCDAE84, surface: 0xECDCBF, page: 0xDCC39C, ink: 0x3B2A1A, muted: 0x4F3A26, line: 0x2E462D14, acc: 0x8F3B22, accSoft: 0xE3C9A6, acc2: 0x3D5A40 },
  AIRMAIL: { label: tr('Airmail'), about: tr('par avion'), bg: 0xF3F0E8, surface: 0xFFFDF8, page: 0xFAF6EE, ink: 0x1F2A44, muted: 0x4A5166, line: 0xFFE2DDD2, acc: 0xC8323C, accSoft: 0xF6DCDC, acc2: 0x23498A },
  GALLERY: { label: tr('Gallery'), about: tr('white wall'), bg: 0xFFFFFF, surface: 0xF5F5F3, page: 0xF1F1EF, ink: 0x111111, muted: 0x555555, line: 0xFFE8E8E8, acc: 0xE0482D, accSoft: 0xFBE3DD, acc2: 0x111111 },
  HERBARIUM: { label: tr('Herbarium'), about: tr('pressed sage'), bg: 0xDDE2CF, surface: 0xF4F3E8, page: 0xEBE9D8, ink: 0x2C3326, muted: 0x454D3C, line: 0xFFCCD1BC, acc: 0x667A35, accSoft: 0xE1E6C9, acc2: 0x8A5A6B },
  RIVIERA: { label: tr('Riviera'), about: tr('sea and coral'), bg: 0xD9EAEE, surface: 0xFDFAF3, page: 0xF7F0E3, ink: 0x1D3945, muted: 0x3E5962, line: 0xFFCADDE1, acc: 0xE0694A, accSoft: 0xFADFD5, acc2: 0x2F7A8A },
  INK: { label: tr('Ink'), about: tr('for evenings'), bg: 0x1D2027, surface: 0x252931, page: 0x2A2E37, ink: 0xE7E3DA, muted: 0xB4B8C1, line: 0xFF343944, acc: 0xCFA276, accSoft: 0x3A352F, acc2: 0x93A7C2, onAcc: 0x1D2027, dark: true },
};

/** The look's colours, for drawing on canvases. Kept in step with the CSS variables by applyLook. */
const K = { look: LOOKS.KRAFT, name: 'KRAFT' };
function applyLook(name) {
  const l = LOOKS[name] || LOOKS.KRAFT;
  K.look = l; K.name = LOOKS[name] ? name : 'KRAFT';
  const s = document.documentElement.style;
  for (const k of ['bg', 'surface', 'page', 'ink', 'muted', 'acc', 'accSoft']) s.setProperty('--' + k, rgb(l[k]));
  s.setProperty('--line', css(l.line));
  s.setProperty('--green', rgb(l.acc2));
  s.setProperty('--onAcc', rgb(l.onAcc ?? 0xFFFFFF));
  document.documentElement.classList.toggle('dark', !!l.dark);
  document.querySelector('meta[name=theme-color]')?.setAttribute('content', rgb(l.bg));
}

/** The moon for the postmark card, and Insights' full-moon nights. */
const Moon = {
  age(date) {
    const jd = D.epochDay(date) + 2440587.5;
    let a = ((jd - 2451550.1) / 29.530588853) % 1;
    if (a < 0) a += 1;
    return a;
  },
  name(date) {
    const a = this.age(date);
    if (a < .03 || a > .97) return tr('New moon');
    if (a < .22) return tr('Waxing crescent');
    if (a < .28) return tr('First quarter');
    if (a < .47) return tr('Waxing gibbous');
    if (a < .53) return tr('Full moon');
    if (a < .72) return tr('Waning gibbous');
    if (a < .78) return tr('Last quarter');
    return tr('Waning crescent');
  },
  /** How lit the moon is that night, 0 to 1. */
  lit(date) { return (1 - Math.cos(2 * Math.PI * this.age(date))) / 2; },
};
