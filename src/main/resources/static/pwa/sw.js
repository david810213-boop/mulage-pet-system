// 慕沐村員工手機版 service worker（需求，2026-09-24）
// 刻意不快取任何會員、預約等業務資料頁面：這些頁面每次都要拿伺服器最新資料，
// 避免員工看到過時的預約狀態。唯一快取的是「沒有網路」提示頁，
// 讓手機斷線時畫面不會變成瀏覽器預設的錯誤頁。
const CACHE = "mulage-staff-v1";
const OFFLINE_URL = "/pwa/offline.html";

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll([OFFLINE_URL, "/pwa/icon-192.png"]))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.mode !== "navigate") {
    return;
  }
  event.respondWith(
    fetch(event.request).catch(() => caches.match(OFFLINE_URL))
  );
});
