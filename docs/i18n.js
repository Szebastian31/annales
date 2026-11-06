'use strict';
// Languages, the same set as Consensus Weather, Augur, Kalendae, Caelum and Hora. Annales opens in your phone's
// language and falls back to English; you can change it in Profile (the app reloads in the new language).
// Texts are written in English in the code: tr('Seal today'). Each file in i18n/ maps them to one language, so a line
// that isn't translated yet simply shows the English. Counts use tn(n, '{n} stamp', '{n} stamps'), and a language file
// gives that one { one, few, many, other } as its grammar needs.
// What's stored stays English (moods, feelings and life factors are saved by their English name, so a backup reads
// the same on Android and in any language); only what's shown is translated. Postmarks are never translated.
const LANGS = {
  en: ['English', 'en-GB'], pl: ['Polski', 'pl-PL'], de: ['Deutsch', 'de-DE'], uk: ['Українська', 'uk-UA'],
  es: ['Español', 'es-ES'], fr: ['Français', 'fr-FR'], ru: ['Русский', 'ru-RU'], zh: ['中文', 'zh-CN'],
  ja: ['日本語', 'ja-JP'], pt: ['Português', 'pt-BR'], nl: ['Nederlands', 'nl-NL'], it: ['Italiano', 'it-IT'],
  hi: ['हिन्दी', 'hi-IN'], ar: ['العربية', 'ar', 'rtl'], el: ['Ελληνικά', 'el-GR'], cs: ['Čeština', 'cs-CZ'],
  sv: ['Svenska', 'sv-SE'], ko: ['한국어', 'ko-KR'], id: ['Bahasa Indonesia', 'id-ID'], bn: ['বাংলা', 'bn-BD'],
  tr: ['Türkçe', 'tr-TR'], te: ['తెలుగు', 'te-IN'], ro: ['Română', 'ro-RO'], hu: ['Magyar', 'hu-HU'],
  fi: ['Suomi', 'fi-FI'], no: ['Norsk', 'nb-NO'], sk: ['Slovenčina', 'sk-SK'], sw: ['Kiswahili', 'sw-KE'],
  fa: ['فارسی', 'fa-IR', 'rtl'], da: ['Dansk', 'da-DK'], th: ['ไทย', 'th-TH'], vi: ['Tiếng Việt', 'vi-VN'],
  bg: ['Български', 'bg-BG'],
};
// phones report Norwegian as nb (Bokmål) or nn (Nynorsk); both open the Norwegian file
const LANG_ALIAS = { nb: 'no', nn: 'no' };
const I18N = {};
const LANG_KEY = 'annales-lang';

const LANG = (() => {
  const asked = (location.search.match(/[?&]lang=(\w+)/) || [])[1]; // ?lang=pl, for screenshots in another language
  if (asked && LANGS[asked]) return asked;
  try { const saved = localStorage.getItem(LANG_KEY); if (saved && LANGS[saved]) return saved; } catch (e) {}
  for (const l of navigator.languages || [navigator.language || 'en']) {
    let c = String(l).toLowerCase().split('-')[0];
    c = LANG_ALIAS[c] || c;
    if (LANGS[c]) return c;
  }
  return 'en';
})();
const LOCALE = LANGS[LANG][1];
document.documentElement.lang = LANG;
document.documentElement.dir = LANGS[LANG][2] === 'rtl' ? 'rtl' : 'ltr';
// in the Android app, its own screens and the evening reminder follow the same language
try { if (window.AnnalesNative && window.AnnalesNative.setLang) window.AnnalesNative.setLang(LANG); } catch (e) {}
// load just the one language file, before the rest of the app runs
if (LANG !== 'en') document.write(`<script src="i18n/${LANG}.js"><\/script>`);

// numbers stay Western in every language, like the postmark's date and the № on every stamp
function fill(s, v) { return v ? String(s).replace(/\{(\w+)\}/g, (m, k) => (k in v ? v[k] : m)) : s; }
function tr(s, v) {
  let out = I18N[LANG] && I18N[LANG][s];
  if (out && typeof out === 'object') out = out.other;
  return fill(out || s, v);
}
let PLURAL;
try { PLURAL = new Intl.PluralRules(LOCALE); } catch (e) { PLURAL = { select: n => (n === 1 ? 'one' : 'other') }; }
/** tn(3, '{n} stamp', '{n} stamps'): the language's own plural form; the English [other] text is the key. */
function tn(n, one, other, v) {
  const vars = Object.assign({ n }, v);
  const e = I18N[LANG] && I18N[LANG][other];
  if (e && typeof e === 'object') return fill(e[PLURAL.select(n)] || e.other || other, vars);
  if (e) return fill(e, vars);
  return fill(n === 1 ? one : other, vars);
}
function setLanguage(code) {
  try { localStorage.setItem(LANG_KEY, code); } catch (e) {}
  location.reload();
}

/** A "2026-11-05" date in the language: Gregorian, Western digits, never shifted by the time zone. */
function ldate(s, opts) {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString(LOCALE, { timeZone: 'UTC', calendar: 'gregory', numberingSystem: 'latn', ...opts });
}
/** Month 1–12 on its own ("listopad", not "listopada"), and weekday 1 = Monday … 7 = Sunday. */
const lmonth = (m, month = 'long') => new Date(Date.UTC(2026, m - 1, 1)).toLocaleDateString(LOCALE, { timeZone: 'UTC', calendar: 'gregory', month });
const lweekday = (dow, weekday = 'long') => new Date(Date.UTC(2026, 10, 1 + dow)).toLocaleDateString(LOCALE, { timeZone: 'UTC', weekday });
/** Sentence case for names that some languages write in lower case ("listopad" → "Listopad") when they stand alone. */
const cap = s => s.charAt(0).toLocaleUpperCase(LOCALE) + s.slice(1);
