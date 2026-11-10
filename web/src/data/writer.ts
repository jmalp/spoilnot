// Applies plans of changes from any feature in batched writes (DESIGN 9.3; FirestoreHouseholdWriter in the app).
import { collection, doc, writeBatch, type Firestore, type WriteBatch } from 'firebase/firestore';
import { Subject, type Observable } from 'rxjs';
import type { AppError } from '../core/errors';
import { planBatches, type Actor, type HouseholdChange, type HouseholdWriter } from '../domain/write';
import type { FirebaseSdk } from './firebase';
import { toAppError } from './firestore-rx';

/** Turns one feature's changes into Firestore writes; returns false for another feature's change. */
export interface ChangeMapper {
  write(batch: WriteBatch, db: Firestore, householdId: string, actor: Actor, change: HouseholdChange): boolean;
  /**
   * Why the server refused [chunk], one batch this mapper helped write, when another phone finished the same thing
   * first (DESIGN 8.5): read from the server after the refusal, so it costs reads only then. Null when the batch holds
   * none of this feature's races, or nothing shows that one was lost.
   */
  whyRefused?(db: Firestore, householdId: string, chunk: HouseholdChange[]): Promise<AppError | null>;
}

export class FirestoreHouseholdWriter implements HouseholdWriter {
  private readonly errors = new Subject<AppError>();
  readonly errors$: Observable<AppError> = this.errors.asObservable();
  private readonly appliedPlans = new Subject<HouseholdChange[]>();
  readonly localPlans$: Observable<HouseholdChange[]> = this.appliedPlans.asObservable();

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
    // Counts go one step per write (DESIGN 8.5), so a plan may need more batches than its size alone.
    for (const chunk of planBatches(changes)) {
      const batch = writeBatch(db);
      for (const change of chunk) {
        if (!this.mappers.some((m) => m.write(batch, db, householdId, actor, change))) {
          throw new Error(`No mapper for ${change.type}`);
        }
      }
      batch.commit().catch(async (error: unknown) => {
        const refused = toAppError(error);
        const why = refused.kind === 'permissionDenied' ? await this.explain(db, householdId, chunk) : null;
        this.errors.next(why ?? refused);
      });
    }
    this.appliedPlans.next(changes);
  }

  /** The race a refused batch lost, if a feature can tell (DESIGN 8.5); a failed read leaves the plain refusal. */
  private async explain(db: Firestore, householdId: string, chunk: HouseholdChange[]): Promise<AppError | null> {
    try {
      for (const mapper of this.mappers) {
        const why = (await mapper.whyRefused?.(db, householdId, chunk)) ?? null;
        if (why !== null) return why;
      }
    } catch {
      // The plain refusal stands.
    }
    return null;
  }
}
