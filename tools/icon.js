// Draws the Annales icon as an SVG string. Used by icon.html (which tools/icon.sh renders) and by any page that needs the icon.
// mode: round (rounded corners), full (square, stamp in the safe zone), bg (envelope only), fg (stamp only, transparent).
function annalesIcon(mode = 'round') {
const RING = '#26252a', WAVE = '#9b2f35';   // official black datestamp, carmine machine cancel

// a perforated stamp outline: half-circle bites along every edge
function stampPath(x, y, w, h, r, p) {
  let d = `M${x} ${y}`;
  const nx = Math.round(w / p), ny = Math.round(h / p), sx = w / nx, sy = h / ny;
  for (let i = 0; i < nx; i++) { const cx = x + sx * (i + .5); d += ` L${cx - r} ${y} A${r} ${r} 0 0 0 ${cx + r} ${y}`; } d += ` L${x + w} ${y}`;
  for (let i = 0; i < ny; i++) { const cy = y + sy * (i + .5); d += ` L${x + w} ${cy - r} A${r} ${r} 0 0 0 ${x + w} ${cy + r}`; } d += ` L${x + w} ${y + h}`;
  for (let i = nx - 1; i >= 0; i--) { const cx = x + sx * (i + .5); d += ` L${cx + r} ${y + h} A${r} ${r} 0 0 0 ${cx - r} ${y + h}`; } d += ` L${x} ${y + h}`;
  for (let i = ny - 1; i >= 0; i--) { const cy = y + sy * (i + .5); d += ` L${x} ${cy + r} A${r} ${r} 0 0 0 ${x} ${cy - r}`; }
  return d + 'Z';
}
const STAMP = stampPath(300, 250, 380, 470, 20, 62), SHADOW = stampPath(300, 272, 380, 470, 20, 62);
const fox = 'rgba(70,35,10,.5)';
const waves = [0, 1, 2].map(i => `<path d="M190 ${300 + i * 42} q31 -26 62 0 t62 0 t62 0 t62 0"/>`).join('');

// the envelope: dusk-to-kraft gradient, deep creases, fine crinkles, fibre, foxed corners and the folded flap
const paper = `
  <rect width="1024" height="1024" fill="url(#sk)"/><rect width="1024" height="1024" fill="url(#gw)"/>
  <rect width="1024" height="1024" filter="url(#cr)" style="mix-blend-mode:soft-light"/>
  <rect width="1024" height="1024" fill="url(#f1)" style="mix-blend-mode:multiply"/><rect width="1024" height="1024" fill="url(#f2)" style="mix-blend-mode:multiply"/>
  <rect width="1024" height="1024" filter="url(#fb)"/>
  <rect width="1024" height="1024" filter="url(#c2)" style="mix-blend-mode:soft-light" opacity=".85"/>
  <rect width="1024" height="1024" filter="url(#c3)"/>
  <path d="M0 0 L512 410 L1024 0" fill="none" stroke="#000" stroke-opacity=".18" stroke-width="10"/><path d="M0 6 L512 416 L1024 6" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="5"/>
  <path d="M0 0 L512 410 L1024 0 Z" fill="#fff" fill-opacity=".05"/><path d="M0 1024 L430 560 M1024 1024 L594 560" stroke="#000" stroke-opacity=".08" stroke-width="6"/>`;

// the stamp, slightly larger and centred, with its two cancels pressed in uneven ink
const art = `
  <circle cx="512" cy="560" r="300" fill="#ffcf9a" opacity=".3" filter="url(#b)"/>
  <g transform="translate(512 512) scale(1.13) translate(-490 -485)">
    <g transform="rotate(-6 490 485)">
      <path d="${SHADOW}" fill="#1a1030" opacity=".3" filter="url(#s)"/>
      <path d="${STAMP}" fill="url(#gl)"/>
      <path d="${STAMP}" fill="none" stroke="url(#ed)" stroke-width="5"/>
      <rect x="344" y="294" width="292" height="382" rx="18" fill="#fff" fill-opacity=".1" stroke="#fff" stroke-opacity=".45" stroke-width="3"/>
      <g clip-path="url(#in)"><circle cx="490" cy="540" r="120" fill="#ffd2a8" opacity=".35" filter="url(#s)"/><circle cx="490" cy="540" r="70" fill="#ffe2bd"/>
        <path d="M330 600 Q490 520 650 600 V700 H330Z" fill="#fff" fill-opacity=".38"/><path d="M330 600 Q490 520 650 600" fill="none" stroke="#fff" stroke-opacity=".85" stroke-width="7"/></g>
    </g>
    <g filter="url(#ink)" opacity=".9">
      <g transform="rotate(-14 720 640)" fill="none" stroke="${RING}"><circle cx="720" cy="640" r="130" stroke-width="13"/><circle cx="720" cy="640" r="94" stroke-width="8"/></g>
      <g transform="rotate(-16 300 330)" fill="none" stroke="${WAVE}" stroke-width="14" stroke-linecap="round">${waves}</g>
    </g>
  </g>`;

// full, bg and fg are adaptive-icon squares: the paper fills them, the stamp shrinks into the 66 dp safe zone
const fit = mode === 'round' ? '' : 'transform="translate(512 512) scale(.64) translate(-512 -512)"';
const showPaper = mode !== 'fg', showArt = mode !== 'bg';

return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
<defs>
  <linearGradient id="sk" x1="0" y1="0" x2=".25" y2="1"><stop offset="0" stop-color="#3a2c4e"/><stop offset=".333" stop-color="#7a4a5a"/><stop offset=".667" stop-color="#b77a58"/><stop offset="1" stop-color="#d9a877"/></linearGradient>
  <radialGradient id="gw" cx=".5" cy="1.05" r=".75"><stop offset="0" stop-color="#ffd6a0" stop-opacity=".7"/><stop offset="1" stop-color="#ffd6a0" stop-opacity="0"/></radialGradient>
  <filter id="cr" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".0045 .007" numOctaves="5" seed="23"/>
    <feDiffuseLighting lighting-color="#fff" surfaceScale="8" diffuseConstant="1.1"><feDistantLight azimuth="235" elevation="42"/></feDiffuseLighting></filter>
  <filter id="c2" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".018 .026" numOctaves="4" seed="30"/>
    <feDiffuseLighting lighting-color="#fff" surfaceScale="3.4" diffuseConstant="1.05"><feDistantLight azimuth="215" elevation="48"/></feDiffuseLighting></filter>
  <filter id="fb" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".75" numOctaves="3" seed="4"/><feColorMatrix values="0 0 0 0 .25  0 0 0 0 .15  0 0 0 0 .05  0 0 0 .18 0"/></filter>
  <filter id="c3" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".09" numOctaves="2" seed="3"/><feColorMatrix values="0 0 0 0 .3  0 0 0 0 .18  0 0 0 0 .06  0 0 0 .16 0"/></filter>
  <radialGradient id="f1" cx="0" cy="0" r=".65"><stop offset="0" stop-color="${fox}"/><stop offset="1" stop-color="${fox}" stop-opacity="0"/></radialGradient>
  <radialGradient id="f2" cx="1" cy="1" r=".55"><stop offset="0" stop-color="${fox}"/><stop offset="1" stop-color="${fox}" stop-opacity="0"/></radialGradient>
  <filter id="b" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="36"/></filter>
  <filter id="s" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="14"/></filter>
  <linearGradient id="gl" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".58"/><stop offset=".55" stop-color="#fff" stop-opacity=".2"/><stop offset="1" stop-color="#fff" stop-opacity=".1"/></linearGradient>
  <linearGradient id="ed" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".95"/><stop offset=".5" stop-color="#fff" stop-opacity=".25"/><stop offset="1" stop-color="#fff" stop-opacity=".6"/></linearGradient>
  <filter id="ink" x="-10%" y="-10%" width="120%" height="120%"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="5" result="g"/>
    <feDisplacementMap in="SourceGraphic" in2="g" scale="7" xChannelSelector="R" yChannelSelector="G" result="d"/>
    <feTurbulence type="fractalNoise" baseFrequency=".03" numOctaves="2" seed="9" result="p"/>
    <feColorMatrix in="p" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -2.6 2.35" result="pa"/><feComposite in="d" in2="pa" operator="in"/></filter>
  <clipPath id="in"><rect x="344" y="294" width="292" height="382" rx="18"/></clipPath>
  <clipPath id="clip"><rect width="1024" height="1024" rx="${mode === 'round' ? 228 : 0}"/></clipPath>
</defs>
<g clip-path="url(#clip)">
  ${showPaper ? paper : ''}
  ${showArt ? `<g ${fit}>${art}</g>` : ''}
</g>
</svg>`;
}
