// Ported from MealPlanGroceryTest and MealPlanServiceTest.
import { EMPTY } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import type { GroceryItem } from '../grocery/models';
import { batch, product } from '../pantry/fixtures';
import type { PantryData } from '../pantry/models';
import { PantryService } from '../pantry/service';
import { recipe } from '../recipes/fixtures';
import type { RecipeChange } from '../recipes/models';
import { RecipeGrocery, RecipeService, selectedByDefault } from '../recipes/service';
import { addDays } from '../time';
import type { HouseholdChange, HouseholdWriter } from '../write';
import { meal, THURSDAY } from './fixtures';
import { MealPlanLimits, type MealPlanChange, type PlannedMeal } from './models';
import { MealPlanGrocery, MealPlanService } from './service';
import { MealWeek } from './week';

class RecordingWriter implements HouseholdWriter {
  last: HouseholdChange[] = [];
  private next = 0;
  readonly errors$ = EMPTY;
  newId = () => `id${this.next++}`;
  apply = async (_h: string, _a: unknown, changes: HouseholdChange[]) => {
    this.last = changes;
  };
}

const empty: PantryData = { products: [], batches: [], fromCache: false };

describe('MealPlanGrocery', () => {
  const monday = '2026-10-26';
  const wednesday = addDays(monday, 2);
  const planned = (
    id: string,
    date: string,
    recipeId: string | null,
    { servings = null as number | null, slot = 'dinner' as PlannedMeal['slot'], cooked = false } = {},
  ): PlannedMeal => ({
    ...meal(id, { date, slot }),
    recipeId,
    servings,
    cookedAt: cooked ? Date.parse('2026-10-28T19:00:00Z') : null,
  });

  it('only recipes still to cook this week are shopped for, in plan order', () => {
    const recipes = [
      recipe('tacos', { title: 'Tacos' }),
      recipe('soup', { title: 'Soup' }),
      recipe('pie', { title: 'Pie' }),
    ];
    const meals = [
      planned('past', monday, 'tacos'), // before today
      planned('done', wednesday, 'soup', { cooked: true }),
      planned('other', wednesday, null), // Leftovers: no recipe
      planned('gone', wednesday, 'deleted'), // recipe deleted since
      planned('dinner', wednesday, 'tacos'),
      planned('lunch', wednesday, 'pie', { slot: 'lunch' }),
      planned('friday', addDays(monday, 4), 'soup'),
    ];
    const days = MealWeek.build(monday, meals, wednesday);
    expect(MealPlanGrocery.toShop(days, recipes).map(([m]) => m.id)).toEqual(['lunch', 'dinner', 'friday']);
  });

  it("a meal's servings scale counts up to whole items and show in the note", () => {
    const soup = recipe('soup', { title: 'Soup', servings: 4, ingredients: ['3 eggs', '1 onion, diced'] });
    const m = planned('m1', wednesday, 'soup', { servings: 6 });
    expect(MealPlanGrocery.factor(m, soup)).toBe(1.5);
    const lines = MealPlanGrocery.sections([[m, soup]], empty, [])[0]!.candidates;
    // 4.5 eggs are 5 eggs to buy; 1.5 onions are 2.
    expect(lines.map((l) => l.quantity)).toEqual([5, 2]);
    expect(lines[0]!.note).toBe('Soup: 4½ eggs');
    expect(lines.every((l) => l.recipeId === 'soup')).toBe(true);
    // Without servings on either side, the recipe is used as written.
    expect(MealPlanGrocery.factor({ ...m, servings: null }, soup)).toBe(1);
  });

  it('lines already on the list or in the pantry start unchecked, and recipes without lines are left out', () => {
    const tacos = recipe('tacos', { title: 'Tacos', ingredients: ['2 tortillas', '1 cup milk'] });
    const bare = recipe('bare', { title: 'Bare' });
    const pantry: PantryData = { products: [product('milk')], batches: [batch('b1', 'milk')], fromCache: false };
    const onList: GroceryItem = {
      id: 'i1',
      name: 'Tortillas',
      nameKey: 'tortillas',
      quantity: null,
      unit: null,
      note: null,
      category: null,
      checked: false,
      checkedBy: null,
      source: 'manual',
      productId: null,
      recipeId: null,
      createdBy: null,
      createdAt: null,
      pendingSync: false,
      aliasOf: null,
    };
    const sections = MealPlanGrocery.sections(
      [
        [planned('m1', wednesday, 'tacos'), tacos],
        [planned('m2', wednesday, 'bare'), bare],
      ],
      pantry,
      [onList],
    );
    expect(sections).toHaveLength(1);
    const lines = sections[0]!.candidates;
    expect(lines[0]!.onList).toBe(true);
    expect(lines[1]!.inPantry).not.toBeNull();
    expect(lines.some(selectedByDefault)).toBe(false);
  });

  it('the same item from two meals becomes one, with both notes', () => {
    const tacos = recipe('tacos', { title: 'Tacos', ingredients: ['2 tortillas'] });
    const soup = recipe('soup', { title: 'Soup', ingredients: ['3 tortillas'] });
    const sections = MealPlanGrocery.sections(
      [
        [planned('m1', wednesday, 'tacos'), tacos],
        [planned('m2', wednesday, 'soup'), soup],
      ],
      empty,
      [],
    );
    const merged = RecipeGrocery.merge(sections.flatMap((s) => s.candidates));
    expect(merged).toHaveLength(1);
    expect(merged[0]!.quantity).toBe(5);
    expect(merged[0]!.note).toBe('Tacos: 2 tortillas; Soup: 3 tortillas');
  });
});

describe('MealPlanService', () => {
  const actor = { uid: 'u1', name: 'Sam' };
  const clock = { now: () => Date.parse(`${THURSDAY}T12:00:00Z`) };
  const existing = meal('m0', { position: 2 });
  let writer: RecordingWriter;
  let service: MealPlanService;
  beforeEach(() => {
    writer = new RecordingWriter();
    service = new MealPlanService(writer, new PantryService(writer, clock), new RecipeService(writer));
  });
  const only = <T extends MealPlanChange['type']>(type: T) =>
    writer.last.filter((c) => c.type === type) as Extract<MealPlanChange, { type: T }>[];

  it("a recipe is planned after the slot's meals, with the recipe's servings and title", async () => {
    const id = await service.addRecipe(
      'h',
      actor,
      THURSDAY,
      'dinner',
      recipe('r1', { title: 'Lasagna', servings: 6 }),
      ['u1', 'u2', 'u1'],
      [existing],
    );
    expect(id).toBe('id0');
    expect(only('meal.create')[0]!.meal).toEqual({
      ...meal('id0', { position: 3 }),
      createdAt: null,
      recipeId: 'r1',
      title: 'Lasagna',
      servings: 6,
      cooks: ['u1', 'u2'],
    });
  });

  it('something else has a cleaned name and no recipe, and needs a name', async () => {
    await service.addOther('h', actor, THURSDAY, 'lunch', '  Eat   out ', [], []);
    const created = only('meal.create')[0]!.meal;
    expect([created.title, created.recipeId, created.position]).toEqual(['Eat out', null, 0]);
    await expect(service.addOther('h', actor, THURSDAY, 'lunch', '   ', [], [])).rejects.toThrow();
  });

  it('changes are kept within the limits', async () => {
    const cooks = Array.from({ length: 25 }, (_, i) => `u${i + 1}`);
    await service.update('h', actor, existing, 500, cooks, '  ');
    const update = only('meal.update')[0]!;
    expect(update.servings).toBe(MealPlanLimits.MAX_SERVINGS);
    expect(update.cooks).toHaveLength(MealPlanLimits.MAX_COOKS);
    expect(update.note).toBeNull();
    await service.update('h', actor, existing, 0, [], ' Double batch ');
    expect([only('meal.update')[0]!.servings, only('meal.update')[0]!.note]).toEqual([1, 'Double batch']);
  });

  it('removing leaves a tombstone and Undo puts the meal back as it was', async () => {
    const undo = await service.remove('h', actor, existing);
    expect(writer.last).toEqual([{ type: 'meal.delete', mealId: 'm0' }]);
    expect(undo.changes).toEqual([{ type: 'meal.restore', meal: existing }]);
    await service.undo('h', actor, undo);
    expect(writer.last).toEqual(undo.changes);
  });

  it('cooking a recipe meal takes it off the pantry, marks the meal and logs it, all in one Undo', async () => {
    const tacos = recipe('r1', { title: 'Tacos', ingredients: ['2 eggs'] });
    const m = { ...existing, recipeId: 'r1', title: 'Tacos' };
    const pantry: PantryData = {
      products: [product('eggs')],
      batches: [batch('b1', 'eggs', { quantity: 6 })],
      fromCache: false,
    };
    const undo = await service.cooked('h', actor, m, tacos, new Map([['eggs', 2]]), pantry);
    expect(writer.last[0]).toEqual({ type: 'pantry.addToBatch', batchId: 'b1', delta: -2 });
    expect(only('meal.setCooked')).toEqual([{ type: 'meal.setCooked', mealId: 'm0', cooked: true }]);
    const event = writer.last.find((c): c is RecipeChange => c.type === 'recipe.logCooked') as Extract<
      RecipeChange,
      { type: 'recipe.logCooked' }
    >;
    expect(event.title).toBe('Tacos');
    expect(undo.changes).toEqual([
      { type: 'pantry.addToBatch', batchId: 'b1', delta: 2 },
      { type: 'meal.setCooked', mealId: 'm0', cooked: false },
      { type: 'pantry.deleteEvent', eventId: event.eventId },
    ]);
  });

  it('a meal without a recipe, or nothing used, only marks the meal', async () => {
    const undo = await service.cooked('h', actor, existing, null, new Map(), empty);
    expect(writer.last).toEqual([{ type: 'meal.setCooked', mealId: 'm0', cooked: true }]);
    expect(undo.changes).toEqual([{ type: 'meal.setCooked', mealId: 'm0', cooked: false }]);
    // A recipe meal with nothing taken off still logs that it was cooked.
    const withRecipe = await service.cooked('h', actor, existing, recipe('r1', { title: 'Soup' }), new Map(), empty);
    expect(writer.last).toHaveLength(2);
    expect(writer.last.some((c) => c.type === 'recipe.logCooked')).toBe(true);
    expect(withRecipe.changes.some((c) => c.type === 'pantry.deleteEvent')).toBe(true);
    await service.notCooked('h', actor, existing);
    expect(writer.last).toEqual([{ type: 'meal.setCooked', mealId: 'm0', cooked: false }]);
  });

  it('moving goes after the meals in the new slot and Undo puts it back', async () => {
    const friday = addDays(THURSDAY, 1);
    const there = meal('m1', { date: friday, slot: 'lunch', position: 3 });
    const undo = (await service.move('h', actor, existing, friday, 'lunch', [there]))!;
    expect(writer.last).toEqual([{ type: 'meal.move', mealId: 'm0', date: friday, slot: 'lunch', position: 4 }]);
    expect(undo.changes).toEqual([{ type: 'meal.move', mealId: 'm0', date: THURSDAY, slot: 'dinner', position: 2 }]);
    // The same place is no move.
    expect(await service.move('h', actor, existing, THURSDAY, 'dinner', [])).toBeNull();
  });

  it('copying last week creates the copies, and Undo removes them', async () => {
    const lastStart = '2026-10-19';
    const previous = [{ ...meal('p1', { date: addDays(THURSDAY, -7) }), title: 'Tacos' }];
    const [count, undo] = (await service.copyWeek('h', actor, previous, lastStart, addDays(lastStart, 7), []))!;
    expect(count).toBe(1);
    expect(only('meal.create').map((c) => [c.meal.id, c.meal.date])).toEqual([['id0', THURSDAY]]);
    expect(undo.changes).toEqual([{ type: 'meal.delete', mealId: 'id0' }]);
    expect(await service.copyWeek('h', actor, [], lastStart, addDays(lastStart, 7), [])).toBeNull();
  });

  it('clearing a week removes every meal with one Undo', async () => {
    const other = meal('m1', { date: addDays(THURSDAY, 1) });
    const undo = (await service.clear('h', actor, [existing, other]))!;
    expect(writer.last).toEqual([
      { type: 'meal.delete', mealId: 'm0' },
      { type: 'meal.delete', mealId: 'm1' },
    ]);
    expect(undo.changes).toEqual([
      { type: 'meal.restore', meal: existing },
      { type: 'meal.restore', meal: other },
    ]);
    expect(await service.clear('h', actor, [])).toBeNull();
  });
});
