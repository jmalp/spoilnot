// The meal plan (DESIGN 5.9, 8.2), ported from feature/plan/domain/MealPlanModels.
import type { Observable } from 'rxjs';
import type { LocalDate } from '../time';
import type { HouseholdChange } from '../write';

/** The meal slots of a day, in the order they are shown. */
export const MEAL_SLOTS = ['breakfast', 'lunch', 'dinner'] as const;
export type MealSlot = (typeof MEAL_SLOTS)[number];
export const mealSlotOf = (value: unknown): MealSlot | null =>
  (MEAL_SLOTS as readonly unknown[]).includes(value) ? (value as MealSlot) : null;

/**
 * households/{hid}/meals/{id}: one planned meal. [recipeId] is null for a meal without a recipe ("Leftovers");
 * [title] is the recipe's title when it was planned, so a deleted recipe still has a name. [cooks] are member uids.
 * [position] orders meals within a slot.
 */
export type PlannedMeal = {
  id: string;
  date: LocalDate;
  slot: MealSlot;
  position: number;
  recipeId: string | null;
  title: string;
  servings: number | null;
  cooks: string[];
  note: string | null;
  /** Epoch milliseconds (server time), or null when not cooked. */
  cookedAt: number | null;
  cookedBy: string | null;
  createdAt: number | null;
  createdBy: string | null;
  pendingSync: boolean;
};

/** One write in a meal plan, applied by the HouseholdWriter. */
export type MealPlanChange =
  | { type: 'meal.create'; meal: PlannedMeal }
  /** Puts a removed meal back under its old id (Undo). */
  | { type: 'meal.restore'; meal: PlannedMeal }
  | { type: 'meal.update'; mealId: string; servings: number | null; cooks: string[]; note: string | null }
  /** A tombstone, so other phones' changes-only listeners see it go (DESIGN 9.8). */
  | { type: 'meal.delete'; mealId: string }
  /** To another day or slot, after the meals there. */
  | { type: 'meal.move'; mealId: string; date: LocalDate; slot: MealSlot; position: number }
  /** Marks a meal cooked by the actor now (server time), or not cooked again. */
  | { type: 'meal.setCooked'; mealId: string; cooked: boolean };

export const isMealPlanChange = (change: HouseholdChange): change is MealPlanChange => change.type.startsWith('meal.');

/** Meals in a date range, and whether the server has confirmed them yet. */
export type MealPlanData = { meals: PlannedMeal[]; fromCache: boolean };

export interface MealPlanRepository {
  /** Planned meals from [from] to [to] inclusive (changes-only, DESIGN 9.8). */
  observe(householdId: string, from: LocalDate, to: LocalDate): Observable<MealPlanData>;
}

/** DESIGN 5.9 limits, also enforced by the security rules. */
export const MealPlanLimits = {
  TITLE: 120,
  NOTE: 200,
  MAX_COOKS: 20,
  MIN_SERVINGS: 1,
  MAX_SERVINGS: 100,
};
