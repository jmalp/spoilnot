// Whether this phone can show Spoil Not's notifications, and asking for them (DESIGN 5.4, 18.4). Browser APIs
// only; the push token is the data layer's (FcmDeviceRegistry).
import { DeepLinks } from './deepLinks';
import { str } from './i18n';

/**
 * The system's answer (granted, denied, or not asked yet), `install` on an iPhone outside a Home Screen web app
 * (Safari shows web notifications only there), or `unsupported` in a browser without web push.
 */
export type PushStatus = NotificationPermission | 'install' | 'unsupported';

const isIos = () =>
  /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

export function pushStatus(): PushStatus {
  if (typeof Notification === 'undefined' || !('serviceWorker' in navigator) || !('PushManager' in window)) {
    return isIos() ? 'install' : 'unsupported';
  }
  return Notification.permission;
}

/** Asks the system; iOS shows its question only from a tap. */
export async function requestPushPermission(): Promise<PushStatus> {
  if (pushStatus() === 'default') {
    try {
      await Notification.requestPermission();
    } catch {
      // Older Safari takes a callback; the status below tells what happened.
    }
  }
  return pushStatus();
}

/** Checks the permission with a local notification that opens like a real digest (Notifications.sendTest). */
export async function showTestNotification(): Promise<boolean> {
  if (pushStatus() !== 'granted') return false;
  const title = str('test_notification_title');
  const options: NotificationOptions = {
    body: str('test_notification_body'),
    tag: 'test',
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
