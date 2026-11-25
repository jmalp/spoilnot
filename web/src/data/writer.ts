// Applies plans of changes from any feature in batched writes (DESIGN 9.3; FirestoreHouseholdWriter in the app).
import { collection, doc, writeBatch, type Firestore, type WriteBatch } from 'firebase/firestore';
import { merge, Subject, type Observable } from 'rxjs';
import type { AppError } from '../core/errors';
import { planBatches, type Actor, type HouseholdChange, type HouseholdWriter } from '../domain/write';
import type { FirebaseSdk } from './firebase';
import { lateRefusals$, toAppError } from './firestore-rx';

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
  /** Every refused write, this writer's and the queued ones of other repositories (lateRefusals$, REL-13). */
  readonly errors$: Observable<AppError> = merge(this.errors, lateRefusals$);
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
    for (const { chunk, commit } of this.commit(db, householdId, actor, changes)) {
      commit.catch(async (error: unknown) => {
        const refused = toAppError(error);
        const why = refused.kind === 'permissionDenied' ? await this.explain(db, householdId, chunk) : null;
        this.errors.next(why ?? refused);
      });
    }
    this.appliedPlans.next(changes);
  }

  async applyQuietly(
    householdId: string,
    actor: Actor,
    changes: HouseholdChange[],
    onAnswer: (refusal: AppError | null) => void,
  ): Promise<void> {
    const commits = this.commit(this.sdk.current, householdId, actor, changes).map((c) => c.commit);
    void Promise.allSettled(commits).then((results) => {
      const refused = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
      onAnswer(refused ? toAppError(refused.reason) : null);
    });
    this.appliedPlans.next(changes);
  }

  /** Commits [changes] to the local cache; counts go one step per write (DESIGN 8.5), so maybe in more batches. */
  private commit(db: Firestore, householdId: string, actor: Actor, changes: HouseholdChange[]) {
    return planBatches(changes).map((chunk) => {
      const batch = writeBatch(db);
      for (const change of chunk) {
        if (!this.mappers.some((m) => m.write(batch, db, householdId, actor, change))) {
          throw new Error(`No mapper for ${change.type}`);
        }
      }
      return { chunk, commit: batch.commit() };
    });
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
