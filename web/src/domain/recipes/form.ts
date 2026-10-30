// The Edit form as typed (DESIGN 5.5), ported from RecipeForm: one ingredient and one step per line.
import { RecipeLines } from './box';
import { content as emptyContent, RecipeLimits, type RecipeContent } from './models';
import { SourceUrl } from './text';

export type RecipeForm = {
  title: string;
  imageUrl: string;
  servings: string;
  prepMinutes: string;
  cookMinutes: string;
  totalMinutes: string;
  ingredients: string;
  steps: string;
  notes: string;
  tags: string;
};

export type FormProblem = 'titleMissing' | 'titleTooLong' | 'imageUrl' | 'number';

/** A whole number from 1 to 10,000, or null. */
export function formNumber(text: string): number | null {
  const t = text.trim();
  if (!/^[+-]?\d+$/.test(t)) return null;
  const n = Number(t);
  return n >= 1 && n <= 10_000 ? n : null;
}

export const RecipeForms = {
  empty: (): RecipeForm => ({
    title: '',
    imageUrl: '',
    servings: '',
    prepMinutes: '',
    cookMinutes: '',
    totalMinutes: '',
    ingredients: '',
    steps: '',
    notes: '',
    tags: '',
  }),

  problems(form: RecipeForm): Set<FormProblem> {
    const problems = new Set<FormProblem>();
    const t = form.title.trim();
    if (t === '') problems.add('titleMissing');
    if (t.length > RecipeLimits.TITLE) problems.add('titleTooLong');
    if (form.imageUrl.trim() !== '' && !SourceUrl.isWebUrl(form.imageUrl.trim())) problems.add('imageUrl');
    const numbers = [form.servings, form.prepMinutes, form.cookMinutes, form.totalMinutes];
    if (numbers.some((n) => n.trim() !== '' && formNumber(n) === null)) problems.add('number');
    return problems;
  },

  /** The content to save; [base] keeps what the form does not show (link, source, description, import method). */
  toContent(form: RecipeForm, base: RecipeContent | null): RecipeContent {
    const prep = formNumber(form.prepMinutes);
    const cook = formNumber(form.cookMinutes);
    const total = formNumber(form.totalMinutes) ?? (prep !== null || cook !== null ? (prep ?? 0) + (cook ?? 0) : null);
    const servings = formNumber(form.servings);
    const keepServingsText = base?.servings === servings;
    return {
      ...(base ?? emptyContent('')),
      title: form.title.trim(),
      imageUrl: form.imageUrl.trim() || null,
      servings,
      servingsText: keepServingsText ? (base?.servingsText ?? null) : null,
      prepMinutes: prep,
      cookMinutes: cook,
      totalMinutes: total,
      ingredients: RecipeLines.ingredients(form.ingredients),
      steps: RecipeLines.steps(form.steps),
      notes: form.notes.trim() || null,
      tags: RecipeLines.tags(form.tags),
    };
  },

  from: (c: RecipeContent): RecipeForm => ({
    title: c.title,
    imageUrl: c.imageUrl ?? '',
    servings: c.servings?.toString() ?? '',
    prepMinutes: c.prepMinutes?.toString() ?? '',
    cookMinutes: c.cookMinutes?.toString() ?? '',
    totalMinutes: c.totalMinutes?.toString() ?? '',
    ingredients: RecipeLines.ingredientsToText(c.ingredients),
    steps: RecipeLines.stepsToText(c.steps),
    notes: c.notes ?? '',
    tags: c.tags.join(', '),
  }),
};
