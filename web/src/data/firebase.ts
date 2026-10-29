// The Firebase SDK instances (DESIGN 18.3; core/firebase in the app). A build with no VITE_FIREBASE_CONFIG uses the
// emulator-only project demo-spoilnot on the Firebase emulators (./scripts/emulators.sh), like the Android dev debug
// build; deploy builds get the project's web config from scripts/deploy-web.sh (W7).
import { initializeApp, type FirebaseApp, type FirebaseOptions } from 'firebase/app';
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth';
import {
  connectFirestoreEmulator,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from 'firebase/firestore';

const EMULATOR_CONFIG: FirebaseOptions = {
  apiKey: 'demo-api-key',
  authDomain: 'demo-spoilnot.firebaseapp.com',
  projectId: 'demo-spoilnot',
  appId: 'demo-spoilnot-web',
};

export type FirebaseSdk = { app: FirebaseApp; auth: Auth; db: Firestore; emulatorHost: string | null };

let sdk: FirebaseSdk | null = null;

/** Where the emulators run: the page's own host, so a phone on the same network reaches the Mac's emulators. */
export function emulatorHost(): string {
  return import.meta.env.VITE_EMULATOR_HOST ?? location.hostname;
}

export function firebase(): FirebaseSdk {
  if (sdk) return sdk;
  const deployed = import.meta.env.VITE_FIREBASE_CONFIG;
  const app = initializeApp(deployed ? (JSON.parse(deployed) as FirebaseOptions) : EMULATOR_CONFIG);
  const auth = getAuth(app);
  // The household stays on the phone (IndexedDB), so the app opens and edits offline (DESIGN 9.5, 18.3).
  const db = initializeFirestore(app, {
    localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
  });
  const host = deployed ? null : emulatorHost();
  if (host) {
    connectAuthEmulator(auth, `http://${host}:9099`, { disableWarnings: true });
    connectFirestoreEmulator(db, host, 8080);
  }
  sdk = { app, auth, db, emulatorHost: host };
  return sdk;
}
