'use strict';
// Small helpers every screen uses: building elements, the stamp view, sheets and dialogs, and messages.

/** h('div.card.row', { onclick }, kids…). Strings become text; null, false and undefined are skipped. */
function h(sel, props, ...kids) {
  const [tag, ...cls] = sel.split('.');
  const el = document.createElement(tag || 'div');
  if (cls.length) el.className = cls.join(' ');
  if (props != null && (typeof props !== 'object' || props instanceof Node || Array.isArray(props))) { kids.unshift(props); props = null; }
  if (props) for (const [k, v] of Object.entries(props)) {
    if (v == null || v === false) continue;
    if (k === 'style' && typeof v === 'object') { for (const [sk, sv] of Object.entries(v)) if (sv != null) el.style[sk] = typeof sv === 'number' && !/opacity|zIndex|flex/.test(sk) ? sv + 'px' : sv; }
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className += (el.className ? ' ' : '') + v;
    else if (k === 'html') el.innerHTML = v;
    else if (k in el && !['list', 'type', 'form'].includes(k)) el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  add(el, kids);
  return el;
}
function add(el, kids) {
  for (const k of kids.flat(Infinity)) if (k != null && k !== false && k !== true) el.append(k instanceof Node ? k : String(k));
  return el;
}
/** "Today, *not sealed yet*": plain words, then the accent in italics, the way every title reads. One string, with the
 * italic part between asterisks, so each language can put it where its grammar wants it. */
const title = text => { const [a, em = '', b = ''] = text.split('*'); return h('div.title', null, a, h('em', null, em), b); };
const header = (kicker, text) => h('div.hdr', null, h('div.kick', null, kicker), title(text));
const sec = (left, right = '') => h('div.sec', null, h('div.kick', null, left), right && h('span.small', { style: { fontSize: '12.5px' } }, right));

const SVG = (inner, w = 24, h = 24) => `<svg viewBox="0 0 ${w} ${h}" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;
const TAB_ICONS = {
  today: SVG('<rect x="5" y="4" width="14" height="17" rx="1.5" stroke-dasharray="2 2"/><circle cx="12" cy="12" r="3.2"/>'),
  journal: SVG('<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 9.5h16M9 3v4M15 3v4"/><rect x="7" y="12" width="3" height="3.5"/><rect x="13.5" y="12" width="3" height="3.5"/>'),
  collections: SVG('<rect x="7" y="3" width="13" height="15" rx="1.5" stroke-dasharray="2 1.6"/><path d="M4 7v13.5c0 .3.2.5.5.5H16"/>'),
  insights: SVG('<path d="M4 19h16M6 16c2-6 4-9 6-6s4 1 6-5"/>'),
  profile: SVG('<circle cx="12" cy="8.5" r="3.8"/><path d="M4.5 20c1.3-3.6 4-5.3 7.5-5.3s6.2 1.7 7.5 5.3"/>'),
};

/**
 * A stamp as the renderer drew it, with the day number and mood dot laid over it. The slot is always portrait-sized;
 * the postmark spills past the paper ([bleed]) except on tiny thumbnails, which clip and shrink to fit instead.
 */
function stampView(id, doy, mood, width, opt = {}) {
  const bleed = opt.bleed ?? width >= 50, small = width < 110;
  const slot = h('div.st', { style: { width, height: width * 1.25, overflow: bleed ? 'visible' : 'hidden', transform: opt.rotation ? `rotate(${opt.rotation}deg)` : null } });
  const inner = h('div.inner', { style: { width, height: width * 1.25 } });
  const img = h('img', { alt: '', draggable: false });
  slot.append(inner);
  const layout = (bw, bh) => {
    const padded = bw > SR.W;
    const pw = padded ? bw - SR.PL - SR.PR : bw, ph = padded ? bh - SR.PT - SR.PB : bh;
    const u = bleed ? width / SR.W : Math.min(width / pw, width * 1.25 / ph);
    Object.assign(inner.style, { width: u * pw + 'px', height: u * ph + 'px' });
    Object.assign(img.style, padded
      ? { left: -u * SR.PL + 'px', top: -u * SR.PT + 'px', width: u * bw + 'px', height: u * bh + 'px' }
      : { left: 0, top: 0, width: '100%', height: '100%' });
    inner.classList.add('ready');
  };
  img.onload = () => { stampView.dims[img.src] = [img.naturalWidth, img.naturalHeight]; layout(img.naturalWidth, img.naturalHeight); };
  const pad = small ? 7 : 13;
  inner.append(img, h('span.no', { style: { left: pad, top: small ? 7 : 12, fontSize: small ? '10.5px' : '16px' } }, String(doy)));
  if (mood != null) {
    const s = small ? 7 : 11;
    inner.append(h('i.md', { style: { right: pad, top: pad, width: s, height: s, background: Moods.css(mood), borderWidth: small ? '1.5px' : '2px' } }));
  }
  M.stampUrl(id).then(u => {
    if (!u) return;
    const known = stampView.dims[u];
    if (known) layout(known[0], known[1]); // laid out at once, so a redraw doesn't blink
    img.src = u;
  });
  return slot;
}
stampView.dims = {};

/** A message at the bottom, with an optional action (needed on iPhone: sharing must start from a tap). */
function toast(msg, action, label = tr('Save')) {
  const box = document.getElementById('toast');
  const t = h('div', null, msg, action && h('button', { onclick: () => { t.remove(); action(); } }, label));
  box.append(t);
  setTimeout(() => t.remove(), action ? 9000 : 3200);
}

/** The overlay layers: each is { key, el, refresh }. App.render() refreshes them so they follow the model. */
const Layers = {
  list: [],
  /** [fixed] layers (the crop screen) are built once and keep their own state; the rest are rebuilt on every change. */
  open(key, build, { className = '', onClose, fixed = false } = {}) {
    this.close(key);
    const el = h('div', { class: className });
    const layer = {
      key, el, onClose,
      refresh: () => {
        if (fixed && el.firstChild) return;
        const keep = el.querySelector(':focus');
        if (keep && keep.matches('input,textarea')) return;
        const top = [...el.querySelectorAll('.sheet, .scroll')].map(s => s.scrollTop);
        el.replaceChildren(...[build(layer)].flat().filter(Boolean));
        [...el.querySelectorAll('.sheet, .scroll')].forEach((s, i) => { if (top[i]) s.scrollTop = top[i]; });
      },
    };
    layer.refresh();
    document.getElementById('overlay').append(el);
    this.list.push(layer);
    return layer;
  },
  close(key) {
    const i = this.list.findIndex(l => l.key === key);
    if (i < 0) return;
    const [l] = this.list.splice(i, 1);
    l.el.remove();
    if (l.onClose) l.onClose();
  },
  has(key) { return this.list.some(l => l.key === key); },
  refreshAll() { this.list.forEach(l => l.refresh()); },
};

/** A bottom sheet: tap outside or Done to close. */
function sheet(key, build, { surface = false, onClose } = {}) {
  return Layers.open(key, layer => {
    const panel = h('div.sheet' + (surface ? '.surface' : ''), { onclick: e => e.stopPropagation() }, h('div.handle'), build(layer));
    return panel;
  }, { className: 'scrim', onClose }).el.addEventListener('click', () => Layers.close(key));
}

/** A centred dialog. [build] returns its content; actions are [label, fn, style]. */
function dialog(key, heading, build, actions) {
  Layers.open(key, () => h('div', { onclick: e => e.stopPropagation() },
    h('h2', null, heading), build(),
    h('div.acts', null, actions.map(([label, fn, style]) => h('button', { style: { color: style || 'var(--acc)' }, onclick: fn }, label)))),
  { className: 'dialog' }).el.addEventListener('click', () => Layers.close(key));
}

/** Paper grain for envelopes and pages (a CSS pattern; the Android app speckles it in code). */
const grained = el => { el.classList.add('grain'); return el; };

/** A switch row: name, a line under it, and the toggle. */
function toggleRow(name, sub, on, onChange) {
  return h('div.toggle', null,
    h('div.grow', null, h('div.body', null, name), h('div.small', null, sub)),
    onChange ? h('label.switch', null, h('input', { type: 'checkbox', checked: on, onchange: e => onChange(e.target.checked) }), h('span')) : h('b', { style: { color: 'var(--green)' } }, '✓'));
}

function inkChip(ink, selected, label, onClick) {
  return h('button.ink' + (selected ? '.on' : ''), { style: { borderColor: selected ? rgb(ink.rgb) : null }, onclick: onClick },
    h('i', { style: { background: rgb(ink.rgb) } }), label || ink.label);
}

const isStandalone = () => window.navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;
const isIOS = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

/** Hands files to the share sheet, where iPhone offers Save Image; elsewhere, downloads them. */
async function shareFiles(files, text) {
  if (navigator.canShare && navigator.canShare({ files })) {
    try { await navigator.share({ files, text }); return true; } catch (e) { if (e.name === 'AbortError') return false; }
  }
  for (const f of files) {
    const a = h('a', { href: URL.createObjectURL(f), download: f.name });
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 60e3);
  }
  return true;
}

// Anything that breaks shows itself at the top, so it can be reported instead of failing quietly.
window.addEventListener('error', e => showError(e.message + (e.filename ? ` (${e.filename.split('/').pop()}:${e.lineno})` : '')));
window.addEventListener('unhandledrejection', e => showError(String(e.reason && (e.reason.stack || e.reason.message) || e.reason)));
function showError(msg) {
  let el = document.getElementById('err');
  if (!el) { el = h('div', { id: 'err', onclick: () => el.remove() }); document.body.append(el); }
  el.textContent = `${tr("Something went wrong: {error}", { error: msg })}\n${tr("Tap to hide.")}`;
}
