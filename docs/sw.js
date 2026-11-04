// Keeps Annales opening without a connection. The app files are cached; the weather is always fetched fresh.
const CACHE = 'annales-1.0.1';
const FILES = ['./', 'index.html', 'style.css', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png',
  ...['ui', 'kt', 'data', 'render', 'sky', 'weather', 'store', 'today', 'crop', 'viewer', 'journal', 'collections', 'insights-ui', 'profile', 'app'].map(f => `js/${f}.js`)];

self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
// the app's own files: always asked from the network (no-cache skips the browser's 10-minute copy), the cache only offline
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  e.respondWith(fetch(e.request, { cache: 'no-cache' }).then(r => {
    if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); }
    return r;
  }).catch(() => caches.match(e.request, { ignoreSearch: true })));
});
