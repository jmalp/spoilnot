// Builders for meal plan tests (MealWeekTest.meal in the app).
import type { MealSlot, PlannedMeal } from './models';

export const THURSDAY = '2026-10-29';

export function meal(
  id: string,
  { date = THURSDAY, slot = 'dinner' as MealSlot, position = 0, createdSecond = 0 } = {},
): PlannedMeal {
  return {
    id,
    date,
    slot,
    position,
    recipeId: null,
    title: id,
    servings: null,
    cooks: [],
    note: null,
    cookedAt: null,
    cookedBy: null,
    createdAt: createdSecond * 1000,
    createdBy: null,
    pendingSync: false,
  };
}
