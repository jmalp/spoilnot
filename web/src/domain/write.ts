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
  /**
   * Every plan this phone applied, once it is in the local cache. The restock engine uses it to tell a change made on
   * this phone from one that came from another phone (DESIGN 9.6). Writers without it (test fakes) look like a phone
   * that only receives changes.
   */
  readonly localPlans$?: Observable<HouseholdChange[]>;
}

/** DESIGN 8.5: chunk batched writes at 400 operations. */
export const BATCH_LIMIT = 400;

/**
 * A change that adds `delta` to the counts in one document, such as a month of Insights (DESIGN 5.7). The rules let a
 * count move by at most one per write (DESIGN 8.5), so planBatches sends it as steps of one, each counted document at
 * most once per batch (WriteBatches in the app).
 */
export interface CounterChange extends HouseholdChange {
  /** The counted document (the month), shared by every change that writes to it. */
  readonly counter: string;
  readonly delta: number;
}

export const isCounterChange = (change: HouseholdChange): change is CounterChange =>
  typeof (change as Partial<CounterChange>).counter === 'string' &&
  typeof (change as Partial<CounterChange>).delta === 'number';

/**
 * [changes] in order, at most [limit] per batch. A counter change becomes steps of +1 or -1: the first step of each
 * counted document goes in the batch with the changes it came with, and the rest follow in later batches, at most one
 * step per document in each (two writes to one document in a batch count as one write to the rules).
 */
export function planBatches(changes: readonly HouseholdChange[], limit = BATCH_LIMIT): HouseholdChange[][] {
  if (limit <= 0) throw new Error('limit must be positive');
  const batches: HouseholdChange[][] = [];
  let current: HouseholdChange[] = [];
  const counted = new Set<string>();
  let later: CounterChange[] = [];
  const next = () => {
    if (current.length > 0) batches.push(current);
    current = [];
    counted.clear();
  };
  for (const change of changes) {
    if (current.length >= limit) next();
    if (!isCounterChange(change)) {
      current.push(change);
      continue;
    }
    const steps = Array.from({ length: Math.abs(change.delta) }, () => ({ ...change, delta: Math.sign(change.delta) }));
    const [first, ...rest] = steps;
    if (first === undefined) continue;
    if (counted.has(change.counter)) {
      later.push(...steps);
    } else {
      current.push(first);
      counted.add(change.counter);
      later.push(...rest);
    }
  }
  // The remaining steps fill the open batch where they can, then batches of their own.
  while (later.length > 0) {
    const rest: CounterChange[] = [];
    for (const step of later) {
      if (current.length < limit && !counted.has(step.counter)) {
        current.push(step);
        counted.add(step.counter);
      } else {
        rest.push(step);
      }
    }
    if (rest.length === later.length) next();
    later = rest;
  }
  next();
  return batches;
}
