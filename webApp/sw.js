/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

const CACHE = 'ecosphere-web-v1.6.10-pairing-guidance';
const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './android-auth-return.js',
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
const CACHEABLE_URLS = new Set(ASSETS.map(asset => new URL(asset, self.location.href).href));

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    Promise.all([
      caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key)))),
      self.clients.claim(),
    ])
  );
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin || event.request.method !== 'GET') return;
  event.respondWith(
    fetch(event.request)
      .then(async response => {
        if (response.ok && response.type === 'basic' && !url.search && CACHEABLE_URLS.has(url.href)) {
          const copy = response.clone();
          const cache = await caches.open(CACHE);
          await cache.put(event.request, copy);
        }
        return response;
      })
      .catch(async () => {
        const cached = await caches.match(event.request);
        if (cached) return cached;
        if (event.request.mode === 'navigate') return caches.match('./index.html');
        return Response.error();
      })
  );
});
