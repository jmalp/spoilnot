/// <reference lib="webworker" />
// The service worker (DESIGN 18.5): caches the app shell so the app opens offline, and switches to a new version
// when the app asks (Update). Firestore keeps its own offline copy of the data.
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';
import { notificationFor, openRoute, safeRoute } from './core/pushMessages';

declare const self: ServiceWorkerGlobalScope & { __WB_MANIFEST: (string | { url: string; revision: string | null })[] };

cleanupOutdatedCaches();
precacheAndRoute(self.__WB_MANIFEST);
registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html')));

self.addEventListener('message', (event) => {
  if ((event.data as { type?: string } | null)?.type === 'SKIP_WAITING') void self.skipWaiting();
});

// Every push shows a notification: iOS ends the subscription of a web app that receives pushes silently.
self.addEventListener('push', (event) => {
  let payload: unknown = null;
  try {
    payload = event.data?.json();
  } catch {
    // Not JSON: the notification still shows, with the app's name.
  }
  const { title, options } = notificationFor(payload);
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const route = safeRoute((event.notification.data as { route?: unknown } | null)?.route);
  event.waitUntil(openRoute(self.clients, route, self.location.origin));
});
