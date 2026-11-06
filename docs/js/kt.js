'use strict';
// The bits of Kotlin and java.time the Android app relies on, so the web version cuts, seeds and dates stamps the same way.

/** Kotlin's Random(seed): XorWow, exactly as kotlin.random.XorWowRandom. A deckle edge torn on Android tears the same here. */
class KRandom {
  constructor(seed) {
    seed |= 0;
    const seed2 = seed >> 31;
    this.x = seed; this.y = seed2; this.z = 0; this.w = 0; this.v = ~seed;
    this.addend = (seed << 10) ^ (seed2 >>> 4);
    for (let i = 0; i < 64; i++) this.next();
  }
  next() {
    let t = this.x;
    t ^= t >>> 2;
    this.x = this.y; this.y = this.z; this.z = this.w;
    const v0 = this.v;
    this.w = v0;
    t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4);
    this.v = t | 0;
    this.addend = (this.addend + 362437) | 0;
    return (t + this.addend) | 0;
  }
  bits(n) { return (this.next() >>> (32 - n)) & ((-n) >> 31); }
  /** nextInt() with no bound, or nextInt(until). */
  nextInt(until) {
    if (until === undefined) return this.next();
    if ((until & -until) === until) return this.bits(31 - Math.clz32(until));
    let b, v;
    do { b = this.next() >>> 1; v = b % until; } while (((b - v + (until - 1)) | 0) < 0);
    return v;
  }
  nextFloat() { return this.bits(24) / 16777216; }
  nextBoolean() { return this.bits(1) !== 0; }
}

/** Java's String.hashCode, which seeds each stamp's edge and postmark. */
function javaHash(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const pad2 = n => String(n).padStart(2, '0');

/** Dates as "2026-11-04" strings, like java.time.LocalDate.toString(), worked out in UTC so time zones never shift a day. */
const D = {
  of: (y, m, d) => `${String(y).padStart(4, '0')}-${pad2(m)}-${pad2(d)}`,
  today() { const n = new Date(); return D.of(n.getFullYear(), n.getMonth() + 1, n.getDate()); },
  parts(s) { const [y, m, d] = s.split('-').map(Number); return { y, m, d }; },
  year: s => +s.slice(0, 4),
  month: s => +s.slice(5, 7),
  dom: s => +s.slice(8, 10),
  epochDay(s) { const { y, m, d } = D.parts(s); return Math.round(Date.UTC(y, m - 1, d) / 864e5); },
  ofEpochDay(n) { const t = new Date(n * 864e5); return D.of(t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate()); },
  plus: (s, n) => D.ofEpochDay(D.epochDay(s) + n),
  between: (a, b) => D.epochDay(b) - D.epochDay(a),
  /** 1 = Monday … 7 = Sunday. */
  dow(s) { return ((new Date(D.epochDay(s) * 864e5).getUTCDay() + 6) % 7) + 1; },
  doy(s) { return D.epochDay(s) - D.epochDay(D.of(D.year(s), 1, 1)) + 1; },
  leap: y => (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0,
  lengthOfYear: y => (D.leap(y) ? 366 : 365),
  lengthOfMonth: (y, m) => new Date(Date.UTC(y, m, 0)).getUTCDate(),
  /** "2026-11" */
  ym: s => s.slice(0, 7),
  ymPlus(ym, n) { let y = +ym.slice(0, 4), m = +ym.slice(5, 7) + n; while (m < 1) { m += 12; y--; } while (m > 12) { m -= 12; y++; } return `${y}-${pad2(m)}`; },
  /** Local hour and "d MMM, HH:mm" for a sealedAt timestamp. */
  hourOf: ms => new Date(ms).getHours(),
};

const DAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/** A small DateTimeFormatter: EEEE, EEE, d, dd, MMMM, LLLL, MMM, MM, yyyy, yy, and 'quoted text'. */
function fmt(s, pattern) {
  if (LANG !== 'en' && FMT_INTL[pattern]) return FMT_INTL[pattern](s);
  const { y, m, d } = D.parts(s);
  const day = DAY_NAMES[D.dow(s) - 1];
  return pattern.replace(/EEEE|EEE|MMMM|LLLL|MMM|MM|yyyy|yy|dd|d|'[^']*'/g, t => {
    switch (t) {
      case 'EEEE': return day;
      case 'EEE': return day.slice(0, 3);
      case 'MMMM': case 'LLLL': return MONTH_NAMES[m - 1];
      case 'MMM': return MONTH_NAMES[m - 1].slice(0, 3);
      case 'MM': return pad2(m);
      case 'yyyy': return String(y);
      case 'yy': return pad2(y % 100);
      case 'dd': return pad2(d);
      case 'd': return String(d);
      default: return t.slice(1, -1);
    }
  });
}

/**
 * The same dates in another language, in its own order ("5 lis 2026", "2026年11月5日"). The postmark's dd.MM.yy has
 * no words, so it stays as it is everywhere.
 */
const FMT_INTL = {
  'd MMM yyyy': s => ldate(s, { day: 'numeric', month: 'short', year: 'numeric' }),
  'd MMM': s => ldate(s, { day: 'numeric', month: 'short' }),
  'EEE d MMM': s => ldate(s, { weekday: 'short', day: 'numeric', month: 'short' }),
  'EEE d MMM yyyy': s => ldate(s, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }),
  'MMM yyyy': s => ldate(s, { month: 'short', year: 'numeric' }),
  'EEE d': s => ldate(s, { weekday: 'short', day: 'numeric' }),
  'd MMMM': s => ldate(s, { day: 'numeric', month: 'long' }),
  'EEEE · d MMM yyyy': s => `${cap(ldate(s, { weekday: 'long' }))} · ${ldate(s, { day: 'numeric', month: 'short', year: 'numeric' })}`,
  'EEEE d MMMM yyyy': s => cap(ldate(s, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })),
};
/** Month and weekday names, for the calendar, the pages and anniversaries. */
const monthName = m => (LANG === 'en' ? MONTH_NAMES[m - 1] : cap(lmonth(m)));
const dayName = dow => (LANG === 'en' ? DAY_NAMES[dow - 1] : cap(lweekday(dow)));

/** "4 Nov, 21:14" for a timestamp. */
function fmtStamp(ms) {
  const t = new Date(ms);
  if (LANG !== 'en') return t.toLocaleString(LOCALE, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23', calendar: 'gregory', numberingSystem: 'latn' });
  return `${t.getDate()} ${MONTH_NAMES[t.getMonth()].slice(0, 3)}, ${pad2(t.getHours())}:${pad2(t.getMinutes())}`;
}

/** ARGB int → CSS colour. */
function css(argb) {
  const a = ((argb >>> 24) & 255) / 255, r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
  return a >= 1 ? `rgb(${r},${g},${b})` : `rgba(${r},${g},${b},${+a.toFixed(3)})`;
}
/** RGB (no alpha) int → CSS colour, optionally with an alpha. */
const rgb = (c, a = 1) => (a >= 1 ? `rgb(${(c >> 16) & 255},${(c >> 8) & 255},${c & 255})` : `rgba(${(c >> 16) & 255},${(c >> 8) & 255},${c & 255},${a})`);

/** "GLIWICE 1" → "Gliwice 1", "KRAKÓW 1" → "Kraków 1". */
const prettyOffice = mark => mark.toLowerCase().split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
/** "GLIWICE 1" → "Gliwice", the town to ask the weather for. */
const prettyPlace = mark => { const w = mark.trim().split(' ')[0].toLowerCase(); return w.charAt(0).toUpperCase() + w.slice(1); };
