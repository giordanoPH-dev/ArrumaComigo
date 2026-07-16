const CACHE = 'ac-v8'; // bump a cada deploy
const SHELL = [
  './', './index.html', './style.css', './manifest.json',
  './icon-192.png', './icon-512.png', './apple-touch-icon.png',
  './fonts/fredoka.ttf', './fonts/nunito.ttf',
  './js/app.js', './js/api.js', './js/domain.js', './js/recurrence.js',
  './js/views/login.js', './js/views/today.js', './js/views/rooms.js',
  './js/views/people.js', './js/views/balance.js', './js/views/scenarios.js',
  './js/views/family.js',
];

// Assume o controle imediatamente (sem esperar todas as abas fecharem).
self.addEventListener('install', (e) => {
  self.skipWaiting();
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)));
});

self.addEventListener('activate', (e) => e.waitUntil((async () => {
  const keys = await caches.keys();
  await Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)));
  await self.clients.claim();
})()));

// Network-first no app shell: online mostra sempre a versão publicada; offline cai no cache.
// Dados do Supabase nunca passam pelo cache.
self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || req.url.includes('supabase')) return;
  e.respondWith((async () => {
    try {
      const fresh = await fetch(req);
      const cache = await caches.open(CACHE);
      cache.put(req, fresh.clone());
      return fresh;
    } catch {
      const cached = await caches.match(req);
      if (cached) return cached;
      throw new Error('offline e sem cache');
    }
  })());
});
