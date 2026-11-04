'use strict';
// ui/Viewer.kt: stamps full screen, like a gallery. Swipe or use the arrows; tap a stamp to turn it over.

/** The day's stamps, starting at [s]. On the day page itself there's nothing to edit, but they can be reordered. */
function viewDay(date, s) {
  const items = M.day(date).stamps.map(x => [date, x]);
  openViewer(items, Math.max(0, items.findIndex(e => e[1].id === s.id)), { canReorder: true });
}

/** [items] are [date, stamp] pairs; [inside] names the collection you came from, so its cover can be set from here. */
function openViewer(items, start, { inside = null, onEdit = null, canReorder = false, onClose = null } = {}) {
  const st = { index: clamp(start, 0, Math.max(0, items.length - 1)), back: false };
  if (!items.length) return;
  let drag = 0, x0 = null;

  Layers.open('viewer', () => {
    const [date, first] = items[st.index];
    const day = M.day(date);
    const stamp = day.stamps.find(s => s.id === first.id) || first;
    const width = Math.min(innerWidth, 560) * .78, ww = Math.min(width, Math.max(160, (innerHeight - 330) / 1.25));
    const go = d => { st.index = clamp(st.index + d, 0, items.length - 1); st.back = false; layer().refresh(); };
    const pos = items.length > 1 ? `${st.index + 1} of ${items.length}` : st.back ? 'tap to turn back' : 'tap the stamp to turn it over';
    const makeFirst = canReorder && day.stamps[0] && day.stamps[0].id !== stamp.id;

    const flip = h('div.flip' + (st.back ? '.over' : ''), { onclick: () => { st.back = !st.back; flip.classList.toggle('over', st.back); } },
      h('div.face', null, stampView(stamp.id, D.doy(date), day.mood, ww)),
      backOf(date, ww));
    const stage = h('div.stage', null, flip,
      st.index > 0 && h('button.arrow', { style: { left: 0 }, onclick: () => go(-1) }, '‹'),
      st.index < items.length - 1 && h('button.arrow', { style: { right: 0 }, onclick: () => go(1) }, '›'));
    stage.addEventListener('pointerdown', e => { x0 = e.clientX; drag = 0; });
    stage.addEventListener('pointermove', e => { if (x0 == null) return; drag = e.clientX - x0; flip.style.translate = `${drag * .4}px 0`; });
    const end = () => {
      if (x0 == null) return;
      x0 = null; flip.style.translate = '';
      if (drag > 80 && st.index > 0) go(-1); else if (drag < -80 && st.index < items.length - 1) go(1);
      else if (Math.abs(drag) > 8) flip.addEventListener('click', e => e.stopImmediatePropagation(), { capture: true, once: true });
    };
    stage.addEventListener('pointerup', end); stage.addEventListener('pointercancel', end);

    return [
      h('div.top', null,
        h('button', { onclick: () => Layers.close('viewer') }, '‹ Back'),
        h('div.grow'),
        h('span.small', { style: { color: 'rgba(255,255,255,.6)' } }, pos),
        makeFirst && h('button', { onclick: () => { M.moveStamp(date, stamp, 0); items = M.day(date).stamps.map(x => [date, x]); st.index = 0; layer().refresh(); } }, '★ Make first'),
        onEdit && h('button', { onclick: () => { onEdit(date); Layers.close('viewer'); } }, 'Edit ✎')),
      stage,
      h('div.serif.c', { style: { fontSize: '22px', paddingTop: '8px' } }, fmt(date, 'EEEE d MMMM yyyy')),
      h('div.small.c', { style: { color: 'rgba(255,255,255,.7)', marginTop: '2px' } },
        [day.mood != null ? Moods.names[day.mood] : null, M.placeOf(date, stamp), day.sealed ? null : 'not sealed yet'].filter(Boolean).join(' · ')),
      day.line.trim() && !st.back && h('div.it.c', { style: { fontSize: '17px', lineHeight: '23px', padding: '10px 28px 0', display: '-webkit-box', WebkitLineClamp: '4', WebkitBoxOrient: 'vertical', overflow: 'hidden' } }, `“${day.line}”`),
      themeChips(stamp, inside),
    ];
  }, { className: 'full viewer', onClose });
  const layer = () => Layers.list.find(l => l.key === 'viewer');
}

/** Chips under the stamp: one per theme (tap to add or remove), plus "Set as cover" inside a collection. */
function themeChips(stamp, inside) {
  const themes = M.collections.filter(c => c.type === 'THEME');
  const current = inside && M.collections.find(c => c.id === inside.id);
  const row = h('div.hscroll', { style: { padding: '14px 16px', justifyContent: 'safe center' } });
  if (current && M.entries(current).some(([, ss]) => ss.some(s => s.id === stamp.id))) {
    const cv = M.cover(current), isCover = cv && cv[1].id === stamp.id;
    row.append(h('button.dchip' + (isCover ? '.on' : ''), { onclick: () => { if (!isCover) M.setCover(current, stamp.id); } }, isCover ? `★ Cover of ${current.name}` : '☆ Set as cover'));
  }
  themes.forEach(t => {
    const inIt = t.ids.includes(stamp.id);
    row.append(h('button.dchip' + (inIt ? '.on' : ''), { onclick: () => M.toggleInTheme(t, stamp.id) }, (inIt ? '✓ ' : '＋ ') + t.name));
  });
  if (!themes.length) row.append(h('span.small', { style: { color: 'rgba(255,255,255,.5)' } }, 'Make a theme in Collections to file stamps like this one.'));
  return row;
}

/** The back of the stamp: the day it belongs to, its one line, and how it felt. Edited on the day's page, not here. */
function backOf(date, width) {
  const day = M.day(date);
  const at = day.sealed && day.sealedAt > 0 ? fmtStamp(day.sealedAt) : null;
  const bits = postmarkBits(day);
  const why = [day.feels.join(', '), day.factors.length ? 'because of ' + day.factors.join(', ') : ''].filter(Boolean).join(' · ');
  return h('div.backface', { style: { width, height: width * 1.25 } },
    h('div.kick', null, `№${D.doy(date)} · ${fmt(date, 'EEEE d MMMM yyyy')}`),
    h('div.small', { style: { marginTop: '4px' } }, at ? `Sealed ${at} · ${prettyOffice(day.office)}` : 'Not sealed yet'),
    h('div.it.grow', { style: { fontSize: '20px', lineHeight: '27px', marginTop: '18px', color: day.line.trim() ? 'var(--ink)' : 'var(--muted)', overflow: 'hidden' } },
      day.line.trim() ? `“${day.line}”` : 'No line written for this day.'),
    h('div.row', null,
      day.mood != null && h('i', { style: { width: '12px', height: '12px', borderRadius: '50%', background: Moods.css(day.mood), marginRight: '8px' } }),
      h('span.body.b', null, day.mood != null ? Moods.names[day.mood] : 'No mood set')),
    why && h('div.small', { style: { marginTop: '4px', display: '-webkit-box', WebkitLineClamp: '3', WebkitBoxOrient: 'vertical', overflow: 'hidden' } }, why),
    bits.length > 0 && h('div', { style: { borderTop: '1px solid var(--line)', marginTop: '12px', paddingTop: '10px', fontSize: '14px' } }, bits.join('  ·  ')));
}
