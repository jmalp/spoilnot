import { useState } from 'react';
import { messageId } from '../../core/errors';
import { useObservable } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import { formNumber, RecipeForms, type FormProblem, type RecipeForm } from '../../domain/recipes/form';
import type { Recipe } from '../../domain/recipes/models';
import { Button, Progress, TextArea, TextField, useSnackbars } from '../kit';
import { DetailScaffold, EmptyState } from '../Scaffold';
import './recipes.css';

/** Edit (DESIGN 5.5). Creating recipes stays in the Android app (DESIGN 18.1). */
export function RecipeEditScreen({ recipeId, onBack }: { recipeId: string; onBack: () => void }) {
  const { recipes } = useServices();
  const box = useObservable(recipes.box$, null);
  const recipe = box?.recipes.find((r) => r.id === recipeId) ?? null;
  return (
    <DetailScaffold title={str('recipe_edit')} onBack={onBack} testId="screen_recipe_edit">
      {box === null ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : recipe === null ? (
        <EmptyState icon="round/delete" message={str('recipe_gone')} />
      ) : (
        // Keyed: the form starts from the recipe once and keeps what is typed even if it changes elsewhere.
        <RecipeFormFields key={recipe.id} recipe={recipe} onSaved={onBack} />
      )}
    </DetailScaffold>
  );
}

function RecipeFormFields({ recipe, onSaved }: { recipe: Recipe; onSaved: () => void }) {
  const me = useHouseholdSession();
  const { recipeService } = useServices();
  const snackbars = useSnackbars();
  const [form, setForm] = useState<RecipeForm>(() => RecipeForms.from(recipe.content));
  const [showProblems, setShowProblems] = useState(false);
  const [saving, setSaving] = useState(false);
  const problems: Set<FormProblem> = showProblems ? RecipeForms.problems(form) : new Set();
  const set = (change: Partial<RecipeForm>) => setForm({ ...form, ...change });
  const numberError = (value: string) =>
    problems.has('number') && value.trim() !== '' && formNumber(value) === null
      ? str('recipe_number_invalid')
      : undefined;
  const save = () => {
    setShowProblems(true);
    if (RecipeForms.problems(form).size > 0 || saving) return;
    setSaving(true);
    recipeService
      .update(
        me.householdId,
        { uid: me.user.uid, name: me.displayName },
        recipe.id,
        RecipeForms.toContent(form, recipe.content),
      )
      .then(onSaved, (e: unknown) => snackbars.show(str(messageId(e))))
      .finally(() => setSaving(false));
  };
  return (
    <div className="sn-recipe-form">
      <TextField
        label={str('recipe_field_title')}
        value={form.title}
        onValueChange={(title) => set({ title })}
        autoCapitalize="sentences"
        error={
          problems.has('titleMissing')
            ? str('recipe_title_missing')
            : problems.has('titleTooLong')
              ? str('recipe_title_too_long')
              : undefined
        }
        data-testid="recipe_field_title"
      />
      <TextField
        label={str('recipe_field_image')}
        value={form.imageUrl}
        onValueChange={(imageUrl) => set({ imageUrl })}
        supportingText={str('optional')}
        error={problems.has('imageUrl') ? str('recipe_image_invalid') : undefined}
        type="url"
        inputMode="url"
        autoCapitalize="off"
      />
      <TextField
        label={str('recipe_field_servings')}
        value={form.servings}
        onValueChange={(servings) => set({ servings })}
        error={numberError(form.servings)}
        inputMode="numeric"
        data-testid="recipe_field_servings"
      />
      <div className="sn-recipe-form__times">
        {(
          [
            ['prepMinutes', 'recipe_field_prep'],
            ['cookMinutes', 'recipe_field_cook'],
            ['totalMinutes', 'recipe_field_total'],
          ] as const
        ).map(([field, label]) => (
          <TextField
            key={field}
            label={str(label)}
            value={form[field]}
            onValueChange={(v) => set({ [field]: v })}
            error={numberError(form[field])}
            inputMode="numeric"
          />
        ))}
      </div>
      <TextArea
        label={str('recipe_field_ingredients')}
        value={form.ingredients}
        onValueChange={(ingredients) => set({ ingredients })}
        supportingText={str('recipe_field_lines_hint')}
        autoCapitalize="sentences"
        data-testid="recipe_field_ingredients"
      />
      <TextArea
        label={str('recipe_field_steps')}
        value={form.steps}
        onValueChange={(steps) => set({ steps })}
        supportingText={str('recipe_field_lines_hint')}
        autoCapitalize="sentences"
        data-testid="recipe_field_steps"
      />
      <TextArea
        label={str('recipe_notes')}
        value={form.notes}
        onValueChange={(notes) => set({ notes })}
        supportingText={str('optional')}
        autoCapitalize="sentences"
        data-testid="recipe_field_notes"
      />
      <TextField
        label={str('recipe_field_tags')}
        value={form.tags}
        onValueChange={(tags) => set({ tags })}
        supportingText={str('recipe_field_tags_hint')}
        autoCapitalize="sentences"
      />
      <Button full large busy={saving} onClick={save} data-testid="recipe_save">
        {str('action_save')}
      </Button>
    </div>
  );
}
