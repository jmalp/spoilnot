// Builders for recipe tests (the app's recipe() test helper).
import { IngredientParser } from './parser';
import { content, type Recipe } from './models';

export function recipe(
  id: string,
  {
    title = id,
    tags = [] as string[],
    favorite = false,
    ingredients = [] as string[],
    createdSecondsAgo = 0 as number | null,
    sourceUrl = null as string | null,
    servings = null as number | null,
  } = {},
): Recipe {
  return {
    id,
    content: content(title, {
      tags,
      ingredients: ingredients.map((line) => IngredientParser.parse(line)),
      sourceUrl,
      servings,
    }),
    favorite,
    rating: null,
    createdAt: createdSecondsAgo === null ? null : Date.parse('2026-10-17T12:00:00Z') - createdSecondsAgo * 1000,
    createdBy: null,
  };
}
