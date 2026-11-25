// config/app (DESIGN 8.2, 18.5; FirestoreAppConfigRepository in the app): the one document the owner edits in the
// console. The rules let any signed-in user read it.
import { doc, type DocumentSnapshot } from 'firebase/firestore';
import { distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import type { AppConfig, AppConfigRepository } from '../domain/updates';
import type { FirebaseSdk } from './firebase';
import { docSnapshots } from './firestore-rx';

export const APP_CONFIG_PATH = 'config/app';

/** A field that is not a whole number reads as 0 (not set). */
export function toAppConfig(snapshot: DocumentSnapshot): AppConfig | null {
  if (!snapshot.exists()) return null;
  const min: unknown = snapshot.get('minWebBuild');
  return { minWebBuild: typeof min === 'number' && Number.isInteger(min) ? min : 0 };
}

export class FirestoreAppConfigRepository implements AppConfigRepository {
  constructor(private readonly sdk: FirebaseSdk) {}

  /** A listener error goes to the caller, which keeps its last answer and listens again (useObservable). */
  observe(): Observable<AppConfig | null> {
    return this.sdk.instance$.pipe(
      switchMap((db) => docSnapshots(doc(db, APP_CONFIG_PATH))),
      map(toAppConfig),
      distinctUntilChanged((a, b) => a?.minWebBuild === b?.minWebBuild),
    );
  }
}
