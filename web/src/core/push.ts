// Whether this phone can show Spoil Not's notifications, and asking for them (DESIGN 5.4, 18.4). Browser APIs
// only; the push token is the data layer's (FcmPushIdentity).
import { DeepLinks } from './deepLinks';
import { str } from './i18n';
import { isIos } from './install';

/**
 * The system's answer (granted, denied, or not asked yet), `install` on an iPhone outside a Home Screen web app
 * (Safari shows web notifications only there), or `unsupported` in a browser without web push.
 */
export type PushStatus = NotificationPermission | 'install' | 'unsupported';

export function pushStatus(): PushStatus {
  if (typeof Notification === 'undefined' || !('serviceWorker' in navigator) || !('PushManager' in window)) {
    return isIos() ? 'install' : 'unsupported';
  }
  return Notification.permission;
}

/** Sent on `window` when this app asked the system, so every screen that shows the status reads it again. */
export const PUSH_STATUS_EVENT = 'spoilnot-push-status';

/** Asks the system; iOS shows its question only from a tap. */
export async function requestPushPermission(): Promise<PushStatus> {
  if (pushStatus() === 'default') {
    try {
      await Notification.requestPermission();
    } catch {
      // Older Safari takes a callback; the status below tells what happened.
    }
    window.dispatchEvent(new Event(PUSH_STATUS_EVENT));
  }
  return pushStatus();
}

/** Checks the permission with a local notification that opens like a real digest (Notifications.sendTest). */
export const showTestNotification = (): Promise<boolean> =>
  showLocalNotification(str('test_notification_title'), str('test_notification_body'), 'test');

/**
 * A notification from this phone, not from the reminder job, that opens like a real digest: Send test notification
 * and "You're set" (DESIGN 5.4). It goes through the service worker, which is how an installed iPhone web app may show
 * one; false when notifications aren't allowed or it could not be shown.
 */
export async function showLocalNotification(title: string, body: string, tag: string): Promise<boolean> {
  if (pushStatus() !== 'granted') return false;
  const options: NotificationOptions = {
    body,
    tag,
    icon: '/icons/icon-192.png',
    data: { route: DeepLinks.PANTRY_EXPIRING },
  };
  try {
    const registration = await navigator.serviceWorker.getRegistration();
    if (registration) await registration.showNotification(title, options);
    else new Notification(title, options); // development builds have no service worker
    return true;
  } catch {
    return false;
  }
}
