// Applies plans of changes from any feature in batched writes (DESIGN 9.3; FirestoreHouseholdWriter in the app).
import { collection, doc, writeBatch, type Firestore, type WriteBatch } from 'firebase/firestore';
import { Subject, type Observable } from 'rxjs';
import type { AppError } from '../core/errors';
import type { Actor, HouseholdChange, HouseholdWriter } from '../domain/write';
import type { FirebaseSdk } from './firebase';
import { toAppError } from './firestore-rx';

/** Turns one feature's changes into Firestore writes; returns false for another feature's change. */
export interface ChangeMapper {
  write(batch: WriteBatch, db: Firestore, householdId: string, actor: Actor, change: HouseholdChange): boolean;
}

/** DESIGN 8.5: chunk batched writes at 400 operations. */
const BATCH_LIMIT = 400;

export class FirestoreHouseholdWriter implements HouseholdWriter {
  private readonly errors = new Subject<AppError>();
  readonly errors$: Observable<AppError> = this.errors.asObservable();

  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly mappers: ChangeMapper[],
  ) {}

  newId(): string {
    return doc(collection(this.sdk.current, 'ids')).id;
  }

  /** Returns once the changes are in the local cache; a later rejection by the server arrives on errors$. */
  async apply(householdId: string, actor: Actor, changes: HouseholdChange[]): Promise<void> {
    const db = this.sdk.current;
    for (let i = 0; i < changes.length; i += BATCH_LIMIT) {
      const batch = writeBatch(db);
      for (const change of changes.slice(i, i + BATCH_LIMIT)) {
        if (!this.mappers.some((m) => m.write(batch, db, householdId, actor, change))) {
          throw new Error(`No mapper for ${change.type}`);
        }
      }
      batch.commit().catch((error: unknown) => this.errors.next(toAppError(error)));
    }
  }
}
