// Offline shell: the app's own files are fetched network-first (so a deploy shows up on the next
// open) with the cache as fallback; the pinned CDN libraries never change, so they are cache-first.
// Firestore and Auth traffic is never touched here; the Firestore SDK keeps its own offline cache.
const CACHE = "flatstock-web-v0.2.0";
const SHELL = [
  "./", "index.html", "manifest.webmanifest", "css/app.css",
  "js/app.js", "js/db.js", "js/i18n.js", "js/ledger.js", "js/scanner.js", "js/off.js", "js/firebase-config.js",
  "icons/icon-192.png", "icons/icon-512.png", "icons/apple-touch-icon.png",
];
const PINNED_HOSTS = ["www.gstatic.com", "cdn.jsdelivr.net"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);

  if (url.origin === location.origin) {
    // Firebase Hosting's reserved /__/ URLs (auth handler, SDK config) always go to the network.
    if (url.pathname.startsWith("/__/")) return;
    e.respondWith(
      fetch(req)
        .then((res) => {
          if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(req, copy)); }
          return res;
        })
        .catch(() => caches.match(req).then((hit) => hit ?? caches.match("index.html"))),
    );
    return;
  }

  if (PINNED_HOSTS.includes(url.hostname) && /\/(firebasejs\/\d|npm\/(@[^/]+\/)?[^/@]+@\d)/.test(url.pathname)) {
    e.respondWith(
      caches.match(req).then((hit) => hit ?? fetch(req).then((res) => {
        if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(req, copy)); }
        return res;
      })),
    );
  }
});
