import { describe, expect, it } from 'vitest';
import { meal, THURSDAY } from '../../domain/plan/fixtures';
import { PlanDrafts } from './planDrafts';

const roundTrip = (value: unknown) => PlanDrafts.parse(JSON.parse(JSON.stringify(value)));

describe('PlanDrafts (REL-09 on the web)', () => {
  it('Add meal starts empty and keeps its slot, cooks and typed meal through a reload', () => {
    const draft = { ...PlanDrafts.addMeal(THURSDAY, 'dinner'), cooks: ['u1', 'u2'], other: 'Pizza night' };
    expect(PlanDrafts.addMeal(THURSDAY, 'lunch')).toEqual({
      kind: 'add',
      date: THURSDAY,
      slot: 'lunch',
      cooks: [],
      other: '',
    });
    expect(roundTrip(draft)).toEqual(draft);
  });

  it("a meal's sheet starts from the meal and keeps servings, cooks and note through a reload", () => {
    const tacos = { ...meal('tacos'), servings: 4, cooks: ['u1'], note: 'Spicy' };
    expect(PlanDrafts.meal(tacos)).toEqual({
      kind: 'meal',
      mealId: 'tacos',
      date: THURSDAY,
      servings: 4,
      cooks: ['u1'],
      note: 'Spicy',
    });
    const edited = { ...PlanDrafts.meal(meal('soup')), servings: null, cooks: ['u2'] };
    expect(edited.note).toBe('');
    expect(roundTrip(edited)).toEqual(edited);
  });

  it("refuses what isn't a draft", () => {
    const add = PlanDrafts.addMeal(THURSDAY, 'dinner');
    const mealDraft = PlanDrafts.meal(meal('soup'));
    expect(PlanDrafts.parse(null)).toBeNull();
    expect(PlanDrafts.parse({ ...add, kind: 'other' })).toBeNull();
    expect(PlanDrafts.parse({ ...add, slot: 'brunch' })).toBeNull();
    expect(PlanDrafts.parse({ ...add, date: 'Thursday' })).toBeNull();
    expect(PlanDrafts.parse({ ...add, cooks: [1] })).toBeNull();
    expect(PlanDrafts.parse({ ...mealDraft, servings: 2.5 })).toBeNull();
    expect(PlanDrafts.parse({ ...mealDraft, servings: '2' })).toBeNull();
    expect(PlanDrafts.parse({ ...mealDraft, mealId: undefined })).toBeNull();
  });
});
