import type { Services } from '../core/services';
import type { DeviceRegistry } from '../domain/household';
import { SessionManager } from '../domain/session';
import { FirebaseAuthRepository } from './auth';
import { firebase, type FirebaseSdk } from './firebase';
import { FirestoreHouseholdRepository, FirestoreLocalData } from './households';
import { FirestoreUserRepository } from './users';

/** Web push registration arrives with W6; until then the phone has no device to register. */
const noDevices: DeviceRegistry = { sync: async () => undefined, unregister: async () => undefined };

/** The real services, on Firebase (the app's Hilt modules). */
export function createServices(sdk: FirebaseSdk = firebase()): Services {
  const auth = new FirebaseAuthRepository(sdk);
  const users = new FirestoreUserRepository(sdk);
  const households = new FirestoreHouseholdRepository(sdk);
  const session = new SessionManager(auth, users, households, new FirestoreLocalData(sdk), noDevices);
  return { session, auth, users, households };
}
