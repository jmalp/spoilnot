// The meal plan (feature/plan/data in the app): a changes-only listener on a window of dates and the writes of
// MealPlanChange. Every field is written explicitly, nulls included (DESIGN 8.1).
import {
  collection,
  doc,
  query,
  serverTimestamp,
  Timestamp,
  where,
  type DocumentSnapshot,
  type Firestore,
  type WriteBatch,
} from 'firebase/firestore';
import { distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import {
  isMealPlanChange,
  mealSlotOf,
  type MealPlanData,
  type MealPlanRepository,
  type PlannedMeal,
} from '../domain/plan/models';
import { isLocalDate, type LocalDate } from '../domain/time';
import type { Actor, HouseholdChange } from '../domain/write';
import { liveDocuments, Stamps, Tombstones, type SyncLog } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import type { ChangeMapper } from './writer';

export const meals = (hid: string) => `households/${hid}/meals`;

const millis = (value: unknown): number | null => (value instanceof Timestamp ? value.toMillis() : null);
const text = (value: unknown): string | null => (typeof value === 'string' ? value : null);

// New fields are optional on read with defaults (DESIGN 8.1); a meal with no valid date or slot is skipped.
export function toMeal(s: DocumentSnapshot): PlannedMeal | null {
  const date = s.get('date');
  const slot = mealSlotOf(s.get('slot'));
  if (!isLocalDate(date) || slot === null) return null;
  const servings = s.get('servings');
  return {
    id: s.id,
    date,
    slot,
    position: typeof s.get('position') === 'number' ? (s.get('position') as number) : 0,
    recipeId: text(s.get('recipeId')),
    title: text(s.get('title')) ?? '',
    servings: typeof servings === 'number' ? Math.trunc(servings) : null,
    cooks: ((s.get('cooks') as unknown[] | null) ?? []).filter((c): c is string => typeof c === 'string'),
    note: text(s.get('note')),
    cookedAt: millis(s.get('cookedAt')),
    cookedBy: text(s.get('cookedBy')),
    createdAt: (s.get('createdAt', { serverTimestamps: 'estimate' }) as Timestamp | null)?.toMillis?.() ?? null,
    createdBy: text(s.get('createdBy')),
    pendingSync: s.metadata.hasPendingWrites,
  };
}

export class FirestoreMealPlanRepository implements MealPlanRepository {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly log: SyncLog,
  ) {}

  // Changes-only, limited to the dates on screen (DESIGN 9.8): a week seen for the first time is read in full, then
  // only what changed. Dates are 'YYYY-MM-DD' strings, so a string range is a date range.
  observe(householdId: string, from: LocalDate, to: LocalDate): Observable<MealPlanData> {
    return this.sdk.instance$.pipe(
      switchMap((db) => {
        const all = collection(db, meals(householdId));
        const window = query(all, where('date', '>=', from), where('date', '<=', to));
        return liveDocuments(db, all, this.log, window, `${all.path}?date=${from}..${to}`);
      }),
      map((live) => ({
        meals: live.documents.map(toMeal).filter((m): m is PlannedMeal => m !== null),
        fromCache: live.fromCache,
      })),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }
}

const fields = (meal: PlannedMeal) => ({
  date: meal.date,
  slot: meal.slot,
  position: meal.position,
  recipeId: meal.recipeId,
  title: meal.title,
  servings: meal.servings,
  cooks: meal.cooks,
  note: meal.note,
  cookedAt: meal.cookedAt !== null ? Timestamp.fromMillis(meal.cookedAt) : null,
  cookedBy: meal.cookedBy,
});

/** Firestore writes for MealPlanChange (MealPlanChangeMapper). */
export const mealPlanMapper: ChangeMapper = {
  write(batch: WriteBatch, db: Firestore, hid: string, actor: Actor, change: HouseholdChange): boolean {
    if (!isMealPlanChange(change)) return false;
    const meal = (id: string) => doc(db, meals(hid), id);
    const stamp = Stamps.updated(actor);
    switch (change.type) {
      case 'meal.create':
        batch.set(meal(change.meal.id), { ...fields(change.meal), ...Stamps.created(actor), ...stamp });
        break;
      case 'meal.restore':
        batch.set(meal(change.meal.id), {
          ...fields(change.meal),
          ...stamp,
          createdAt: change.meal.createdAt !== null ? Timestamp.fromMillis(change.meal.createdAt) : serverTimestamp(),
          createdBy: change.meal.createdBy ?? actor.uid,
        });
        break;
      case 'meal.update':
        batch.update(meal(change.mealId), {
          servings: change.servings,
          cooks: change.cooks,
          note: change.note,
          ...stamp,
        });
        break;
      case 'meal.delete':
        batch.set(meal(change.mealId), Tombstones.fields(actor), { merge: true });
        break;
      case 'meal.move':
        batch.update(meal(change.mealId), {
          date: change.date,
          slot: change.slot,
          position: change.position,
          ...stamp,
        });
        break;
      case 'meal.setCooked':
        batch.update(meal(change.mealId), {
          cookedAt: change.cooked ? serverTimestamp() : null,
          cookedBy: change.cooked ? actor.uid : null,
          ...stamp,
        });
        break;
    }
    return true;
  },
};
