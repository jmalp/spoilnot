// Plan's open sheet and what was picked in it (after v1, REL-09 on the web, as B14's rememberSaveable in the app):
// Add meal with its cooks and typed meal, or a meal's sheet with its servings, cooks and note. Kept in
// sessionStorage while the sheet is open (core/drafts.ts), so iOS reloading the page brings the sheet back as it was,
// on the week it was opened in. Picks are member ids and live apart from the members list, so a member joining or
// leaving meanwhile changes the chips but not the picks.
import { MEAL_SLOTS, type MealSlot, type PlannedMeal } from '../../domain/plan/models';
import { isLocalDate, type LocalDate } from '../../domain/time';

export type AddMealDraft = { kind: 'add'; date: LocalDate; slot: MealSlot; cooks: string[]; other: string };
export type MealDraft = {
  kind: 'meal';
  mealId: string;
  date: LocalDate;
  servings: number | null;
  cooks: string[];
  note: string;
};
export type PlanSheetDraft = AddMealDraft | MealDraft;

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const isStrings = (v: unknown): v is string[] => Array.isArray(v) && v.every((x) => typeof x === 'string');

export const PlanDrafts = {
  addMeal: (date: LocalDate, slot: MealSlot): AddMealDraft => ({ kind: 'add', date, slot, cooks: [], other: '' }),

  meal: (meal: PlannedMeal): MealDraft => ({
    kind: 'meal',
    mealId: meal.id,
    date: meal.date,
    servings: meal.servings,
    cooks: meal.cooks,
    note: meal.note ?? '',
  }),

  /** A saved draft, or null when it is not one (an older build's, or damaged). */
  parse(raw: unknown): PlanSheetDraft | null {
    if (!isRecord(raw) || !isLocalDate(raw.date) || !isStrings(raw.cooks)) return null;
    if (raw.kind === 'add') {
      if (!MEAL_SLOTS.includes(raw.slot as MealSlot) || typeof raw.other !== 'string') return null;
      return { kind: 'add', date: raw.date, slot: raw.slot as MealSlot, cooks: raw.cooks, other: raw.other };
    }
    if (raw.kind === 'meal') {
      const { mealId, servings, note } = raw;
      if (typeof mealId !== 'string' || typeof note !== 'string') return null;
      const count = servings === null ? null : Number.isInteger(servings) ? (servings as number) : undefined;
      if (count === undefined) return null;
      return { kind: 'meal', mealId, date: raw.date, servings: count, cooks: raw.cooks, note };
    }
    return null;
  },
};
