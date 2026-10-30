// What the service worker does with a push and a tap on its notification (DESIGN 18.4): pure, so it is unit tested
// without a browser, and free of DOM types so the service worker build can use it.
import { OPEN_ROUTE_MESSAGE } from './deepLinks';

/** An FCM message as the push carries it: the job's notification (webpush overrides) and data. */
type PushPayload = { notification?: Record<string, unknown>; data?: Record<string, unknown> };

const text = (value: unknown): string | undefined => (typeof value === 'string' && value !== '' ? value : undefined);

/** Only a path on this site, so a push can never send the app elsewhere. */
export function safeRoute(route: unknown): string {
  return typeof route === 'string' && route.startsWith('/') && !route.startsWith('//') && !route.includes('\\')
    ? route
    : '/';
}

/** The notification for a push: the digest's title and body, its tag (a newer digest replaces the older one). */
export function notificationFor(payload: unknown): { title: string; options: NotificationOptions } {
  const { notification = {}, data = {} } = (payload && typeof payload === 'object' ? payload : {}) as PushPayload;
  return {
    title: text(notification.title) ?? 'Spoil Not',
    options: {
      body: text(notification.body) ?? '',
      tag: text(notification.tag) ?? 'expiry_digest',
      icon: text(notification.icon) ?? '/icons/icon-192.png',
      data: { route: safeRoute(data.route) },
    },
  };
}

type WindowLike = { url: string; postMessage(message: unknown): void; focus(): Promise<unknown> };
type ClientsLike = {
  matchAll(options: { type: 'window'; includeUncontrolled: boolean }): Promise<readonly WindowLike[]>;
  openWindow(url: string): Promise<unknown>;
};

/** A tap: an open window of the app goes to [route] (without reloading), otherwise the app opens there. */
export async function openRoute(clients: ClientsLike, route: string, origin: string): Promise<void> {
  const windows = await clients.matchAll({ type: 'window', includeUncontrolled: true });
  const open = windows.find((w) => new URL(w.url).origin === origin);
  if (!open) {
    await clients.openWindow(route);
    return;
  }
  open.postMessage({ type: OPEN_ROUTE_MESSAGE, route });
  await open.focus().catch(() => undefined);
}
