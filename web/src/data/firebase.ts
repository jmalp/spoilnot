// The Firebase SDK instances (DESIGN 18.3; core/firebase in the app). A build with no VITE_FIREBASE_CONFIG uses the
// emulator-only project demo-spoilnot on the Firebase emulators (./scripts/emulators.sh), like the Android dev debug
// build; deploy builds get the project's web config from scripts/deploy-web.sh (W7).
import { initializeApp, type FirebaseApp, type FirebaseOptions } from 'firebase/app';
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth';
import {
  clearIndexedDbPersistence,
  connectFirestoreEmulator,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  terminate,
  waitForPendingWrites,
  type Firestore,
} from 'firebase/firestore';
import { BehaviorSubject, type Observable } from 'rxjs';

const EMULATOR_CONFIG: FirebaseOptions = {
  apiKey: 'demo-api-key',
  authDomain: 'demo-spoilnot.firebaseapp.com',
  projectId: 'demo-spoilnot',
  appId: 'demo-spoilnot-web',
};

/** Where the emulators run: the page's own host, so a phone on the same network reaches the Mac's emulators. */
export function emulatorHost(): string {
  return import.meta.env.VITE_EMULATOR_HOST ?? location.hostname;
}

export class FirebaseSdk {
  readonly app: FirebaseApp;
  readonly auth: Auth;
  readonly emulatorHost: string | null;
  private readonly db: BehaviorSubject<Firestore>;

  constructor(options: FirebaseOptions | null, name?: string) {
    this.app = initializeApp(options ?? EMULATOR_CONFIG, name);
    this.auth = getAuth(this.app);
    this.emulatorHost = options ? null : emulatorHost();
    if (this.emulatorHost)
      connectAuthEmulator(this.auth, `http://${this.emulatorHost}:9099`, { disableWarnings: true });
    this.db = new BehaviorSubject(this.newFirestore());
  }

  /**
   * The Firestore instance; it is replaced when local data is cleared, so repositories read [current] or follow
   * [instance$] and never keep one (FirestoreProvider in the app).
   */
  get current(): Firestore {
    return this.db.value;
  }

  get instance$(): Observable<Firestore> {
    return this.db.asObservable();
  }

  /** Sends pending writes if possible, then wipes the phone's copy (DESIGN 12.2, control 6). */
  async clearLocalData(): Promise<void> {
    const old = this.db.value;
    await Promise.race([waitForPendingWrites(old), new Promise((resolve) => setTimeout(resolve, 3_000))]);
    await terminate(old);
    await clearIndexedDbPersistence(old).catch(() => undefined);
    this.db.next(this.newFirestore());
  }

  private newFirestore(): Firestore {
    // The household stays on the phone (IndexedDB), so the app opens and edits offline (DESIGN 9.5, 18.3).
    const db = initializeFirestore(this.app, {
      localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
    });
    if (this.emulatorHost) connectFirestoreEmulator(db, this.emulatorHost, 8080);
    return db;
  }
}

let sdk: FirebaseSdk | null = null;

export function firebase(): FirebaseSdk {
  const deployed = import.meta.env.VITE_FIREBASE_CONFIG;
  sdk ??= new FirebaseSdk(deployed ? (JSON.parse(deployed) as FirebaseOptions) : null);
  return sdk;
}
