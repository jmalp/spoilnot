// Ported from TourTest.
import { describe, expect, it } from 'vitest';
import { Tour, TOUR_STEPS, type TourStepId } from './tour';

describe('Tour', () => {
  it('Next walks every step in order and ends after the last', () => {
    let step: TourStepId | null = Tour.first;
    const seen: TourStepId[] = [];
    while (step !== null) {
      seen.push(step);
      step = Tour.next(step, { type: 'next' });
    }
    expect(seen).toEqual(TOUR_STEPS.map((s) => s.id));
  });

  it('opening the highlighted tab follows along, another tab does not', () => {
    expect(Tour.next('openPantry', { type: 'tabOpened', tab: 'pantry' })).toBe('addItem');
    expect(Tour.next('openPantry', { type: 'tabOpened', tab: 'groceries' })).toBe('openPantry');
    // A step without a tab to open ignores tab changes.
    expect(Tour.next('home', { type: 'tabOpened', tab: 'pantry' })).toBe('home');
  });

  it('tapping the highlighted button follows along, only where the step says so', () => {
    expect(Tour.next('addItem', { type: 'targetTapped', target: 'tour_add_item' })).toBe('pantryRows');
    expect(Tour.next('addItem', { type: 'targetTapped', target: 'tour_quick_add' })).toBe('addItem');
    // Typing in Quick add is not "done with this step": Next is.
    expect(Tour.next('quickAdd', { type: 'targetTapped', target: 'tour_quick_add' })).toBe('quickAdd');
  });

  it('skip ends the tour from any step', () => {
    TOUR_STEPS.forEach((s) => expect(Tour.next(s.id, { type: 'skip' })).toBeNull());
  });

  it('positions count the steps after the welcome card', () => {
    expect(Tour.position('welcome')).toBeNull();
    expect(Tour.position('home')).toBe(1);
    expect(Tour.position(TOUR_STEPS[TOUR_STEPS.length - 1]!.id)).toBe(Tour.countedSteps);
    expect(Tour.isLast('invite')).toBe(true);
    expect(Tour.isLast('quickAdd')).toBe(false);
  });

  it('every step that points at a tab button starts on another tab', () => {
    TOUR_STEPS.filter((s) => s.advanceOnTab).forEach((s) => expect(s.tab).not.toBe(s.advanceOnTab));
  });

  it('the recipes step shows the recipe box: the web app adds no recipes (DESIGN 18.1)', () => {
    expect(TOUR_STEPS.map((s) => s.target)).not.toContain('tour_add_recipe');
    expect(Tour.step('recipeBox')).toMatchObject({ tab: 'recipes', target: 'tour_recipe_list' });
    expect(Tour.next('openRecipes', { type: 'tabOpened', tab: 'recipes' })).toBe('recipeBox');
  });
});
