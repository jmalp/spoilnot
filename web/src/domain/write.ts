// Writes to household data (core/write in the app): features build plans of changes, which the HouseholdWriter
// applies in batched writes (DESIGN 9.3), so they are testable without Firestore.
import type { Observable } from 'rxjs';
import type { AppError } from '../core/errors';

/** Who is making a change, for createdBy/updatedBy and the activity log. */
export type Actor = { uid: string; name: string };

/** One write. `type` is `feature.change`, so each feature's mapper picks its own. */
export interface HouseholdChange {
  readonly type: string;
}

/** What an Undo puts back (DESIGN 5.2: every destructive action has Undo for 8 seconds). */
export type Undo = { changes: HouseholdChange[] };

export interface HouseholdWriter {
  /** A new random document id. */
  newId(): string;
  /**
   * Applies the changes in order, in as few batches as the 400-operation limit allows (DESIGN 8.5). Returns once
   * they are in the local cache; a later rejection by the server arrives on [errors$].
   */
  apply(householdId: string, actor: Actor, changes: HouseholdChange[]): Promise<void>;
  readonly errors$: Observable<AppError>;
}
