const CACHE = 'ecosphere-web-v1.5.0-oauth-icons';
const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.webmanifest',
  './icon.svg',
  './icon-192.png',
  './icon-512.png',
  './icons/ic_air_humidity.svg',
  './icons/ic_android.svg',
  './icons/ic_auto_mode.svg',
  './icons/ic_dashboard.svg',
  './icons/ic_diagnostics.svg',
  './icons/ic_error.svg',
  './icons/ic_esp32.svg',
  './icons/ic_fan.svg',
  './icons/ic_github.svg',
  './icons/ic_google.svg',
  './icons/ic_grow_led.svg',
  './icons/ic_history.svg',
  './icons/ic_info.svg',
  './icons/ic_light.svg',
  './icons/ic_linux.svg',
  './icons/ic_manual_mode.svg',
  './icons/ic_offline.svg',
  './icons/ic_ok.svg',
  './icons/ic_online.svg',
  './icons/ic_pump.svg',
  './icons/ic_refresh_animated.svg',
  './icons/ic_soil_humidity.svg',
  './icons/ic_temperature.svg',
  './icons/ic_warning.svg',
  './icons/ic_water_level.svg',
  './icons/ic_windows.svg'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))
  );
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  event.respondWith(
    fetch(event.request)
      .then(response => {
        const copy = response.clone();
        caches.open(CACHE).then(cache => cache.put(event.request, copy));
        return response;
      })
      .catch(() => caches.match(event.request).then(cached => cached || caches.match('./index.html')))
  );
});
