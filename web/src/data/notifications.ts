// Reminder settings and this phone's push registration (DESIGN 10.2, 18.4), ported from
// FirestoreNotificationPrefsRepository and FirebaseDeviceRegistry.
import {
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  writeBatch,
  type DocumentReference,
} from 'firebase/firestore';
import { distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import type { DeviceRegistry } from '../domain/household';
import {
  NotificationRules,
  type NotificationPrefs,
  type NotificationPrefsRepository,
  type PushIdentity,
  type StoredFlag,
} from '../domain/notifications';
import type { FirebaseSdk } from './firebase';
import { commitQueued, docSnapshots, mapped } from './firestore-rx';
import { UserFields } from './users';

export class FirestoreNotificationPrefsRepository implements NotificationPrefsRepository {
  constructor(private readonly sdk: FirebaseSdk) {}

  observe(uid: string): Observable<NotificationPrefs> {
    return this.sdk.instance$.pipe(
      switchMap((db) => docSnapshots(doc(db, UserFields.path(uid)))),
      map((snapshot) => NotificationRules.read(snapshot.get('notificationPrefs'))),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }

  async save(uid: string, prefs: NotificationPrefs): Promise<void> {
    // Applied to the cache at once; syncs when online (DESIGN 9.5).
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.update(doc(db, UserFields.path(uid)), {
      notificationPrefs: {
        enabled: prefs.enabled,
        digestMinutes: prefs.digestMinutes,
        stages: NotificationRules.stages(prefs.stages),
        expiredMode: prefs.expiredMode,
        cooking: prefs.cooking,
      },
      [UserFields.UPDATED_AT]: serverTimestamp(),
    });
    await commitQueued(batch);
  }
}

const INSTALL_ID_KEY = 'spoilnot.installId';
/** Set while this browser still has a push token to delete (DeviceSync); outside Firestore and the sync log. */
export const TOKEN_TO_DELETE_KEY = 'spoilnot.push.tokenToDelete';
const UNREGISTER_TIMEOUT_MS = 3_000;

/** A flag in localStorage; a browser that refuses storage reads it as unset. */
export function storedFlag(key: string): StoredFlag {
  return {
    get: () => {
      try {
        return localStorage.getItem(key) === '1';
      } catch {
        return false;
      }
    },
    set: (on) => {
      try {
        if (on) localStorage.setItem(key, '1');
        else localStorage.removeItem(key);
      } catch {
        // Private browsing without storage: nothing to keep.
      }
    },
  };
}

/**
 * This browser's push identity (FirebasePushIdentity in the app): a random id kept in localStorage, like Firebase
 * Installations' id in the app, and the FCM web token. A token needs the permission, the Push API (an iPhone web app
 * on the Home Screen, iOS 16.4 or later), the service worker and the build's VAPID key; without them there is none,
 * and `firebase/messaging` is never loaded.
 */
export class FcmPushIdentity implements PushIdentity {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly vapidKey: string | undefined = import.meta.env.VITE_FIREBASE_VAPID_KEY,
  ) {}

  installId(): string {
    try {
      const known = localStorage.getItem(INSTALL_ID_KEY);
      if (known) return known;
      const id = crypto.randomUUID();
      localStorage.setItem(INSTALL_ID_KEY, id);
      return id;
    } catch {
      return crypto.randomUUID();
    }
  }

  async token(): Promise<string | null> {
    const fcm = await this.messaging();
    return fcm ? fcm.getToken(fcm.messaging, fcm.options) : null;
  }

  async deleteToken(): Promise<void> {
    const fcm = await this.messaging();
    if (!fcm) return; // no permission, worker or key here: no token to delete
    // deleteToken uses the service worker getToken was given; without one it would register FCM's default worker
    // in place of the app's. getToken returns the stored token without a request while it is fresh.
    await fcm.getToken(fcm.messaging, fcm.options);
    await fcm.deleteToken(fcm.messaging);
  }

  private async messaging() {
    if (!this.vapidKey || typeof Notification === 'undefined' || Notification.permission !== 'granted') return null;
    const registration = await navigator.serviceWorker?.getRegistration();
    if (!registration) return null; // development builds have no service worker
    // Loaded only here: most visits never need it.
    const { deleteToken, getMessaging, getToken, isSupported } = await import('firebase/messaging');
    if (!(await isSupported())) return null;
    return {
      messaging: getMessaging(this.sdk.app),
      options: { vapidKey: this.vapidKey, serviceWorkerRegistration: registration },
      getToken,
      deleteToken,
    };
  }
}

/**
 * The phone's time zone on the profile and its FCM web token in users/{uid}/devices/{installId} with
 * `platform: web`, written only when they change; without a token only the time zone is written.
 */
export class FcmDeviceRegistry implements DeviceRegistry {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly push: PushIdentity,
    private readonly appVersion: string,
  ) {}

  async sync(uid: string): Promise<void> {
    const db = this.sdk.current;
    const user = doc(db, UserFields.path(uid));
    try {
      // Time zone at app start, when it changed (DESIGN 10.2).
      const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      if ((await mapped(getDoc(user))).get('timezone') !== zone) {
        const batch = writeBatch(db);
        batch.update(user, { timezone: zone, [UserFields.UPDATED_AT]: serverTimestamp() });
        await commitQueued(batch);
      }
      const token = await this.push.token();
      if (!token) return;
      const device: DocumentReference = doc(db, `${UserFields.path(uid)}/devices/${this.push.installId()}`);
      const current = await mapped(getDoc(device));
      if (current.get('token') !== token || current.get('appVersion') !== this.appVersion) {
        await mapped(
          setDoc(
            device,
            { token, platform: 'web', appVersion: this.appVersion, updatedAt: serverTimestamp() },
            { merge: true },
          ),
        );
      }
    } catch {
      // Offline, or push unavailable here; the next start tries again.
    }
  }

  /**
   * On sign-out, so the signed-out phone stops receiving this user's digests. Offline the delete is lost with the
   * local data; DeviceSync then deletes the token, so the document left behind holds one FCM rejects.
   */
  async unregister(uid: string): Promise<void> {
    const ref = doc(this.sdk.current, `${UserFields.path(uid)}/devices/${this.push.installId()}`);
    await Promise.race([
      deleteDoc(ref).catch(() => undefined),
      new Promise((resolve) => setTimeout(resolve, UNREGISTER_TIMEOUT_MS)),
    ]);
  }
}
