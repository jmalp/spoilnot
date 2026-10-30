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
import { NotificationRules, type NotificationPrefs, type NotificationPrefsRepository } from '../domain/notifications';
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
const UNREGISTER_TIMEOUT_MS = 3_000;

/** This browser's id for its device document, kept like Firebase Installations' id in the app. */
function installId(): string {
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

/**
 * The phone's time zone on the profile and its FCM web token in users/{uid}/devices/{installId} with
 * `platform: web`, written only when they change. A token needs the permission, the Push API (an iPhone web app on
 * the Home Screen, iOS 16.4 or later), the service worker and the build's VAPID key; without them only the time
 * zone is written.
 */
export class FcmDeviceRegistry implements DeviceRegistry {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly appVersion: string,
    private readonly vapidKey: string | undefined = import.meta.env.VITE_FIREBASE_VAPID_KEY,
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
      const token = await this.token();
      if (!token) return;
      const device: DocumentReference = doc(db, `${UserFields.path(uid)}/devices/${installId()}`);
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

  /** On sign-out, so the signed-out phone stops receiving this user's digests. */
  async unregister(uid: string): Promise<void> {
    const ref = doc(this.sdk.current, `${UserFields.path(uid)}/devices/${installId()}`);
    await Promise.race([
      deleteDoc(ref).catch(() => undefined),
      new Promise((resolve) => setTimeout(resolve, UNREGISTER_TIMEOUT_MS)),
    ]);
  }

  private async token(): Promise<string | null> {
    if (!this.vapidKey || typeof Notification === 'undefined' || Notification.permission !== 'granted') return null;
    const registration = await navigator.serviceWorker?.getRegistration();
    if (!registration) return null; // development builds have no service worker
    // Loaded only here: most visits never need it.
    const { getMessaging, getToken, isSupported } = await import('firebase/messaging');
    if (!(await isSupported())) return null;
    return getToken(getMessaging(this.sdk.app), { vapidKey: this.vapidKey, serviceWorkerRegistration: registration });
  }
}
