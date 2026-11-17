import { useMemo, useState } from 'react';
import { distinctUntilChanged, of, switchMap } from 'rxjs';
import { messageId } from '../../core/errors';
import { useObservable } from '../../core/hooks';
import { retryLoading } from '../../domain/load';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import type { GroceryItem, GroceryList } from '../../domain/grocery/models';
import type { PantryData } from '../../domain/pantry/models';
import { RecipeLines } from '../../domain/recipes/box';
import { Cooking } from '../../domain/recipes/cooking';
import type { Recipe } from '../../domain/recipes/models';
import { Scaling } from '../../domain/recipes/parser';
import { RecipeGrocery, type GroceryCandidate } from '../../domain/recipes/service';
import { SourceUrl } from '../../domain/recipes/text';
import { Button, CountStepper, Icon, Menu, MenuItem, Progress, RoundIconButton, useSnackbars } from '../kit';
import { DetailScaffold, EmptyState } from '../Scaffold';
import {
  durationText,
  FavoriteToggle,
  IngredientChecklist,
  InfoPill,
  RecipeImage,
  SectionTitle,
  servingsLabel,
  StepList,
} from './components';
import { AddToGrocerySheet, CookedSheet } from './RecipeSheets';
import './recipes.css';
import { recipeWords } from './words';

const NO_PANTRY: PantryData = { products: [], batches: [], fromCache: true };

/** Recipe detail (DESIGN 5.5): servings, the ingredient checklist, Add to grocery list, I cooked this, Cook. */
export function RecipeDetailScreen({
  recipeId,
  onBack,
  onEdit,
  onCook,
}: {
  recipeId: string;
  onBack: () => void;
  onEdit: () => void;
  onCook: (servings: number | null) => void;
}) {
  const me = useHouseholdSession();
  const { recipes, recipeService, pantryService } = useServices();
  const snackbars = useSnackbars();
  const box = useObservable(recipes.box$, null);
  const recipe = box?.recipes.find((r) => r.id === recipeId) ?? null;
  const actor = useMemo(() => ({ uid: me.user.uid, name: me.displayName }), [me]);
  const hid = me.householdId;
  const [addingToList, setAddingToList] = useState(false);
  const [cookFactor, setCookFactor] = useState<number | null>(null);
  // The stepper starts at the recipe's servings and follows them when they change (rememberSaveable(servings)).
  const [servingsState, setServings] = useState<{ base: number | null; value: number | null }>({
    base: null,
    value: null,
  });
  const servings =
    servingsState.base === recipe?.content.servings ? servingsState.value : (recipe?.content.servings ?? null);

  const failed = (e: unknown) => snackbars.show(str(messageId(e)));
  const share = (r: Recipe) => void shareRecipe(r).then((copied) => copied && snackbars.show(str('web_recipe_copied')));
  // Undo, no dialog (after v1, R13 and DESIGN 6.1): it leaves the screen and offers Undo where the user lands.
  const remove = (r: Recipe) =>
    recipeService.delete(hid, actor, r).then((undo) => {
      onBack();
      snackbars.showUndo(str('recipe_deleted', r.content.title), () => void recipeService.undo(hid, actor, undo));
    }, failed);

  return (
    <DetailScaffold
      title=""
      onBack={onBack}
      testId="screen_recipe_detail"
      // The hero runs under the status bar, with Back, the star, Edit and ⋮ as round buttons over it (DESIGN 19.12).
      header="overlay"
      // In a bar under the content, not floating (after v1, UX-12): the floating Cook button covered I cooked this.
      bottomBar={
        recipe && recipe.content.steps.length > 0 ? (
          <Button primary icon="round/play_arrow" data-testid="recipe_cook" onClick={() => onCook(servings)}>
            {str('recipe_cook')}
          </Button>
        ) : undefined
      }
      actions={
        recipe && (
          <>
            {/* One name with its state, "Favorite, selected", as on the cards (after v1, review A11Y-08). */}
            <FavoriteToggle
              favorite={recipe.favorite}
              testId="recipe_favorite"
              onToggle={() => void recipeService.setFavorite(hid, actor, recipe, !recipe.favorite).catch(failed)}
            />
            <RoundIconButton icon="round/edit" label={str('recipe_edit')} onClick={onEdit} data-testid="recipe_edit" />
            <OverflowMenu onShare={() => share(recipe)} onDelete={() => void remove(recipe)} />
          </>
        )
      }
    >
      {box === null ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : recipe === null ? (
        <EmptyState icon="round/delete" message={str('recipe_gone')} />
      ) : (
        <RecipeBody
          recipe={recipe}
          servings={servings}
          onServings={(value) => setServings({ base: recipe.content.servings, value })}
          onAddToList={() => setAddingToList(true)}
          onCooked={setCookFactor}
        />
      )}
      {cookFactor !== null && recipe && (
        <CookedSheetFor
          recipe={recipe}
          factor={cookFactor}
          onDismiss={() => setCookFactor(null)}
          onDone={(amounts, pantry) => {
            const [event, removeEvent] = recipeService.cookedEvent(recipe);
            pantryService.use(hid, actor, amounts, pantry, [event], [removeEvent]).then((undo) => {
              setCookFactor(null);
              const message = str('recipe_cooked_done', recipe.content.title);
              if (undo) snackbars.showUndo(message, () => void pantryService.undo(hid, actor, undo));
              else snackbars.show(message);
            }, failed);
          }}
        />
      )}
      {addingToList && recipe && (
        <AddToListFor
          recipe={recipe}
          onDismiss={() => setAddingToList(false)}
          onAdd={(list, chosen, pantry) =>
            void recipeService.addToGrocery(hid, actor, list.id, recipe, chosen, pantry, list.name).then((count) => {
              setAddingToList(false);
              if (count > 0) snackbars.show(str('recipe_added_to_list', list.name));
            }, failed)
          }
        />
      )}
    </DetailScaffold>
  );
}

/**
 * A recipe (DESIGN 5.5, 19.12): the photo or the letter block, then the page with the title, the facts as pills, the
 * ingredients as a checklist with the servings stepper beside their heading, Add to grocery list and I cooked this,
 * and the steps with their numbers in leaves.
 */
function RecipeBody({
  recipe,
  servings,
  onServings,
  onAddToList,
  onCooked,
}: {
  recipe: Recipe;
  servings: number | null;
  onServings: (servings: number) => void;
  onAddToList: () => void;
  onCooked: (factor: number) => void;
}) {
  const c = recipe.content;
  const factor = Scaling.factor(c.servings, servings);
  const [checked, setChecked] = useState<ReadonlySet<number>>(new Set());
  const time = durationText(c.totalMinutes);
  // One servings control (after v1, R9): the pill only when there is no stepper to show the number.
  const stepper = c.ingredients.length > 0 && c.servings !== null && servings !== null;
  const people = stepper ? null : servingsLabel(c.servings, c.servingsText);
  // Only http(s) links open (DESIGN 12.2): a stored link is data another phone wrote.
  const link = SourceUrl.isWebUrl(c.sourceUrl) ? c.sourceUrl : null;
  const site = SourceUrl.siteName(link);
  return (
    <div className="sn-recipe-detail">
      <RecipeImage url={c.imageUrl} title={c.title} size="header" className="sn-recipe-detail__hero" />
      <div className="sn-recipe-detail__page">
        <div className="sn-recipe-detail__head">
          <h1 className="t-screen-title" data-testid="recipe_title">
            {c.title}
          </h1>
          <div className="sn-info-pills">
            {time && <InfoPill label={str('recipe_total_time', time)} icon="round/schedule" />}
            {people && <InfoPill label={people} icon="round/people" />}
            {site && link && (
              <a
                className="sn-info-pill m3-state t-button-medium"
                href={link}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Icon name="round/language" size={18} />
                {site}
              </a>
            )}
          </div>
          {/* One source line (after v1, R10): "By …" only when it names someone other than the site. */}
          {c.sourceName && !SourceUrl.namesSite(c.sourceName, SourceUrl.siteName(c.sourceUrl)) && (
            <p className="t-body-medium c-on-surface-variant">{str('recipe_by', c.sourceName)}</p>
          )}
          {c.description && <p className="t-body-large">{c.description}</p>}
        </div>
        {c.ingredients.length > 0 && (
          <section className="sn-recipe-block">
            <SectionTitle text={str('recipe_ingredients')}>
              {stepper && (
                <div className="sn-servings-stepper">
                  {/* The servings beside Ingredients (DESIGN 19.9), announced as they change. */}
                  <CountStepper
                    value={plural('recipe_servings', servings, servings)}
                    minusLabel={str('recipe_fewer_servings')}
                    plusLabel={str('recipe_more_servings')}
                    onMinus={() => onServings(servings - 1)}
                    onPlus={() => onServings(servings + 1)}
                    minusDisabled={servings <= 1}
                    plusDisabled={servings >= Scaling.maxServings(c.servings)}
                    valueTestId="recipe_servings"
                    announce
                  />
                </div>
              )}
            </SectionTitle>
            <IngredientChecklist
              lines={c.ingredients.map((i) => [i.group, Scaling.line(i.raw, factor, str('recipe_scaled_pinch'))])}
              checked={checked}
              onToggle={(index) => {
                const next = new Set(checked);
                if (next.has(index)) next.delete(index);
                else next.add(index);
                setChecked(next);
              }}
            />
            {/* Tonal buttons side by side, one under the other from 1.5x text (DESIGN 19.9). */}
            <div className="sn-recipe-actions">
              <Button
                variant="tonal"
                large
                icon="round/add_shopping_cart"
                className="t-button-medium"
                onClick={onAddToList}
                data-testid="recipe_add_to_list"
              >
                {str('recipe_add_to_list')}
              </Button>
              <Button
                variant="tonal"
                large
                icon="round/soup_kitchen"
                className="t-button-medium"
                onClick={() => onCooked(factor)}
                data-testid="recipe_cooked"
              >
                {str('recipe_cooked_this')}
              </Button>
            </div>
          </section>
        )}
        {c.steps.length > 0 && (
          <section className="sn-recipe-block">
            <SectionTitle text={str('recipe_steps')} />
            <StepList steps={c.steps.map((step) => [step.group, step.text])} />
          </section>
        )}
        {c.notes && (
          <section className="sn-recipe-block">
            <SectionTitle text={str('recipe_notes')} />
            <p className="t-body-large sn-recipe-notes">{c.notes}</p>
          </section>
        )}
        {link && (
          <a
            className="m3-button m3-button--text m3-button--with-icon m3-state sn-open-original t-button-medium"
            href={link}
            target="_blank"
            rel="noopener noreferrer"
            data-testid="recipe_open_original"
          >
            <Icon name="round/open_in_new" />
            {str('recipe_open_original')}
          </a>
        )}
      </div>
    </div>
  );
}

function OverflowMenu({ onShare, onDelete }: { onShare: () => void; onDelete: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <span className="sn-anchor">
      <RoundIconButton
        icon="round/more_vert"
        label={str('more_options')}
        onClick={() => setOpen(true)}
        data-testid="recipe_more"
      />
      <Menu open={open} onDismiss={() => setOpen(false)}>
        <MenuItem
          label={str('recipe_share')}
          leading={<Icon name="round/share" />}
          onClick={() => {
            setOpen(false);
            onShare();
          }}
        />
        <MenuItem
          label={str('action_delete')}
          leading={<Icon name="round/delete" />}
          testId="recipe_delete"
          onClick={() => {
            setOpen(false);
            onDelete();
          }}
        />
      </Menu>
    </span>
  );
}

/** The "I cooked this" sheet with the pantry it reads while open (DESIGN 9.1). */
function CookedSheetFor({
  recipe,
  factor,
  onDismiss,
  onDone,
}: {
  recipe: Recipe;
  factor: number;
  onDismiss: () => void;
  onDone: (amounts: Map<string, number>, pantry: PantryData) => void;
}) {
  const me = useHouseholdSession();
  const { pantry } = useServices();
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(me.householdId), [pantry, me.householdId]),
    null,
  );
  if (data === null) return null;
  return (
    <CookedSheet
      lines={Cooking.lines(recipe, data, recipeWords(), factor)}
      onDismiss={onDismiss}
      onUse={(amounts) => onDone(amounts, data)}
    />
  );
}

/** The Add to grocery list sheet with the lists, items and pantry it reads while open (DESIGN 9.1). */
function AddToListFor({
  recipe,
  onDismiss,
  onAdd,
}: {
  recipe: Recipe;
  onDismiss: () => void;
  onAdd: (list: GroceryList, chosen: GroceryCandidate[], pantry: PantryData) => void;
}) {
  const me = useHouseholdSession();
  const { grocery, pantry } = useServices();
  const hid = me.householdId;
  const [chosenList, setChosenList] = useState<string | null>(null);
  const lists = useObservable<GroceryList[] | null>(
    useMemo(() => grocery.observeLists(hid).pipe(retryLoading<GroceryList[]>(undefined, [])), [grocery, hid]),
    null,
  );
  const list = lists?.find((l) => l.id === chosenList) ?? lists?.find((l) => l.isDefault) ?? lists?.[0] ?? null;
  const listId = list?.id ?? null;
  const items = useObservable<GroceryItem[] | null>(
    useMemo(
      () =>
        of(listId).pipe(
          distinctUntilChanged(),
          switchMap((id) => (id === null ? of([]) : grocery.observeItems(hid, id))),
          retryLoading<GroceryItem[]>(undefined, []),
        ),
      [grocery, hid, listId],
    ),
    null,
  );
  const pantryData = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(hid).pipe(retryLoading(undefined, NO_PANTRY)), [pantry, hid]),
    null,
  );
  const loading = lists === null || items === null || pantryData === null;
  return (
    <AddToGrocerySheet
      lists={lists ?? []}
      listId={listId}
      candidates={loading ? [] : RecipeGrocery.candidates(recipe, pantryData, items, recipeWords())}
      products={pantryData?.products ?? []}
      loading={loading}
      onChooseList={setChosenList}
      onDismiss={onDismiss}
      onAdd={(chosen) => {
        if (list && pantryData) onAdd(list, chosen, pantryData);
      }}
    />
  );
}

/**
 * The iPhone share sheet with the recipe; without one, the clipboard (returns true then). Imported recipes are shared
 * as their title and link, never re-published (DESIGN 11.6); recipes typed in by the household are shared in full.
 */
async function shareRecipe(recipe: Recipe): Promise<boolean> {
  const c = recipe.content;
  const text =
    c.importMethod === 'web' && c.sourceUrl
      ? `${c.title}\n${c.sourceUrl}`
      : [
          c.title,
          RecipeLines.ingredientsToText(c.ingredients) || null,
          c.steps.map((s, i) => `${i + 1}. ${s.text}`).join('\n') || null,
          c.notes,
          c.sourceUrl,
        ]
          .filter((part): part is string => !!part)
          .join('\n\n');
  if (typeof navigator.share === 'function') {
    await navigator.share({ title: c.title, text }).catch(() => undefined);
    return false;
  }
  await navigator.clipboard?.writeText(text);
  return true;
}
