import type { Services } from '../core/services';
import type { DeviceRegistry } from '../domain/household';
import { PantryService } from '../domain/pantry/service';
import { SessionManager } from '../domain/session';
import { systemClock } from '../domain/time';
import { FirebaseAuthRepository } from './auth';
import { firebase, type FirebaseSdk } from './firebase';
import { FirestoreHouseholdRepository, FirestoreLocalData } from './households';
import { FirestorePantryRepository, pantryMapper } from './pantry';
import { statsMapper } from './stats';
import { FirestoreUserRepository } from './users';
import { SyncLog } from './delta-sync';
import { FirestoreHouseholdWriter } from './writer';

/** Web push registration arrives with W6; until then the phone has no device to register. */
const noDevices: DeviceRegistry = { sync: async () => undefined, unregister: async () => undefined };

/** The real services, on Firebase (the app's Hilt modules). */
export function createServices(sdk: FirebaseSdk = firebase()): Services {
  const auth = new FirebaseAuthRepository(sdk);
  const users = new FirestoreUserRepository(sdk);
  const households = new FirestoreHouseholdRepository(sdk);
  const session = new SessionManager(auth, users, households, new FirestoreLocalData(sdk), noDevices);
  const clock = systemClock;
  const writer = new FirestoreHouseholdWriter(sdk, [pantryMapper, statsMapper]);
  const log = new SyncLog(() => clock.now());
  return {
    session,
    auth,
    users,
    households,
    writer,
    pantry: new FirestorePantryRepository(sdk, log),
    pantryService: new PantryService(writer, clock),
    clock,
  };
}
