// The follow-along tour for new users (DESIGN 5.8), ported from Tutorial.kt. The web app has no Add recipe (18.1),
// so its recipes step points at the recipe box instead of the Add recipe button.

/** The bottom-bar tabs, as the tour knows them (the shell maps them to its tabs). */
export type TourTab = 'home' | 'pantry' | 'plan' | 'recipes' | 'groceries';

/** What a tour step points at; the UI marks each element with a coach mark of the same key. */
export type TourTarget =
  | 'tour_home_cards'
  | 'tour_tab_pantry'
  | 'tour_add_item'
  | 'tour_pantry_list'
  | 'tour_tab_plan'
  | 'tour_tab_recipes'
  | 'tour_recipe_list'
  | 'tour_tab_groceries'
  | 'tour_quick_add'
  | 'tour_settings';

/**
 * One step: it points at [target] on [tab] (the shell shows that tab when the step starts). A step with
 * [advanceOnTab] also moves on when the user opens that tab, one with [advanceOnTap] when the user taps the target
 * itself: the tour follows along instead of only telling.
 */
export type TourStep = {
  id: TourStepId;
  tab: TourTab | null;
  target: TourTarget | null;
  advanceOnTab?: TourTab;
  advanceOnTap?: boolean;
};

export type TourStepId =
  | 'welcome'
  | 'home'
  | 'openPantry'
  | 'addItem'
  | 'pantryRows'
  | 'openPlan'
  | 'openRecipes'
  | 'recipeBox'
  | 'openGroceries'
  | 'quickAdd'
  | 'invite';

export const TOUR_STEPS: readonly TourStep[] = [
  { id: 'welcome', tab: null, target: null },
  { id: 'home', tab: 'home', target: 'tour_home_cards' },
  { id: 'openPantry', tab: 'home', target: 'tour_tab_pantry', advanceOnTab: 'pantry' },
  { id: 'addItem', tab: 'pantry', target: 'tour_add_item', advanceOnTap: true },
  { id: 'pantryRows', tab: 'pantry', target: 'tour_pantry_list' },
  { id: 'openPlan', tab: 'pantry', target: 'tour_tab_plan', advanceOnTab: 'plan' },
  { id: 'openRecipes', tab: 'plan', target: 'tour_tab_recipes', advanceOnTab: 'recipes' },
  // In the app: AddRecipe (save recipes from any website). The web app shows the saved ones (18.1).
  { id: 'recipeBox', tab: 'recipes', target: 'tour_recipe_list' },
  { id: 'openGroceries', tab: 'recipes', target: 'tour_tab_groceries', advanceOnTab: 'groceries' },
  { id: 'quickAdd', tab: 'groceries', target: 'tour_quick_add' },
  { id: 'invite', tab: 'groceries', target: 'tour_settings' },
];

export type TourEvent =
  | { type: 'next' }
  | { type: 'skip' }
  | { type: 'tabOpened'; tab: TourTab }
  | { type: 'targetTapped'; target: TourTarget };

const index = (id: TourStepId) => TOUR_STEPS.findIndex((s) => s.id === id);

/** The tour's state machine: pure, so every path is unit tested. */
export const Tour = {
  first: 'welcome' as TourStepId,

  /** Steps the user can see as "n of total": the welcome card is not counted. */
  countedSteps: TOUR_STEPS.length - 1,

  step: (id: TourStepId): TourStep => TOUR_STEPS[index(id)]!,

  isLast: (id: TourStepId) => index(id) === TOUR_STEPS.length - 1,

  /** "3 of 10" for [id]; null for the welcome card. */
  position: (id: TourStepId): number | null => (id === 'welcome' ? null : index(id)),

  /** The step after [event] on [id], or null when the tour is over (finished or skipped). */
  next(id: TourStepId, event: TourEvent): TourStepId | null {
    const step = Tour.step(id);
    const after = () => TOUR_STEPS[index(id) + 1]?.id ?? null;
    switch (event.type) {
      case 'skip':
        return null;
      case 'next':
        return after();
      case 'tabOpened':
        return step.advanceOnTab === event.tab ? after() : id;
      case 'targetTapped':
        return step.advanceOnTap && step.target === event.target ? after() : id;
    }
  },
};
