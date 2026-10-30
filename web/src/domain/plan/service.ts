// The week's groceries and the meal plan actions (DESIGN 5.9), ported from MealPlanGrocery and MealPlanService.
import type { GroceryItem } from '../grocery/models';
import type { PantryChange, PantryData } from '../pantry/models';
import { NameKey } from '../pantry/rules';
import type { PantryService } from '../pantry/service';
import type { Recipe } from '../recipes/models';
import { Scaling } from '../recipes/parser';
import { RecipeGrocery, type GroceryCandidate, type RecipeService } from '../recipes/service';
import type { LocalDate } from '../time';
import type { Actor, HouseholdChange, HouseholdWriter, Undo } from '../write';
import { MEAL_SLOTS, MealPlanLimits, type MealPlanChange, type MealSlot, type PlannedMeal } from './models';
import { MealWeek, type PlanDay } from './week';

/** A planned recipe and its ingredients as grocery lines, scaled to the meal's servings. */
export type MealGroceries = { meal: PlannedMeal; recipe: Recipe; candidates: GroceryCandidate[] };

/**
 * "Add to grocery list" for a week: the ingredients of the recipes still to cook, chosen the same way as from one
 * recipe. Lines that look like pantry items or are already on the list start unchecked; the same item from two meals
 * becomes one when added.
 */
export const MealPlanGrocery = {
  /** Recipe meals from today on that are not cooked yet and whose recipe is still saved, in plan order. */
  toShop(days: PlanDay[], recipes: Recipe[]): [PlannedMeal, Recipe][] {
    const byId = new Map(recipes.map((r) => [r.id, r]));
    return days
      .filter((d) => !d.isPast)
      .flatMap((d) => MEAL_SLOTS.flatMap((s) => d.slots[s]))
      .flatMap((meal): [PlannedMeal, Recipe][] => {
        const recipe = meal.recipeId ? byId.get(meal.recipeId) : undefined;
        return recipe && meal.cookedAt === null ? [[meal, recipe]] : [];
      });
  },

  /** How much of the recipe the meal needs: its servings over the recipe's, or 1 when either is unknown. */
  factor: (meal: PlannedMeal, recipe: Recipe): number => Scaling.factor(recipe.content.servings, meal.servings),

  sections(meals: [PlannedMeal, Recipe][], pantry: PantryData, listItems: GroceryItem[]): MealGroceries[] {
    return meals
      .map(([meal, recipe]) => ({
        meal,
        recipe,
        candidates: RecipeGrocery.candidates(recipe, pantry, listItems, MealPlanGrocery.factor(meal, recipe)),
      }))
      .filter((s) => s.candidates.length > 0);
  },
};

export function validTitle(title: string): string {
  const clean = NameKey.clean(title);
  if (clean === '') throw new Error('A meal needs a name');
  return clean.slice(0, MealPlanLimits.TITLE).trim();
}

export const validServings = (servings: number | null): number | null =>
  servings === null ? null : Math.min(MealPlanLimits.MAX_SERVINGS, Math.max(MealPlanLimits.MIN_SERVINGS, servings));

export const validCooks = (cooks: string[]): string[] =>
  [...new Set(cooks.filter((c) => c.trim() !== ''))].slice(0, MealPlanLimits.MAX_COOKS);

export const validNote = (note: string | null): string | null =>
  note?.trim().slice(0, MealPlanLimits.NOTE).trim() || null;

type Change = MealPlanChange | PantryChange;

/** Meal plan actions. Every member may change the plan; removing and cooking have Undo. */
export class MealPlanService {
  constructor(
    private readonly writer: HouseholdWriter,
    private readonly pantry: PantryService,
    private readonly recipes: RecipeService,
  ) {}

  private write(householdId: string, actor: Actor, changes: Change[]): Promise<void> {
    return this.writer.apply(householdId, actor, changes);
  }

  /** Plans [recipe] in a slot, after the meals already there; servings start at the recipe's. Returns the id. */
  addRecipe(
    householdId: string,
    actor: Actor,
    date: LocalDate,
    slot: MealSlot,
    recipe: Recipe,
    cooks: string[],
    inSlot: PlannedMeal[],
  ): Promise<string> {
    return this.add(
      householdId,
      actor,
      date,
      slot,
      recipe.id,
      recipe.content.title,
      recipe.content.servings,
      cooks,
      inSlot,
    );
  }

  /** Plans a meal without a recipe, such as "Leftovers" or "Eat out". Returns the id. */
  addOther(
    householdId: string,
    actor: Actor,
    date: LocalDate,
    slot: MealSlot,
    title: string,
    cooks: string[],
    inSlot: PlannedMeal[],
  ): Promise<string> {
    return this.add(householdId, actor, date, slot, null, title, null, cooks, inSlot);
  }

  update(
    householdId: string,
    actor: Actor,
    meal: PlannedMeal,
    servings: number | null,
    cooks: string[],
    note: string | null,
  ) {
    return this.write(householdId, actor, [
      {
        type: 'meal.update',
        mealId: meal.id,
        servings: validServings(servings),
        cooks: validCooks(cooks),
        note: validNote(note),
      },
    ]);
  }

  async remove(householdId: string, actor: Actor, meal: PlannedMeal): Promise<Undo> {
    await this.write(householdId, actor, [{ type: 'meal.delete', mealId: meal.id }]);
    const restore: Change = { type: 'meal.restore', meal };
    return { changes: [restore] };
  }

  undo(householdId: string, actor: Actor, undo: Undo) {
    return this.writer.apply(householdId, actor, undo.changes);
  }

  /**
   * "Cooked": marks [meal] cooked and, for a recipe, takes [amounts] (product id to amount) off the pantry and logs
   * "cooked" in Recent activity, the same as I cooked this on the recipe. One Undo puts all of it back.
   */
  async cooked(
    householdId: string,
    actor: Actor,
    meal: PlannedMeal,
    recipe: Recipe | null,
    amounts: Map<string, number>,
    pantryData: PantryData,
  ): Promise<Undo> {
    const mark: Change = { type: 'meal.setCooked', mealId: meal.id, cooked: true };
    const unmark: Change = { type: 'meal.setCooked', mealId: meal.id, cooked: false };
    const extra: HouseholdChange[] = [mark];
    const undoExtra: HouseholdChange[] = [unmark];
    if (recipe) {
      const [event, removeEvent] = this.recipes.cookedEvent(recipe);
      extra.push(event);
      undoExtra.push(removeEvent);
    }
    const undo = await this.pantry.use(householdId, actor, amounts, pantryData, extra, undoExtra);
    if (undo) return undo;
    // Nothing taken off the pantry: only the meal (and the event) change.
    await this.writer.apply(householdId, actor, extra);
    return { changes: undoExtra };
  }

  /** Moves [meal] to [date] and [slot], after the meals there ([inSlot]). Undo puts it back where it was. */
  async move(
    householdId: string,
    actor: Actor,
    meal: PlannedMeal,
    date: LocalDate,
    slot: MealSlot,
    inSlot: PlannedMeal[],
  ): Promise<Undo | null> {
    if (meal.date === date && meal.slot === slot) return null;
    const position = MealWeek.nextPosition(inSlot.filter((m) => m.id !== meal.id));
    await this.write(householdId, actor, [{ type: 'meal.move', mealId: meal.id, date, slot, position }]);
    const back: Change = {
      type: 'meal.move',
      mealId: meal.id,
      date: meal.date,
      slot: meal.slot,
      position: meal.position,
    };
    return { changes: [back] };
  }

  /**
   * Copy last week: [previous] week's meals into the week starting [to], skipping ones already there. Returns how many
   * were copied and an Undo that removes them, or null when there was nothing to copy.
   */
  async copyWeek(
    householdId: string,
    actor: Actor,
    previous: PlannedMeal[],
    from: LocalDate,
    to: LocalDate,
    existing: PlannedMeal[],
  ): Promise<[number, Undo] | null> {
    const copies = MealWeek.copy(previous, from, to, existing).map((m) => ({ ...m, id: this.writer.newId() }));
    if (copies.length === 0) return null;
    await this.write(
      householdId,
      actor,
      copies.map((meal): Change => ({ type: 'meal.create', meal })),
    );
    return [copies.length, { changes: copies.map((m): Change => ({ type: 'meal.delete', mealId: m.id })) }];
  }

  /** Clear week: removes [meals] (one week's), with one Undo that puts them all back. Null when it is empty. */
  async clear(householdId: string, actor: Actor, meals: PlannedMeal[]): Promise<Undo | null> {
    if (meals.length === 0) return null;
    await this.write(
      householdId,
      actor,
      meals.map((m): Change => ({ type: 'meal.delete', mealId: m.id })),
    );
    return { changes: meals.map((meal): Change => ({ type: 'meal.restore', meal })) };
  }

  /** "Not cooked yet": for a meal marked cooked by mistake. The pantry is not touched. */
  notCooked(householdId: string, actor: Actor, meal: PlannedMeal) {
    return this.write(householdId, actor, [{ type: 'meal.setCooked', mealId: meal.id, cooked: false }]);
  }

  private async add(
    householdId: string,
    actor: Actor,
    date: LocalDate,
    slot: MealSlot,
    recipeId: string | null,
    title: string,
    servings: number | null,
    cooks: string[],
    inSlot: PlannedMeal[],
  ): Promise<string> {
    const meal: PlannedMeal = {
      id: this.writer.newId(),
      date,
      slot,
      position: MealWeek.nextPosition(inSlot),
      recipeId,
      title: validTitle(title),
      servings: validServings(servings),
      cooks: validCooks(cooks),
      note: null,
      cookedAt: null,
      cookedBy: null,
      createdAt: null,
      createdBy: null,
      pendingSync: false,
    };
    await this.write(householdId, actor, [{ type: 'meal.create', meal }]);
    return meal.id;
  }
}
