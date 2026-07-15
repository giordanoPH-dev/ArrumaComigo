const CACHE = 'ac-v4'; // bump a cada deploy
const SHELL = [
  './', './index.html', './style.css', './manifest.json', './icon-192.png', './icon-512.png',
  './fonts/fredoka.ttf', './fonts/nunito.ttf',
  './js/app.js', './js/api.js', './js/domain.js', './js/recurrence.js',
  './js/views/login.js', './js/views/today.js', './js/views/rooms.js',
  './js/views/people.js', './js/views/balance.js', './js/views/scenarios.js',
  './js/views/family.js',
];

self.addEventListener('install', (e) =>
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL))));

self.addEventListener('activate', (e) =>
  e.waitUntil(caches.keys().then((keys) =>
    Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))));

self.addEventListener('fetch', (e) => {
  if (e.request.url.includes('supabase')) return; // dados sempre da rede
  e.respondWith(caches.match(e.request).then((hit) => hit || fetch(e.request)));
});
