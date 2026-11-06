'use strict';
// ui/CropScreen.kt: "Frame the memory". Move and pinch the photo under the stamp frame, in portrait, square or wide.

/** [photo] is a canvas; [replacing] the stamp being replaced or re-cropped; [place] the town picked in the add sheet. */
async function openCrop(date, photo, replacing, place) {
  const st = { shape: replacing ? await M.shapeOf(replacing) : 'portrait', filter: 'natural', scale: 0, ox: 0, oy: 0, bw: 0, bh: 0 };
  const view = photo; // shown as is: a CSS transform moves it, a CSS filter previews the look
  Object.assign(view.style, { position: 'absolute', left: 0, top: 0, right: 'auto', bottom: 'auto', transformOrigin: '0 0', visibility: 'hidden', width: photo.width + 'px', height: photo.height + 'px' });
  // drawn at the screen's pixel density, but always shown at the stage's own size
  const mask = h('canvas', { style: { position: 'absolute', left: 0, top: 0, width: '100%', height: '100%' } });
  const stage = h('div.stage', null, view, mask);
  const slider = h('input', { type: 'range', min: 0, max: 1000, value: 0, style: { flex: 1, marginInlineStart: '12px' } });
  const chipsShape = h('div.row', { style: { gap: '6px', padding: '0 16px 8px' } });
  const chipsFilter = h('div.hscroll', { style: { padding: '0 16px 14px' } });

  const pw = () => SR.size(st.shape)[0], ph = () => SR.size(st.shape)[1];
  const frame = () => {
    const fw = Math.min(st.bw * (pw() > ph() ? .86 : .8), st.bh * .8 * pw() / ph()), fh = fw * ph() / pw();
    return { x: (st.bw - fw) / 2, y: (st.bh - fh) / 2, w: fw, h: fh };
  };
  const minScale = f => Math.max(f.w / photo.width, f.h / photo.height);
  const fit = (v, lo, hi) => (lo <= hi ? clamp(v, lo, hi) : (lo + hi) / 2);
  const clampOff = (f, s, x, y) => [fit(x, f.x + f.w - photo.width * s, f.x), fit(y, f.y + f.h - photo.height * s, f.y)];

  const paint = () => {
    if (st.scale <= 0) return;
    view.style.visibility = 'visible';
    view.style.transform = `translate(${st.ox}px, ${st.oy}px) scale(${st.scale})`;
    view.style.filter = Filters.cssOf[st.filter];
    const dpr = window.devicePixelRatio || 1;
    if (mask.width !== Math.round(st.bw * dpr) || mask.height !== Math.round(st.bh * dpr)) { mask.width = Math.round(st.bw * dpr); mask.height = Math.round(st.bh * dpr); }
    const g = mask.getContext('2d'), f = frame();
    g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, st.bw, st.bh);
    g.fillStyle = 'rgba(0,0,0,.6)'; g.fillRect(0, 0, st.bw, st.bh); g.clearRect(f.x, f.y, f.w, f.h);
    // the edge it will get, so you can see what the perforation bites off
    const { paper, holes } = SR.shape(M.s.cut, 7, f.w, f.h);
    g.save(); g.translate(f.x, f.y); g.strokeStyle = '#fff'; g.lineWidth = 1.5; g.stroke(paper); g.stroke(holes); g.restore();
    const lo = minScale(f);
    slider.value = Math.round(clamp((st.scale / lo - 1) / 5, 0, 1) * 1000);
  };
  const chips = () => {
    chipsShape.replaceChildren(...SR.shapes.map(([k, n]) => h('button.cchip' + (k === st.shape ? '.on' : ''), { onclick: () => {
      st.shape = k; const f = frame(), s = Math.max(st.scale, minScale(f));
      [st.ox, st.oy] = clampOff(f, s, st.ox, st.oy); st.scale = s; chips(); paint();
    } }, n)));
    chipsFilter.replaceChildren(...Filters.all.map(([k, n]) => h('button.cchip' + (k === st.filter ? '.on' : ''), { onclick: () => { st.filter = k; chips(); paint(); } }, n)));
  };
  const zoomAt = (s, cx, cy) => {
    const f = frame(), lo = minScale(f);
    s = clamp(s, lo, lo * 6);
    const z = s / st.scale;
    [st.ox, st.oy] = clampOff(f, s, cx - (cx - st.ox) * z, cy - (cy - st.oy) * z);
    st.scale = s;
  };
  const layout = () => {
    const r = stage.getBoundingClientRect();
    if (!r.width || !r.height) return;
    const first = st.scale <= 0;
    st.bw = r.width; st.bh = r.height;
    const f = frame(), s = minScale(f);
    if (first) { st.scale = s; st.ox = st.bw / 2 - photo.width * s / 2; st.oy = st.bh / 2 - photo.height * s / 2; }
    else { st.scale = Math.max(st.scale, s); [st.ox, st.oy] = clampOff(f, st.scale, st.ox, st.oy); }
    paint();
  };

  // pinch and drag: two pointers zoom about their middle, one pans
  const pts = new Map();
  let last = null;
  const centre = () => { const p = [...pts.values()]; const x = p.reduce((a, q) => a + q.x, 0) / p.length, y = p.reduce((a, q) => a + q.y, 0) / p.length; return { x, y, d: p.length > 1 ? Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y) : 0 }; };
  const local = e => { const r = stage.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; };
  stage.addEventListener('pointerdown', e => { stage.setPointerCapture(e.pointerId); pts.set(e.pointerId, local(e)); last = centre(); });
  stage.addEventListener('pointermove', e => {
    if (!pts.has(e.pointerId) || st.scale <= 0) return;
    pts.set(e.pointerId, local(e));
    const c = centre();
    if (last && pts.size > 1 && last.d > 0 && c.d > 0) zoomAt(st.scale * c.d / last.d, c.x, c.y);
    if (last) { const f = frame(); [st.ox, st.oy] = clampOff(f, st.scale, st.ox + c.x - last.x, st.oy + c.y - last.y); }
    last = c; paint();
  });
  const lift = e => { pts.delete(e.pointerId); last = pts.size ? centre() : null; };
  stage.addEventListener('pointerup', lift); stage.addEventListener('pointercancel', lift);
  stage.addEventListener('wheel', e => { e.preventDefault(); const p = local(e); zoomAt(st.scale * Math.exp(-e.deltaY / 400), p.x, p.y); paint(); }, { passive: false });
  // Safari sends its own pinch events too; they would zoom the whole page
  ['gesturestart', 'gesturechange'].forEach(t => stage.addEventListener(t, e => e.preventDefault()));
  slider.addEventListener('input', () => {
    const f = frame(), lo = minScale(f);
    zoomAt(lo * (1 + 5 * slider.value / 1000), f.x + f.w / 2, f.y + f.h / 2); paint();
  });

  let doing = false;
  const done = async () => {
    if (st.scale <= 0) layout();
    if (st.scale <= 0 || doing) return;
    doing = true;
    const f = frame(), s = st.scale;
    const left = clamp(Math.round((f.x - st.ox) / s), 0, photo.width - 1), top = clamp(Math.round((f.y - st.oy) / s), 0, photo.height - 1);
    const w = clamp(Math.round(f.w / s), 1, photo.width - left), hh = clamp(Math.round(f.h / s), 1, photo.height - top);
    const out = canvas(pw(), ph()), g = out.getContext('2d');
    g.imageSmoothingQuality = 'high';
    g.drawImage(photo, left, top, w, hh, 0, 0, out.width, out.height);
    Filters.apply(out, st.filter);
    close();
    await M.addStamp(date, out, replacing, photo, place);
    drop(out); drop(photo);
  };
  const close = () => { ro.disconnect(); Layers.close('crop'); };

  Layers.open('crop', () => [
    h('div.top', null,
      h('button', { onclick: () => { close(); drop(photo); } }, tr('Cancel')),
      h('div.grow.c', null,
        h('div', { style: { fontSize: '12.5px', color: 'rgba(255,255,255,.7)' } }, tr('Drag · pinch to resize')),
        h('div.serif', { style: { fontSize: '18px' } }, tr('Frame the memory'))),
      h('button.b', { style: { color: '#F2C9A0' }, onclick: done }, tr('Done'))),
    stage,
    h('div.row', { style: { padding: '8px 20px' } }, h('span.b', { style: { fontSize: '13.5px', color: 'rgba(255,255,255,.7)' } }, tr('Size')), slider),
    chipsShape, chipsFilter,
  ], { className: 'full crop', fixed: true });
  const ro = new ResizeObserver(layout);
  ro.observe(stage);
  // don't wait on the observer alone: lay it out as soon as the screen has a size
  const settle = () => { if (!Layers.has('crop')) return; layout(); if (st.scale <= 0) requestAnimationFrame(settle); };
  settle(); setTimeout(layout, 100);
  chips();
}
