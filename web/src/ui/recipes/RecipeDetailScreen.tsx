import { useEffect, useMemo, useState } from 'react';
import { catchError, distinctUntilChanged, of, switchMap } from 'rxjs';
import { messageId } from '../../core/errors';
import { useObservable } from '../../core/hooks';
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
import { Button, Dialog, Fab, Icon, IconButton, Menu, MenuItem, Progress, useSnackbars } from '../kit';
import { DetailScaffold, EmptyState } from '../Scaffold';
import {
  durationText,
  groupHeading,
  GroupTitle,
  IngredientChecklist,
  RecipeImage,
  SectionTitle,
  servingsLabel,
} from './components';
import { AddToGrocerySheet, CookedSheet } from './RecipeSheets';
import './recipes.css';

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
  const { recipes, recipeService, pantryService, writer } = useServices();
  const snackbars = useSnackbars();
  const box = useObservable(recipes.box$, null);
  const recipe = box?.recipes.find((r) => r.id === recipeId) ?? null;
  const actor = useMemo(() => ({ uid: me.user.uid, name: me.displayName }), [me]);
  const hid = me.householdId;
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [addingToList, setAddingToList] = useState(false);
  const [cookFactor, setCookFactor] = useState<number | null>(null);
  // The stepper starts at the recipe's servings and follows them when they change (rememberSaveable(servings)).
  const [servingsState, setServings] = useState<{ base: number | null; value: number | null }>({
    base: null,
    value: null,
  });
  const servings =
    servingsState.base === recipe?.content.servings ? servingsState.value : (recipe?.content.servings ?? null);

  useEffect(() => {
    const subscription = writer.errors$.subscribe((e) => snackbars.show(str(messageId(e))));
    return () => subscription.unsubscribe();
  }, [writer, snackbars]);

  const failed = (e: unknown) => snackbars.show(str(messageId(e)));
  const share = (r: Recipe) => void shareRecipe(r).then((copied) => copied && snackbars.show(str('web_recipe_copied')));

  return (
    <DetailScaffold
      title=""
      onBack={onBack}
      testId="screen_recipe_detail"
      fab={
        recipe && recipe.content.steps.length > 0 ? (
          <Fab
            icon="round/play_arrow"
            label={str('recipe_cook')}
            data-testid="recipe_cook"
            onClick={() => onCook(servings)}
          />
        ) : undefined
      }
      actions={
        recipe && (
          <>
            <IconButton
              icon={recipe.favorite ? 'round/star' : 'round/star_border'}
              label={str(recipe.favorite ? 'recipe_unfavorite' : 'recipe_favorite')}
              aria-pressed={recipe.favorite}
              data-testid="recipe_favorite"
              onClick={() => void recipeService.setFavorite(hid, actor, recipe, !recipe.favorite).catch(failed)}
            />
            <IconButton icon="round/edit" label={str('recipe_edit')} onClick={onEdit} data-testid="recipe_edit" />
            <OverflowMenu onShare={() => share(recipe)} onDelete={() => setConfirmDelete(true)} />
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
      {confirmDelete && recipe && (
        <Dialog
          title={str('recipe_delete_title')}
          onDismiss={() => setConfirmDelete(false)}
          actions={
            <>
              <Button variant="text" onClick={() => setConfirmDelete(false)}>
                {str('action_cancel')}
              </Button>
              <Button
                variant="text"
                data-testid="recipe_delete_confirm"
                onClick={() => {
                  setConfirmDelete(false);
                  // Deletes and leaves the screen; the snackbar offers Undo where the user lands.
                  recipeService.delete(hid, actor, recipe).then((undo) => {
                    onBack();
                    snackbars.showUndo(
                      str('recipe_deleted', recipe.content.title),
                      () => void recipeService.undo(hid, actor, undo),
                    );
                  }, failed);
                }}
              >
                {str('action_delete')}
              </Button>
            </>
          }
        >
          <p className="t-body-medium c-on-surface-variant">{str('recipe_delete_body', recipe.content.title)}</p>
        </Dialog>
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
  const people = servingsLabel(c.servings, c.servingsText);
  // Only http(s) links open (DESIGN 12.2): a stored link is data another phone wrote.
  const link = SourceUrl.isWebUrl(c.sourceUrl) ? c.sourceUrl : null;
  const site = SourceUrl.siteName(link);
  return (
    <div className="sn-recipe-detail">
      {c.imageUrl && <RecipeImage url={c.imageUrl} title={c.title} className="sn-recipe-detail__hero" />}
      <div className="sn-recipe-detail__body">
        <h1 className="t-headline-small" data-testid="recipe_title">
          {c.title}
        </h1>
        <div className="sn-info-chips">
          {time && <InfoChip label={str('recipe_total_time', time)} icon="round/schedule" />}
          {people && <InfoChip label={people} icon="round/people" />}
          {site && link && (
            <a className="sn-info-chip m3-state t-label-large" href={link} target="_blank" rel="noopener noreferrer">
              <Icon name="round/language" size={18} />
              {site}
            </a>
          )}
        </div>
        {c.sourceName && <p className="t-body-medium c-on-surface-variant">{str('recipe_by', c.sourceName)}</p>}
        {c.description && <p className="t-body-large">{c.description}</p>}
        {c.ingredients.length > 0 && (
          <>
            <SectionTitle text={str('recipe_ingredients')}>
              {c.servings !== null && servings !== null && (
                <ServingsStepper servings={servings} onChange={onServings} />
              )}
            </SectionTitle>
            <IngredientChecklist
              lines={c.ingredients.map((i) => [i.group, Scaling.line(i.raw, factor)])}
              checked={checked}
              onToggle={(index) => {
                const next = new Set(checked);
                if (next.has(index)) next.delete(index);
                else next.add(index);
                setChecked(next);
              }}
            />
            <div className="sn-recipe-actions">
              <Button
                variant="outlined"
                icon="round/add_shopping_cart"
                onClick={onAddToList}
                data-testid="recipe_add_to_list"
              >
                {str('recipe_add_to_list')}
              </Button>
              <Button
                variant="outlined"
                icon="round/soup_kitchen"
                onClick={() => onCooked(factor)}
                data-testid="recipe_cooked"
              >
                {str('recipe_cooked_this')}
              </Button>
            </div>
          </>
        )}
        {c.steps.length > 0 && (
          <>
            <SectionTitle text={str('recipe_steps')} />
            {c.steps.map((step, index) => {
              const heading = groupHeading(step.group, index > 0 ? c.steps[index - 1]!.group : null);
              return (
                <div key={index}>
                  {heading && <GroupTitle text={heading} />}
                  <div className="sn-recipe-step">
                    <span className="t-title-medium c-primary sn-recipe-step__number">{index + 1}.</span>
                    <span className="t-body-large">{step.text}</span>
                  </div>
                </div>
              );
            })}
          </>
        )}
        {c.notes && (
          <>
            <SectionTitle text={str('recipe_notes')} />
            <p className="t-body-large">{c.notes}</p>
          </>
        )}
        {link && (
          <a
            className="m3-button m3-button--text m3-button--with-icon m3-state sn-open-original"
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

function ServingsStepper({ servings, onChange }: { servings: number; onChange: (servings: number) => void }) {
  return (
    <div className="sn-stepper">
      <IconButton
        icon="round/remove"
        label={str('recipe_fewer_servings')}
        disabled={servings <= 1}
        onClick={() => onChange(servings - 1)}
      />
      <span className="t-title-medium sn-stepper__label" aria-live="polite" data-testid="recipe_servings">
        {plural('recipe_servings', servings, servings)}
      </span>
      <IconButton
        icon="round/add"
        label={str('recipe_more_servings')}
        disabled={servings >= 100}
        onClick={() => onChange(servings + 1)}
      />
    </div>
  );
}

/** A fact about the recipe, styled like a chip but not clickable. */
function InfoChip({ label, icon }: { label: string; icon: 'round/schedule' | 'round/people' }) {
  return (
    <span className="sn-info-chip t-label-large">
      <Icon name={icon} size={18} />
      {label}
    </span>
  );
}

function OverflowMenu({ onShare, onDelete }: { onShare: () => void; onDelete: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <span className="sn-anchor">
      <IconButton
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
      lines={Cooking.lines(recipe, data, factor)}
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
    useMemo(() => grocery.observeLists(hid).pipe(catchError(() => of([]))), [grocery, hid]),
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
          catchError(() => of([])),
        ),
      [grocery, hid, listId],
    ),
    null,
  );
  const pantryData = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(hid).pipe(catchError(() => of(NO_PANTRY))), [pantry, hid]),
    null,
  );
  const loading = lists === null || items === null || pantryData === null;
  return (
    <AddToGrocerySheet
      lists={lists ?? []}
      listId={listId}
      candidates={loading ? [] : RecipeGrocery.candidates(recipe, pantryData, items)}
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
