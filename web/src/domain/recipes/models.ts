// The recipe box (DESIGN 5.5, 8.2), ported from feature/recipes/domain/RecipeModels. Recipes are not added on the
// web (DESIGN 18.1); the ones saved on Android show, edit and cook the same way.
import type { Observable } from 'rxjs';
import type { HouseholdChange } from '../write';

/** One ingredient line. [raw] is always kept; the parsed fields are best effort (DESIGN 11.5). */
export type Ingredient = {
  raw: string;
  qty: number | null;
  unit: string | null;
  name: string | null;
  group: string | null;
};

export const ingredient = (raw: string, rest: Partial<Omit<Ingredient, 'raw'>> = {}): Ingredient => ({
  raw,
  qty: null,
  unit: null,
  name: null,
  group: null,
  ...rest,
});

export type Step = { text: string; group: string | null };

export type ImportMethod = 'manual' | 'web';
export const importMethodOf = (value: unknown): ImportMethod => (value === 'web' ? 'web' : 'manual');

/** What a person can edit or an import can fill in. */
export type RecipeContent = {
  title: string;
  sourceUrl: string | null;
  sourceName: string | null;
  imageUrl: string | null;
  description: string | null;
  servings: number | null;
  servingsText: string | null;
  prepMinutes: number | null;
  cookMinutes: number | null;
  totalMinutes: number | null;
  ingredients: Ingredient[];
  steps: Step[];
  notes: string | null;
  tags: string[];
  importMethod: ImportMethod;
};

export const content = (title: string, rest: Partial<RecipeContent> = {}): RecipeContent => ({
  title,
  sourceUrl: null,
  sourceName: null,
  imageUrl: null,
  description: null,
  servings: null,
  servingsText: null,
  prepMinutes: null,
  cookMinutes: null,
  totalMinutes: null,
  ingredients: [],
  steps: [],
  notes: null,
  tags: [],
  importMethod: 'manual',
  ...rest,
});

/** households/{hid}/recipes/{recipeId}. */
export type Recipe = {
  id: string;
  content: RecipeContent;
  favorite: boolean;
  rating: number | null;
  /** Epoch milliseconds; estimated while the server timestamp is pending, null when unknown. */
  createdAt: number | null;
  createdBy: string | null;
};

/** One write in a recipe plan, applied by the HouseholdWriter (together with grocery changes when needed). */
export type RecipeChange =
  | { type: 'recipe.update'; recipeId: string; content: RecipeContent }
  | { type: 'recipe.setFavorite'; recipeId: string; favorite: boolean }
  | { type: 'recipe.delete'; recipeId: string }
  /** Puts a deleted recipe back under its old id (Undo). */
  | { type: 'recipe.restore'; recipe: Recipe }
  /** "Sam cooked Green curry" in Recent activity (after v1). */
  | { type: 'recipe.logCooked'; eventId: string; recipeId: string; title: string };

export const isRecipeChange = (change: HouseholdChange): change is RecipeChange => change.type.startsWith('recipe.');

/** Limits from DESIGN 11.6, also enforced by the security rules. */
export const RecipeLimits = {
  TITLE: 200,
  INGREDIENT: 300,
  STEP: 2_000,
  MAX_INGREDIENTS: 100,
  MAX_STEPS: 100,
  MAX_TAGS: 8,
  TAG: 40,
  DESCRIPTION: 2_000,
  NOTES: 5_000,
  URL: 2_000,
  SOURCE_NAME: 100,
  SERVINGS_TEXT: 100,
};

export type RecipeBoxData = { recipes: Recipe[]; fromCache: boolean };

export interface RecipeRepository {
  /** The whole collection, from the first visit to a recipe screen (DESIGN 9.1). */
  observe(householdId: string): Observable<RecipeBoxData>;
}
