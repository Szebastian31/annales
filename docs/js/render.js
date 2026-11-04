'use strict';
// render/StampRenderer.kt, render/Filters.kt and render/Photos.kt for the web: same sizes, same edges, same postmark.

const SR = {
  W: 800, H: 1000,
  // Saved stamps keep a clear margin round the paper, so the cancels can hang off the edges.
  PL: 160, PR: 280, PT: 60, PB: 60,
  k: 800 / 150,
  shapes: [['portrait', 'Portrait', [800, 1000]], ['square', 'Square', [900, 900]], ['wide', 'Wide', [1000, 820]]],
  size(shape) { return (this.shapes.find(s => s[0] === shape) || this.shapes[0])[2]; },
  shapeOf: (w, h) => (w > h ? 'wide' : w === h ? 'square' : 'portrait'),
  cuts: [
    ['classic', 'Classic', 'round holes'], ['scallop', 'Scallop', 'big soft bites'],
    ['pinhole', 'Pinhole', 'tiny, dense'], ['comb', 'Comb', 'square teeth'],
    ['pinking', 'Pinking', 'zigzag shears'], ['wave', 'Wave', 'smooth ripple'],
    ['rouletted', 'Rouletted', 'slit tear line'], ['deckle', 'Deckle', 'torn by hand'],
    ['ticket', 'Ticket', 'notched stub'], ['sticker', 'Sticker', 'die-cut round'],
  ],
};

function canvas(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.round(w)); c.height = Math.max(1, Math.round(h));
  return c;
}
/** Frees a canvas's memory at once (iPhones have a tight budget for canvases). */
const drop = c => { if (c) { c.width = 0; c.height = 0; } };

function roundRectPath(p, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  p.moveTo(x + r, y);
  p.arcTo(x + w, y, x + w, y + h, r); p.arcTo(x + w, y + h, x, y + h, r);
  p.arcTo(x, y + h, x, y, r); p.arcTo(x, y, x + w, y, r);
  p.closePath();
}

/**
 * The paper outline with its edge cut, in a w × h box: { paper, holes }. Draw the paper, then erase the holes
 * (destination-out), which is the same as Android's paper DIFFERENCE holes.
 */
SR.shape = function (cut, seed = 7, w = SR.W, h = SR.H) {
  const u = Math.min(w, h) / 150;
  let paper = new Path2D(); paper.rect(0, 0, w, h);
  const holes = new Path2D();
  const circle = (x, y, r) => { holes.moveTo(x + r, y); holes.arc(x, y, r, 0, Math.PI * 2); };
  const box = (x, y, bw, bh) => holes.rect(x, y, bw, bh);
  // points every L/n along the four edges, corners included
  const sides = (p, at) => {
    for (const [len, vertical] of [[w, false], [h, true]]) {
      const n = Math.max(2, Math.round(len / p));
      for (let i = 0; i <= n; i++) { const t = i * len / n; if (vertical) { at(0, t); at(w, t); } else { at(t, 0); at(t, h); } }
    }
  };
  const circles = (r, p) => sides(p, (x, y) => circle(x, y, r));
  // a wavy or torn outline: depth(t, period) inward from each edge, sampled every [step]
  const outline = (p, step, depth) => {
    paper = new Path2D();
    let first = true;
    const pt = (x, y) => { if (first) { paper.moveTo(x, y); first = false; } else paper.lineTo(x, y); };
    const run = (len, map) => {
      const period = len / Math.max(1, Math.round(len / p));
      const k = Math.ceil(len / step);
      for (let i = 0; i < k; i++) { const t = i * len / k; const [x, y] = map(t, depth(t, period)); pt(x, y); }
    };
    run(w, (t, d) => [t, d]); run(h, (t, d) => [w - d, t]);
    run(w, (t, d) => [w - t, h - d]); run(h, (t, d) => [d, h - t]);
    paper.closePath();
  };

  switch (cut) {
    case 'scallop': circles(5.8 * u, 13.5 * u); break;
    case 'pinhole': circles(1.3 * u, 4.3 * u); break;
    case 'comb': { const s = 4.8 * u; sides(9.8 * u, (x, y) => box(x - s / 2, y - s / 2, s, s)); break; }
    case 'pinking': outline(7.4 * u, .6 * u, (t, period) => { const q = (t % period) / period; return 4 * u * (q < .5 ? q * 2 : 2 - q * 2); }); break;
    case 'wave': outline(12 * u, .9 * u, (t, period) => 3.4 * u * (1 - Math.cos(2 * Math.PI * t / period)) / 2); break;
    case 'deckle': {
      const r = new KRandom(seed);
      const ph = [r.nextFloat() * 9, r.nextFloat() * 9];
      outline(9 * u, 1.5 * u, t => u * (1.4 + 1.2 * Math.sin(t / (5 * u) + ph[0]) + .8 * Math.sin(t / (2.1 * u) + ph[1]) + .9 * r.nextFloat()));
      break;
    }
    case 'rouletted': {
      const m = 3.2 * u, l = 4.6 * u, th = 1.3 * u;
      for (const [len, vertical] of [[w, false], [h, true]]) {
        const n = Math.round((len - 2 * m) / (7.4 * u));
        for (let i = 1; i < n; i++) {
          const t = m + i * (len - 2 * m) / n;
          for (const o of [m, (vertical ? w : h) - m]) {
            if (vertical) box(o - th / 2, t - l / 2, th, l); else box(t - l / 2, o - th / 2, l, th);
          }
        }
      }
      break;
    }
    case 'ticket': {
      const r = 7 * u;
      paper = new Path2D(); roundRectPath(paper, 0, 0, w, h, 2 * u);
      for (const [x, y] of [[0, 0], [w, 0], [0, h], [w, h]]) circle(x, y, r * .8);
      for (const [x, y] of [[0, h / 2], [w, h / 2]]) circle(x, y, r);
      for (let x = r * 1.4; x < w - r; x += 3.6 * u) circle(x, h * .78, .85 * u);
      break;
    }
    case 'sticker': { const rr = Math.min(w, h) * .12; paper = new Path2D(); roundRectPath(paper, 0, 0, w, h, rr); break; }
    default: circles(3.5 * u, 9.8 * u);
  }
  return { paper, holes };
};

/** Fills the cut paper in [color] (for the little perforation tiles), into a w × h canvas. */
SR.silhouette = function (cut, w, h, color) {
  const c = canvas(w, h), g = c.getContext('2d');
  const { paper, holes } = SR.shape(cut, 7, w, h);
  g.fillStyle = color; g.fill(paper);
  g.globalCompositeOperation = 'destination-out'; g.fill(holes);
  return c;
};

/** Draws [photo] cut to its edge at (x, y) on g. */
function cutPaper(g, photo, cut, seed, x, y) {
  const w = photo.width, h = photo.height;
  const { paper, holes } = SR.shape(cut, seed, w, h);
  g.save(); g.translate(x, y);
  g.save(); g.clip(paper);
  g.imageSmoothingQuality = 'high';
  g.drawImage(photo, 0, 0, w, h);
  g.restore();
  g.globalCompositeOperation = 'destination-out'; g.fill(holes);
  g.restore();
}

/** Just the perforated paper, the photo's size with no margin and no ink: what goes into Photos. */
SR.clean = function (photo, cut, seed = 7) {
  const out = canvas(photo.width, photo.height);
  cutPaper(out.getContext('2d'), photo, cut, seed, 0, 0);
  return out;
};

/** [photo] is the cropped photo, at one of the shapes' sizes. Without a [seal] it's a draft: just the cut paper. */
SR.render = function (photo, cut, seal, seed = 7) {
  const w = photo.width, h = photo.height;
  const out = canvas(w + SR.PL + SR.PR, h + SR.PT + SR.PB);
  const g = out.getContext('2d');
  cutPaper(g, photo, cut, seed, SR.PL, SR.PT);
  if (seal) { const c = cancels(seal, w, h); g.drawImage(c, 0, 0); drop(c); }
  return out;
};

/** Every hit of the stamp is a little different: angle, pressure and patchy spots. */
class Hit {
  constructor(seed) {
    const r = new KRandom((Math.imul(seed, 7919) + 13) | 0);
    this.light = r.nextFloat() < .2;
    this.rot = -22 + r.nextFloat() * 28;
    this.op = this.light ? .62 + r.nextFloat() * .12 : .8 + r.nextFloat() * .16;
    this.dx = (r.nextFloat() - .5) * 8 * SR.k;
    this.dy = (r.nextFloat() - .5) * 8 * SR.k;
    this.patch = this.light ? .55 : .3 + r.nextFloat() * .15;
    this.noise = r.nextInt();
  }
}

/** Android's canvas.rotate(deg, px, py). */
const rotateAt = (g, deg, px, py) => { g.translate(px, py); g.rotate(deg * Math.PI / 180); g.translate(-px, -py); };

function cancels(s, w, h) {
  const layer = canvas(w + SR.PL + SR.PR, h + SR.PT + SR.PB);
  const g = layer.getContext('2d');
  const pm = new Hit(s.seed), kl = new Hit(s.seed + 101);
  // the date ring: 62% of the short side, hanging off the right edge
  const R = Math.min(w, h) * .62;
  mark(g, pm, m => {
    m.translate(SR.PL + w - R * .58 + pm.dx, SR.PT + h * .5 - R * .42 + pm.dy);
    rotateAt(m, pm.rot, R / 2, R / 2);
    m.scale(R / 100, R / 100);
    datownik(m, s);
  });
  // the wavy lines: a quarter of them off the left edge, just under the day number
  const Kw = Math.min(w, h) * .3;
  mark(g, kl, m => {
    m.translate(SR.PL - Kw * 1.9 * .25 - kl.dx * .3, SR.PT + h * .16 + kl.dy * .4);
    rotateAt(m, pm.rot * .4 - 13, Kw * .95, Kw / 2);
    m.scale(Kw / 60, Kw / 60);
    waves(m, s.ink);
  });
  return layer;
}

/** Draws one mark on its own canvas, wears its ink thin in patches, then presses it on [target] at the hit's pressure. */
function mark(target, hit, draw) {
  const W = target.canvas.width, H = target.canvas.height;
  const m = canvas(W, H), mc = m.getContext('2d');
  mc.save(); draw(mc); mc.restore();
  const mask = wear(hit, W, H);
  mc.globalCompositeOperation = 'destination-in';
  mc.drawImage(mask, 0, 0);
  drop(mask);
  target.save();
  target.globalAlpha = Math.round(hit.op * 255) / 255;
  target.drawImage(m, 0, 0);
  target.restore();
  drop(m);
}

/** An alpha mask: mostly solid, with soft thin patches and fine grain. */
function wear(hit, w, h) {
  const out = canvas(w, h), g = out.getContext('2d');
  const r = new KRandom(hit.noise);
  const grid = (cols, rows, lo, hi) => {
    const c = canvas(cols, rows), cg = c.getContext('2d');
    const img = cg.createImageData(cols, rows);
    for (let y = 0; y < rows; y++) for (let x = 0; x < cols; x++) {
      const a = clamp(lo + (hi - lo) * r.nextFloat(), 0, 1);
      img.data[(y * cols + x) * 4 + 3] = Math.round(a * 255);
    }
    cg.putImageData(img, 0, 0);
    return c;
  };
  g.imageSmoothingEnabled = true;
  const patches = grid(10, 12, 1 - hit.patch * 2.2, 1.6);
  g.drawImage(patches, 0, 0, w, h); drop(patches);
  const grain = grid(200, 250, .55, 1.4);
  g.globalCompositeOperation = 'destination-in';
  g.drawImage(grain, 0, 0, w, h); drop(grain);
  return out;
}

const MONO = 'ui-monospace, Menlo, "Courier New", monospace';
const SANS = '-apple-system, system-ui, Roboto, "Helvetica Neue", Arial, sans-serif';

/** Poczta Polska datownik on a 100-unit grid: ring, office name over the top, postcode under, date bridge dd.mm.yy and hour. */
function datownik(c, s) {
  const ink = rgb(s.ink);
  c.strokeStyle = ink; c.fillStyle = ink; c.lineCap = 'round';
  c.lineWidth = 2.6; c.beginPath(); c.arc(50, 50, 44, 0, Math.PI * 2); c.stroke();
  c.lineWidth = 1.8; c.beginPath(); c.moveTo(12, 39); c.lineTo(88, 39); c.moveTo(12, 61); c.lineTo(88, 61); c.stroke();

  const [y, m, d] = s.date.split('-');
  c.textAlign = 'center'; c.textBaseline = 'alphabetic';
  c.font = `bold 14.5px ${MONO}`;
  c.fillText(`${d}.${m}.${y.slice(2)}`, 50, 55.5);
  c.font = `bold 7.5px ${MONO}`;
  c.fillText(`-${s.hour}`, 50, 35);

  const top = s.office.toUpperCase();
  onArc(c, top, top.length > 9 ? 9.5 : 11, 1.3, 50, 50, 36, Math.PI, 1);
  onArc(c, s.postcode, 7.5, 1.2, 50, 54, 34, Math.PI, -1);
}

/** Text along a half circle, centred like Android's drawTextOnPath with hOffset = (length − width) / 2. */
function onArc(c, text, size, spacing, cx, cy, r, start, dir) {
  if (!text || !text.trim()) return;
  c.save();
  c.font = `bold ${size}px ${SANS}`;
  c.textAlign = 'center'; c.textBaseline = 'alphabetic';
  const chars = [...text];
  const widths = chars.map(ch => c.measureText(ch).width + spacing);
  const total = widths.reduce((a, b) => a + b, 0);
  let at = Math.max(0, (Math.PI * r - total) / 2);
  chars.forEach((ch, i) => {
    const mid = at + widths[i] / 2;
    const th = start + dir * mid / r;
    c.save();
    c.translate(cx + r * Math.cos(th), cy + r * Math.sin(th));
    c.rotate(th + dir * Math.PI / 2);
    c.fillText(ch, 0, 0);
    c.restore();
    at += widths[i];
  });
  c.restore();
}

/** Five wavy cancel lines on a 114 × 60 grid. */
function waves(c, ink) {
  c.strokeStyle = rgb(ink); c.lineWidth = 2.6; c.lineCap = 'round';
  for (let i = 0; i < 5; i++) {
    const y = 8 + i * 11;
    c.beginPath(); c.moveTo(4, y);
    let x = 4;
    for (let j = 0; j < 6; j++) { c.quadraticCurveTo(x + 9, y + (j % 2 === 0 ? -6 : 6), x + 18, y); x += 18; }
    c.stroke();
  }
}

/** The five photo filters, as 4 × 5 colour matrices built from the same CSS steps. */
const Filters = {
  all: [['natural', 'Natural'], ['print', 'Print'], ['faded', 'Faded'], ['dusk', 'Dusk'], ['mono', 'Mono']],
  /** The same look as a CSS filter, for the live preview while framing. */
  cssOf: {
    natural: 'none', print: 'contrast(1.1) saturate(.88) sepia(.14)',
    faded: 'contrast(.9) saturate(.72) brightness(1.06) sepia(.12)',
    dusk: 'saturate(1.2) contrast(1.05) hue-rotate(-8deg)', mono: 'saturate(0) contrast(1.15)',
  },
  identity: () => [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0],
  /** Android's ColorMatrix.postConcat: [b] applied after [a]. */
  then(a, b) {
    const o = new Array(20);
    for (let r = 0; r < 4; r++) for (let c = 0; c < 5; c++) {
      let v = 0;
      for (let k = 0; k < 4; k++) v += b[r * 5 + k] * a[k * 5 + c];
      if (c === 4) v += b[r * 5 + 4];
      o[r * 5 + c] = v;
    }
    return o;
  },
  saturate(s) { const i = 1 - s, R = .213 * i, G = .715 * i, B = .072 * i; return [R + s, G, B, 0, 0, R, G + s, B, 0, 0, R, G, B + s, 0, 0, 0, 0, 0, 1, 0]; },
  contrast(c) { const t = (1 - c) * 127.5; return [c, 0, 0, 0, t, 0, c, 0, 0, t, 0, 0, c, 0, t, 0, 0, 0, 1, 0]; },
  brightness(b) { return [b, 0, 0, 0, 0, 0, b, 0, 0, 0, 0, 0, b, 0, 0, 0, 0, 0, 1, 0]; },
  sepia(a) {
    const i = 1 - a;
    return [.393 + .607 * i, .769 - .769 * i, .189 - .189 * i, 0, 0, .349 - .349 * i, .686 + .314 * i, .168 - .168 * i, 0, 0,
      .272 - .272 * i, .534 - .534 * i, .131 + .869 * i, 0, 0, 0, 0, 0, 1, 0];
  },
  hue(deg) {
    const r = deg * Math.PI / 180, c = Math.cos(r), s = Math.sin(r);
    return [.213 + c * .787 - s * .213, .715 - c * .715 - s * .715, .072 - c * .072 + s * .928, 0, 0,
      .213 - c * .213 + s * .143, .715 + c * .285 + s * .140, .072 - c * .072 - s * .283, 0, 0,
      .213 - c * .213 - s * .787, .715 - c * .715 + s * .715, .072 + c * .928 + s * .072, 0, 0, 0, 0, 0, 1, 0];
  },
  matrix(key) {
    const F = Filters;
    const steps = {
      print: [F.contrast(1.1), F.saturate(.88), F.sepia(.14)],
      faded: [F.contrast(.9), F.saturate(.72), F.brightness(1.06), F.sepia(.12)],
      dusk: [F.saturate(1.2), F.contrast(1.05), F.hue(-8)],
      mono: [F.saturate(0), F.contrast(1.15)],
    }[key] || [];
    return steps.reduce((m, x) => F.then(m, x), F.identity());
  },
  /** Applies a filter to a canvas, in place. */
  apply(c, key) {
    if (key === 'natural') return c;
    const m = Filters.matrix(key), g = c.getContext('2d');
    const img = g.getImageData(0, 0, c.width, c.height), p = img.data;
    for (let i = 0; i < p.length; i += 4) {
      const r = p[i], gg = p[i + 1], b = p[i + 2], a = p[i + 3];
      p[i] = m[0] * r + m[1] * gg + m[2] * b + m[3] * a + m[4];
      p[i + 1] = m[5] * r + m[6] * gg + m[7] * b + m[8] * a + m[9];
      p[i + 2] = m[10] * r + m[11] * gg + m[12] * b + m[13] * a + m[14];
    }
    g.putImageData(img, 0, 0);
    return c;
  },
};

/** Reading photos in, upright, scaled down to at most 2400 px on the long side. */
const Photos = {
  MAX: 2400,
  /** A Blob or File → an <img>, decoded. */
  image(blob) {
    return new Promise((ok, no) => {
      const url = URL.createObjectURL(blob), img = new Image();
      img.onload = () => { URL.revokeObjectURL(url); ok(img); };
      img.onerror = () => { URL.revokeObjectURL(url); no(new Error('decode')); };
      img.src = url;
    });
  },
  /** A picked photo → a canvas at most MAX on its long side. */
  async load(blob) {
    const img = await Photos.image(blob);
    const s = Math.min(1, Photos.MAX / Math.max(img.naturalWidth, img.naturalHeight));
    const c = canvas(img.naturalWidth * s, img.naturalHeight * s);
    const g = c.getContext('2d'); g.imageSmoothingQuality = 'high';
    g.drawImage(img, 0, 0, c.width, c.height);
    return c;
  },
  /** A stored photo (exact size) → a canvas. */
  async canvasOf(blob) {
    const img = await Photos.image(blob);
    const c = canvas(img.naturalWidth, img.naturalHeight);
    c.getContext('2d').drawImage(img, 0, 0);
    return c;
  },
  blob: (c, type = 'image/png', q = .92) => new Promise((ok, no) => c.toBlob(b => (b ? ok(b) : no(new Error('encode'))), type, q)),
  jpeg: c => Photos.blob(c, 'image/jpeg', .92),
  png: c => Photos.blob(c, 'image/png'),
};
