'use strict';
// render/SkyStamp.kt for the web: the day's sky, drawn like a small print, on a 100 × 125 grid in real pixels.

const SKY = {
  CLEAR: { name: 'CLEAR', art: 'Clear', label: tr('Clear'), short: tr('Clear'), glyph: '☀', top: 0xFF5E8FC4, bottom: 0xFFF6D9B4, mid: 0xFFA9C6E0, light: 0xFFFFF1D2 },
  PARTLY: { name: 'PARTLY', art: 'Partly cloudy', label: tr('Partly cloudy'), short: tr('Partly'), glyph: '⛅', top: 0xFF7D9EC2, bottom: 0xFFF0DCC4, mid: 0xFFB9CCDD, light: 0xFFFFF0D6 },
  CLOUDY: { name: 'CLOUDY', art: 'Overcast', label: tr('Overcast'), short: tr('Overcast'), glyph: '☁', top: 0xFF8C939D, bottom: 0xFFE2DDD3, mid: 0xFFB9BCBF, light: 0xFFF4F0E8 },
  RAIN: { name: 'RAIN', art: 'Rain', label: tr('Rain'), short: tr('Rain'), glyph: '☂', top: 0xFF5D6774, bottom: 0xFFB9BEC3, mid: 0xFF8A939D, light: 0xFFDDE2E7 },
  FOG: { name: 'FOG', art: 'Fog', label: tr('Fog'), short: tr('Fog'), glyph: '≋', top: 0xFFB9B9B4, bottom: 0xFFEDE9E0, mid: 0xFFD5D3CC, light: 0xFFFFFCF4 },
};
const SKY_LIST = [SKY.CLEAR, SKY.PARTLY, SKY.CLOUDY, SKY.RAIN, SKY.FOG];

const Sky = (() => {
  const GW = 100, GH = 125;
  const withAlpha = (c, a) => (((a & 255) << 24) | (c & 0xFFFFFF)) >>> 0;
  const mix = (a, b, t) => {
    const ch = (v, s) => (v >>> s) & 255;
    const m = s => clamp(Math.trunc(ch(a, s) + (ch(b, s) - ch(a, s)) * t), 0, 255);
    return ((0xFF << 24) | (m(16) << 16) | (m(8) << 8) | m(0)) >>> 0;
  };
  /**
   * Android's BlurMaskFilter: the shape's shadow, drawn far off and offset back, so it blurs on every browser.
   * Skia's sigma is radius × 0.577 + 0.5, and a canvas shadow's sigma is shadowBlur / 2.
   */
  const blurred = (g, radius, color, draw) => {
    g.save();
    g.shadowColor = color; g.shadowBlur = 2 * (radius * .57735 + .5); g.shadowOffsetX = 10000;
    g.fillStyle = '#000';
    g.translate(-10000, 0); draw(); g.restore();
  };
  const lin = (g, x0, y0, x1, y1, colors, pos) => {
    const gr = g.createLinearGradient(x0, y0, x1, y1);
    colors.forEach((c, i) => gr.addColorStop(pos ? pos[i] : i / (colors.length - 1), css(c)));
    return gr;
  };
  const rad = (g, x, y, r, colors, pos) => {
    const gr = g.createRadialGradient(x, y, 0, x, y, r);
    colors.forEach((c, i) => gr.addColorStop(pos ? pos[i] : i / (colors.length - 1), css(c)));
    return gr;
  };
  const disc = (g, x, y, r) => { g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.fill(); };

  function glow(g, x, y, radius, color, alpha) {
    g.fillStyle = rad(g, x, y, radius, [withAlpha(color, alpha), withAlpha(color, 0)]);
    disc(g, x, y, radius);
  }

  /** A cloud: a few soft puffs over a flat base, with a faint shadow underneath. */
  function cloud(g, r, u, cx, cy, size, rain, near) {
    const n = 5 + r.nextInt(3), puffs = [];
    for (let i = 0; i < n; i++) {
      const dx = (i - 3) * 6.5 * size * u + (r.nextFloat() - .5) * 3 * u;
      const rr = (4.5 + r.nextFloat() * 4.5) * size * u * (1 - Math.abs(i - 3) * .12);
      puffs.push([cx + dx, cy - rr * .35, rr]);
    }
    const shade = css(withAlpha(rain ? 0x6E7782 : 0x9FA9B5, rain ? 150 : 70));
    blurred(g, 1.6 * u * size, shade, () => puffs.forEach(([x, y, q]) => disc(g, x, y + 2.2 * u, q)));
    const body = css(withAlpha(rain ? 0xC3C8CE : 0xFFFFFF, near ? 240 : 190));
    blurred(g, 1.6 * u * size, body, () => {
      puffs.forEach(([x, y, q]) => disc(g, x, y, q));
      g.beginPath(); roundRectPath(g, cx - 20 * size * u, cy - 1 * u, 40 * size * u, 4.5 * size * u + 1 * u, 4 * u); g.fill();
    });
  }

  /** A ridge line from a few slow waves; smooth ones (far away) have fewer, gentler bumps. */
  function ridge(r, u, base, amp, smooth) {
    const ph = [r.nextFloat() * 2 * Math.PI, r.nextFloat() * 2 * Math.PI, r.nextFloat() * 2 * Math.PI];
    const fr = smooth ? [1.1, 2.3, 4.1] : [1.6, 3.4, 7.3];
    const wt = smooth ? [.62, .3, .08] : [.5, .32, .18];
    const y = x => base - amp * [0, 1, 2].reduce((s, i) => s + wt[i] * Math.sin(x / GW * fr[i] * Math.PI + ph[i]), 0);
    const p = new Path2D();
    p.moveTo(0, y(0) * u);
    for (let x = 2; x <= GW + 2; x += 2) p.lineTo(x * u, y(x) * u);
    p.lineTo(GW * u + 4, GH * u); p.lineTo(0, GH * u); p.closePath();
    return p;
  }

  function tree(g, r, u, ground, color) {
    const x = (14 + r.nextFloat() * 66) * u, gy = ground * u;
    g.fillStyle = css(color);
    g.fillRect(x - .45 * u, gy - 7 * u, .9 * u, 7 * u);
    g.beginPath(); g.ellipse(x, gy - 10 * u, 2.6 * u, 5 * u, 0, 0, Math.PI * 2); g.fill();
  }

  function birds(g, r, u, lx, ly) {
    g.save();
    g.strokeStyle = css(0x993A3530); g.lineWidth = .45 * u; g.lineCap = 'round';
    const bx = lx > 50 * u ? 22 * u : 70 * u;
    for (let i = 0; i < 3; i++) {
      const x = bx + (i * 5 + r.nextFloat() * 2) * u, y = ly + (6 + i * 2.5 + r.nextFloat() * 2) * u, s = (1.4 - i * .2) * u;
      g.beginPath(); g.moveTo(x - 2 * s, y - s * .6);
      g.quadraticCurveTo(x - s, y - 1.5 * s, x, y); g.quadraticCurveTo(x + s, y - 1.5 * s, x + 2 * s, y - s * .6);
      g.stroke();
    }
    g.restore();
  }

  function rain(g, r, u, w) {
    g.save(); g.lineCap = 'round';
    for (let i = 0; i < 70; i++) {
      const x = r.nextFloat() * (w + 20 * u), y = (22 + r.nextFloat() * 90) * u, len = (4 + r.nextFloat() * 5) * u;
      const a = 50 + r.nextInt(90);
      g.strokeStyle = css(withAlpha(0xF1F4F7, a)); g.lineWidth = (.25 + r.nextFloat() * .3) * u;
      g.beginPath(); g.moveTo(x, y); g.lineTo(x - len * .28, y + len); g.stroke();
    }
    g.restore();
  }

  /** The lit part of the moon: a half disc closed by an ellipse whose width follows the phase. */
  function moon(g, cx, cy, r, age) {
    const k = Math.cos(2 * Math.PI * age), waxing = age < .5, rx = Math.abs(k) * r;
    g.beginPath(); g.moveTo(cx, cy - r);
    g.arc(cx, cy, r, -Math.PI / 2, Math.PI / 2, !waxing);
    const bulgeOut = k < 0, cw = waxing === bulgeOut;
    g.ellipse(cx, cy, Math.max(rx, .001), r, 0, Math.PI / 2, cw ? Math.PI * 1.5 : -Math.PI / 2, !cw);
    g.closePath(); g.fill();
  }

  /** Bottom left, on the near ridge: that night's moon and the weather, small and spaced, like a print's plate line. */
  function caption(g, u, date, wx) {
    const y = 117.5 * u, mx = 9.5 * u, mr = 2.4 * u;
    g.fillStyle = css(0x40FFFFFF); disc(g, mx, y - 1.3 * u, mr);
    g.fillStyle = css(0xE6FFFAF0); moon(g, mx, y - 1.3 * u, mr, Moon.age(date));
    const size = 3.4 * u;
    g.font = `${size}px ui-serif, Georgia, "Times New Roman", serif`;
    g.textBaseline = 'alphabetic'; g.textAlign = 'left';
    let x = mx + mr + 2.8 * u;
    for (const ch of wx.art.toUpperCase()) { g.fillText(ch, x, y); x += g.measureText(ch).width + .28 * size; }
  }

  function grain(g, r, w, h) {
    const dark = css(0x0F000000), light = css(0x12FFFFFF);
    for (let i = 0, n = Math.trunc(w * h / 90); i < n; i++) {
      g.fillStyle = r.nextBoolean() ? dark : light;
      const x = r.nextFloat() * w, y = r.nextFloat() * h;
      g.fillRect(x, y, 1.6, 1.6);
    }
  }

  /** [moodColor] is RGB, or null when no mood is set yet (then the hills are a warm grey). */
  function draw(date, wx, moodColor, seed, w = SR.W, h = SR.H) {
    const out = canvas(w, h), g = out.getContext('2d');
    const u = w / GW, r = new KRandom(seed);
    const mood = moodColor == null ? 0xFF8F887E : (0xFF000000 | moodColor) >>> 0;

    g.fillStyle = lin(g, 0, 0, 0, h * .72, [wx.top, wx.mid, wx.bottom], [0, .55, 1]);
    g.fillRect(0, 0, w, h);

    const lx = (24 + r.nextFloat() * 52) * u;
    const ly = ((wx === SKY.CLEAR ? 30 : wx === SKY.PARTLY ? 36 : 40) + r.nextFloat() * 8) * u;
    if (wx === SKY.CLEAR || wx === SKY.PARTLY) {
      glow(g, lx, ly, 46 * u, wx.light, 0x66);
      glow(g, lx, ly, 20 * u, wx.light, 0x99);
      const gr = g.createRadialGradient(lx - 2 * u, ly - 2 * u, 0, lx - 2 * u, ly - 2 * u, 9 * u);
      gr.addColorStop(0, '#fff'); gr.addColorStop(1, css(wx.light));
      g.fillStyle = gr; disc(g, lx, ly, 8.5 * u);
    } else if (wx === SKY.CLOUDY || wx === SKY.FOG) {
      glow(g, lx, ly, 34 * u, wx.light, 0x55);
      blurred(g, 1.2 * u, css(withAlpha(wx.light, 150)), () => disc(g, lx, ly, 8 * u));
    } else glow(g, lx, ly, 40 * u, wx.light, 0x33);
    if (wx === SKY.CLEAR) birds(g, r, u, lx, ly);

    const clouds = { CLEAR: 0, PARTLY: 3, CLOUDY: 5, RAIN: 6, FOG: 2 }[wx.name];
    for (let i = 0; i < clouds; i++) {
      const cx = (r.nextFloat() * 110 - 5) * u;
      const cy = (14 + i * (wx === SKY.PARTLY ? 13 : 8) + r.nextFloat() * 6) * u;
      const size = (wx === SKY.PARTLY ? 1 : 1.25) * (.75 + r.nextFloat() * .5);
      cloud(g, r, u, cx, cy, size, wx === SKY.RAIN, i === clouds - 1);
    }
    if (wx === SKY.RAIN || wx === SKY.CLOUDY) {
      g.fillStyle = lin(g, 0, 0, 0, 34 * u, [wx === SKY.RAIN ? 0x80454D57 : 0x5566707A, 0x00000000]);
      g.fillRect(0, 0, w, 34 * u);
    }

    // ridges, far to near: each a little more of the mood colour, a little less of the sky
    const ridges = [[66, 5, .72], [76, 6, .52], [87, 7, .28], [99, 6, 0]];
    ridges.forEach(([base, amp, haze], i) => {
      const last = i === ridges.length - 1;
      const col = mix(mood, wx.bottom, haze);
      const top = mix(col, 0xFFFFFFFF, last ? .06 : .12);
      const bot = mix(col, 0xFF000000, last ? .22 : .08);
      g.fillStyle = lin(g, 0, (base - amp) * u, 0, GH * u, [top, bot]);
      g.fill(ridge(r, u, base, amp, i < 2));
      if (!last) {
        const my = (ridges[i + 1][0] - ridges[i + 1][1] - 2) * u;
        g.fillStyle = lin(g, 0, my - 7 * u, 0, my + 3 * u, [0x00FFFFFF, wx === SKY.FOG ? 0xCCF6F3EC : 0x55F6F1E8]);
        g.fillRect(0, my - 7 * u, w, 10 * u);
      }
      if (i === 2) tree(g, r, u, ridges[3][0] - 7, mix(mood, 0xFF000000, .3));
    });

    if (wx === SKY.RAIN) rain(g, r, u, w);
    if (wx === SKY.FOG) {
      for (let i = 0; i < 4; i++) {
        const y = (54 + i * 13 + r.nextFloat() * 4) * u;
        const hh = (5 + r.nextFloat() * 3) * u;
        blurred(g, 3 * u, css(withAlpha(0xF8F5EE, 120 - i * 18)), () => {
          g.beginPath(); roundRectPath(g, -10 * u, y, (GW + 20) * u, hh, 4 * u); g.fill();
        });
      }
    }

    caption(g, u, date, wx);
    grain(g, r, w, h);
    g.fillStyle = (() => {
      const gr = g.createRadialGradient(w / 2, h * .45, 0, w / 2, h * .45, h * .78);
      gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(.6, 'rgba(0,0,0,0)'); gr.addColorStop(1, css(0x33201812));
      return gr;
    })();
    g.fillRect(0, 0, w, h);
    return out;
  }

  return { draw };
})();
