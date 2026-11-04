'use strict';
// data/Siblings.kt (the weather part) and data/Insights.kt for the web.

/** Weather, worked out the way Consensus Weather does it: thirteen Open-Meteo models, temperatures averaged, the condition most models vote for. */
const Weather = {
  MODELS: ['ecmwf_ifs025', 'icon_seamless', 'gfs_seamless', 'metno_seamless', 'meteofrance_seamless', 'jma_seamless',
    'gem_seamless', 'ukmo_seamless', 'knmi_seamless', 'dmi_seamless', 'kma_seamless', 'cma_grapes_global', 'bom_access_global'],
  coords: { Gliwice: [50.2945, 18.6714] },

  async locate(place) {
    const w = place.trim().split(' ')[0];
    const name = w.charAt(0).toUpperCase() + w.slice(1);
    if (this.coords[name]) return this.coords[name];
    const r = await (await fetch(`https://geocoding-api.open-meteo.com/v1/search?count=1&name=${encodeURIComponent(name)}`)).json();
    const hit = r.results && r.results[0];
    if (!hit) return null;
    return (this.coords[name] = [hit.latitude, hit.longitude]);
  },

  /** The consensus for one day at [place]: wx.code, wx.hi, wx.lo, wx.models, wx.agree. Open-Meteo keeps about three months back. */
  async day(place, date) {
    const back = D.between(date, D.today());
    if (back > 90 || back < -6) return null;
    const at = await this.locate(place);
    if (!at) return null;
    const r = await (await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${at[0]}&longitude=${at[1]}&timezone=auto` +
      `&start_date=${date}&end_date=${date}&daily=weather_code,temperature_2m_max,temperature_2m_min&models=${this.MODELS.join(',')}`)).json();
    const d = r.daily;
    if (!d) return null;
    const vals = key => this.MODELS.map(m => d[`${key}_${m}`]).filter(a => a && a.length && a[0] != null).map(a => a[0]);
    const hi = vals('temperature_2m_max'), lo = vals('temperature_2m_min'), codes = vals('weather_code').map(Math.trunc);
    if (!hi.length || !codes.length) return null;
    const count = {};
    codes.forEach(c => { count[c] = (count[c] || 0) + 1; });
    // the condition most models vote for; on a tie, the one that came first, as groupingBy + maxBy does
    let code = codes[0];
    for (const c of codes) if (count[c] > count[code]) code = c;
    const avg = a => a.reduce((s, x) => s + x, 0) / a.length;
    const spread = Math.max(...hi) - Math.min(...hi);
    return {
      'wx.code': String(code), 'wx.hi': String(Math.round(avg(hi))), 'wx.lo': String(Math.round(avg(lo))),
      'wx.models': String(hi.length), 'wx.agree': spread <= 2.5 ? 'agree' : spread <= 5 ? 'roughly' : 'diverge',
    };
  },

  /** WMO code → a short name, and the sky stamp it matches. */
  describe(code) {
    if (code === 0) return ['Clear', SKY.CLEAR];
    if (code === 1) return ['Mostly clear', SKY.CLEAR];
    if (code === 2) return ['Partly cloudy', SKY.PARTLY];
    if (code === 3) return ['Overcast', SKY.CLOUDY];
    if (code === 45 || code === 48) return ['Fog', SKY.FOG];
    if (code >= 51 && code <= 57) return ['Drizzle', SKY.RAIN];
    if (code >= 61 && code <= 67) return ['Rain', SKY.RAIN];
    if (code >= 71 && code <= 77) return ['Snow', SKY.CLOUDY];
    if (code >= 80 && code <= 82) return ['Showers', SKY.RAIN];
    if (code === 85 || code === 86) return ['Snow showers', SKY.CLOUDY];
    if (code >= 95 && code <= 99) return ['Thunderstorm', SKY.RAIN];
    return ['Mixed', SKY.PARTLY];
  },
};

/** "Where am I now", as a town name. Asked only when you tap for it; only the town is kept. */
async function hereTown() {
  const pos = await new Promise((ok, no) => navigator.geolocation.getCurrentPosition(ok, no, { maximumAge: 30 * 60e3, timeout: 10e3, enableHighAccuracy: false }));
  const { latitude, longitude } = pos.coords;
  const r = await (await fetch(`https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${latitude}&longitude=${longitude}&localityLanguage=en`)).json();
  return r.city || r.locality || r.principalSubdivision || null;
}

/** Everything the Insights tab shows, worked out from your own entries only. */
class Insights {
  constructor(days, collections, today = D.today()) {
    this.today = today;
    this.trips = collections.filter(c => c.type === 'TRIP');
    this.all = [...days].sort((a, b) => (a.date < b.date ? -1 : 1));
    this.felt = this.all.filter(d => d.mood != null);
    const sealed = this.all.filter(d => d.sealed);
    this.stamps = this.all.reduce((s, d) => s + d.stamps.length, 0);
    this.places = new Set(sealed.map(d => d.office).filter(o => o.trim())).size;
    const words = s => s.split(/\s+/).filter(w => w.trim()).length;
    this.words = this.all.reduce((s, d) => s + d.stamps.reduce((t, x) => t + words(x.title) + words(x.note), 0), 0);

    const set = new Set(sealed.map(d => d.date));
    let d = set.has(today) ? today : D.plus(today, -1), cur = 0;
    while (set.has(d)) { cur++; d = D.plus(d, -1); }
    this.streak = cur;
    let best = 0, run = 0, prev = null;
    [...set].sort().forEach(x => { run = prev != null && D.between(prev, x) === 1 ? run + 1 : 1; best = Math.max(best, run); prev = x; });
    this.longest = best;

    this.months = new Array(12).fill(0);
    this.all.filter(x => D.year(x.date) === D.year(today)).forEach(x => { this.months[D.month(x.date) - 1] += x.stamps.length; });
  }

  static avg(ds) { return ds.length ? ds.reduce((s, d) => s + d.mood, 0) / ds.length : 0; }
  static moonLight(date) { return Moon.lit(date); }
  onTrip(date) { return this.trips.some(c => collCovers(c, date)); }
  get average() { return this.felt.length ? Math.round(Insights.avg(this.felt)) : null; }

  columns(range) {
    if (range !== 'ALL') {
      const n = range === 'WEEK' ? 7 : 30, out = [];
      for (let back = n - 1; back >= 0; back--) {
        const date = D.plus(this.today, -back), dom = D.dom(date);
        const label = range === 'WEEK' ? DAY_NAMES[D.dow(date) - 1].charAt(0) : (dom % 5 === 1 ? String(dom) : '');
        const f = this.felt.find(x => x.date === date);
        out.push({ label, moods: f ? [f.mood] : [], trip: this.onTrip(date) });
      }
      return out;
    }
    const first = this.felt.length ? this.felt[0].date : this.today;
    const out = [];
    for (let w = D.plus(first, -(D.dow(first) - 1)); w <= this.today; w = D.plus(w, 7)) {
      const end = D.plus(w, 6);
      out.push({ label: `${D.dom(w)}.${D.month(w)}`, moods: this.felt.filter(x => x.date >= w && x.date <= end).map(x => x.mood), trip: false });
    }
    return out;
  }

  inRange(range) {
    if (range === 'WEEK') return this.felt.filter(x => x.date >= D.plus(this.today, -6));
    if (range === 'MONTH') return this.felt.filter(x => x.date >= D.plus(this.today, -29));
    return this.felt;
  }
  mix(range) { const a = new Array(7).fill(0); this.inRange(range).forEach(x => a[x.mood]++); return a; }

  factors(range) { return this.tally(this.inRange(range), d => d.factors); }
  feels(range) { return this.tally(this.inRange(range), d => d.feels); }
  tally(ds, of) {
    const by = new Map();
    ds.forEach(d => of(d).forEach(t => { if (!by.has(t)) by.set(t, []); by.get(t).push(d); }));
    return [...by].map(([name, days]) => {
      const spread = new Array(7).fill(0); days.forEach(d => spread[d.mood]++);
      return { name, days, avg: Insights.avg(days), spread };
    }).sort((a, b) => b.days.length - a.days.length);
  }

  link(glyph, name, a, b, la, lb) {
    const diff = Insights.avg(a) - Insights.avg(b), n = Math.min(a.length, b.length);
    const level = n < 5 ? 0 : Math.abs(diff) < .4 ? 1 : n >= 15 && Math.abs(diff) >= .9 ? 3 : 2;
    const sentence = level === 0 ? `Only ${n} day${n === 1 ? '' : 's'} so far in one group`
      : level === 1 ? 'About the same either way'
      : `About ${Math.abs(diff).toFixed(1)} steps ${diff > 0 ? 'more pleasant' : 'less pleasant'}`;
    return { glyph, name, a, b, la, lb, diff, level, sentence, label: ['Too early to tell', 'No clear difference', 'Weak hint', 'Clear hint'][level] };
  }

  links() {
    const part = f => [this.felt.filter(f), this.felt.filter(x => !f(x))];
    const [onTrip, home] = part(x => this.onTrip(x.date));
    const [weekend, weekday] = part(x => D.dow(x.date) >= 6);
    const [full, other] = part(x => Moon.lit(x.date) > .9);
    const [people, noPeople] = part(x => ['Friends', 'Family', 'Partner'].some(t => x.factors.includes(t)));
    const [stamped, bare] = part(x => x.stamps.length >= 2);
    const out = [
      this.link('✈', 'Trips', onTrip, home, 'On a trip', 'At home'),
      this.link('♡', 'Your people', people, noPeople, 'Friends, family, partner', 'Other days'),
      this.link('☕', 'Weekends', weekend, weekday, 'Sat · Sun', 'Weekdays'),
      this.link('❏', 'Busy days', stamped, bare, '2–3 stamps', '0–1 stamp'),
      this.link('☾', 'Full moon', full, other, 'Near full', 'Other nights'),
    ];
    // connections that need a sibling app's numbers: weather here, and music and money from days restored off Android
    const wx = this.felt.filter(x => x.extras['wx.code'] != null);
    const sunny = x => { const c = parseInt(x.extras['wx.code'], 10); return (Number.isNaN(c) ? 99 : c) <= 1; };
    if (wx.length) out.push(this.link('☀', 'Sunny days', wx.filter(sunny), wx.filter(x => !sunny(x)), 'Clear sky', 'Cloud, rain, fog'));
    const mus = this.felt.filter(x => Number.isInteger(parseInt(x.extras['music.minutes'], 10)));
    if (mus.length) {
      const sorted = mus.map(x => parseInt(x.extras['music.minutes'], 10)).sort((a, b) => a - b), mid = sorted[Math.trunc(sorted.length / 2)];
      out.push(this.link('♪', 'Lots of music', mus.filter(x => parseInt(x.extras['music.minutes'], 10) > mid),
        mus.filter(x => !(parseInt(x.extras['music.minutes'], 10) > mid)), `Over ${mid} min`, `${mid} min or less`));
    }
    const money = this.felt.filter(x => x.extras['money.spentN'] != null && x.extras['money.safeN'] != null);
    if (money.length) {
      const ok = x => (parseFloat(x.extras['money.spentN']) || 0) <= (parseFloat(x.extras['money.safeN']) || 0);
      out.push(this.link('¤', 'Within budget', money.filter(ok), money.filter(x => !ok(x)), 'Spent ≤ safe', 'Spent more'));
    }
    return out.sort((a, b) => b.level - a.level || Math.abs(b.diff) - Math.abs(a.diff));
  }

  /** A gentle note from your own entries: never a lecture, just what your good days tend to have. */
  note() {
    const recent = this.felt.slice(-4);
    const low = recent.filter(x => x.mood <= 2).length >= 3;
    const count = {};
    this.felt.filter(x => x.mood >= 5).forEach(x => x.factors.forEach(f => { count[f] = (count[f] || 0) + 1; }));
    const top = Object.entries(count).sort((a, b) => b[1] - a[1]).slice(0, 2).map(e => e[0]);
    if (low && top.length) return `A few heavier days lately. On your more pleasant days you often tagged ${top.join(' and ')}.`;
    if (low) return 'A few heavier days lately. Be gentle with yourself.';
    if (top.length) return `Your most pleasant days often involve ${top.join(' and ')}.`;
    return null;
  }
}
