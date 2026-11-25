// The strings.xml words recipe logic writes into scaled lines (RecipeWords; Android's RecipeWordsModule).
import { str } from '../../core/i18n';
import type { RecipeWords } from '../../domain/recipes/models';

export const recipeWords = (): RecipeWords => ({ pinch: str('recipe_scaled_pinch') });
