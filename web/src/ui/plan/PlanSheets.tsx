import { useId, useState } from 'react';
import { plural, str } from '../../core/i18n';
import type { GroceryList } from '../../domain/grocery/models';
import type { Member } from '../../domain/household';
import type { Product } from '../../domain/pantry/models';
import { NameKey } from '../../domain/pantry/rules';
import { MEAL_SLOTS, MealPlanLimits, type MealSlot, type PlannedMeal } from '../../domain/plan/models';
import type { MealGroceries } from '../../domain/plan/service';
import type { UseItUpSuggestion } from '../../domain/recipes/cooking';
import type { Recipe } from '../../domain/recipes/models';
import { Scaling } from '../../domain/recipes/parser';
import { RecipeGrocery, selectedByDefault, type GroceryCandidate } from '../../domain/recipes/service';
import type { LocalDate } from '../../domain/time';
import {
  BottomSheet,
  Button,
  CountStepper,
  Dialog,
  FilterChip,
  Icon,
  ListGroup,
  SearchField,
  SpoilNotFilterChip,
  TextField,
} from '../kit';
import { FormSheetColumn } from '../pantry/ItemForm';
import { CandidateRow, RenameDialog } from '../recipes/RecipeSheets';
import { dayTitle, shortDay, slotLabel } from './components';
import type { AddMealDraft, MealDraft } from './planDrafts';
import './plan.css';

/** Most recipes the picker lists at once; searching narrows it. */
const PICKER_LIMIT = 60;

/** "Who's cooking?": any number of members; the chips carry names, so the choice never depends on color. */
function CookPicker({
  members,
  myUid,
  cooks,
  onChange,
}: {
  members: Member[];
  myUid: string;
  cooks: string[];
  onChange: (cooks: string[]) => void;
}) {
  const labelId = useId();
  return (
    <div className="sn-field-group">
      <p className="t-field-label c-on-surface-variant" id={labelId}>
        {str('plan_whos_cooking')}
      </p>
      <div className="sn-chip-flow" role="group" aria-labelledby={labelId}>
        {members.map((m) => {
          const selected = cooks.includes(m.uid);
          return (
            <SpoilNotFilterChip
              key={m.uid}
              label={m.uid === myUid ? str('plan_you') : m.displayName}
              selected={selected}
              data-testid={`plan_cook_${m.uid}`}
              onClick={() => onChange(selected ? cooks.filter((c) => c !== m.uid) : [...cooks, m.uid])}
            />
          );
        })}
      </div>
    </div>
  );
}

/**
 * Adding to a slot (DESIGN 5.9, 19.11): who cooks, then something else typed in ("Leftovers") and added with its +, or
 * a saved recipe (searchable) added with a tap. Recipes that use food expiring soon come first, under Use it up; the
 * recipes are rows of a group on the field color. The slot, cooks and typed meal are the screen's [draft], kept
 * through a reload (after v1, REL-09).
 */
export function AddMealSheet({
  draft,
  onDraft,
  recipes,
  useItUp,
  members,
  myUid,
  onAddRecipe,
  onAddOther,
  onDismiss,
}: {
  draft: AddMealDraft;
  onDraft: (change: Partial<AddMealDraft>) => void;
  recipes: Recipe[];
  useItUp: UseItUpSuggestion[];
  members: Member[];
  myUid: string;
  onAddRecipe: (recipe: Recipe, cooks: string[]) => void;
  onAddOther: (title: string, cooks: string[]) => void;
  onDismiss: () => void;
}) {
  const { date, slot, cooks, other } = draft;
  const setCooks = (picked: string[]) => onDraft({ cooks: picked });
  const setOther = (text: string) => onDraft({ other: text });
  const [search, setSearch] = useState('');
  const key = NameKey.of(search);
  const shown = recipes.filter((r) => key === '' || NameKey.of(r.content.title).includes(key));
  const title = str('plan_add_title', slotLabel(slot), dayTitle(date));
  const addOther = () => {
    if (other.trim() !== '') onAddOther(other, cooks);
  };
  return (
    <BottomSheet label={title} onDismiss={onDismiss} testId="sheet_add_meal">
      <div className="sn-form-sheet sn-form-sheet--look">
        <div className="sn-form-sheet__fields sn-plan-sheet">
          <h2 className="t-sheet-title">{title}</h2>
          <CookPicker members={members} myUid={myUid} cooks={cooks} onChange={setCooks} />
          {/* Something else, with its own + at the end of the field (DESIGN 19.10). */}
          <TextField
            label={str('plan_something_else')}
            placeholder={str('plan_something_else_hint')}
            value={other}
            onValueChange={(v) => setOther(v.slice(0, MealPlanLimits.TITLE))}
            autoCapitalize="sentences"
            enterKeyHint="done"
            data-testid="plan_other_field"
            onKeyDown={(e) => {
              if (e.key === 'Enter') addOther();
            }}
            trailing={
              <button
                type="button"
                className="sn-pill-add m3-state"
                aria-label={str('plan_add')}
                title={str('plan_add')}
                disabled={other.trim() === ''}
                data-testid="plan_add_other"
                onClick={addOther}
              >
                <Icon name="round/add" />
              </button>
            }
          />
          {useItUp.length > 0 && key === '' && (
            <div className="sn-plan-sheet__group">
              <h3 className="t-group-heading c-primary sn-plan-sheet__heading">{str('plan_use_it_up')}</h3>
              <ListGroup data-testid="plan_use_it_up">
                {useItUp.map(({ recipe, uses }) => (
                  <button
                    key={recipe.id}
                    type="button"
                    className="sn-segment sn-plan-pick sn-plan-pick--two m3-state"
                    aria-description={str('plan_add')}
                    onClick={() => onAddRecipe(recipe, cooks)}
                  >
                    <span className="t-row-title sn-plan-pick__title">{recipe.content.title}</span>
                    <span className="t-row-supporting c-on-surface-variant">
                      {str('home_use_it_up_uses', uses.map((u) => u.product.name).join(', '))}
                    </span>
                  </button>
                ))}
              </ListGroup>
            </div>
          )}
          <div className="sn-plan-sheet__recipes">
            <SearchField
              value={search}
              onValueChange={setSearch}
              placeholder={str('plan_search_recipes')}
              clearLabel={str('action_clear_search')}
              testId="plan_recipe_search"
            />
            {recipes.length === 0 ? (
              <p className="t-body-medium c-on-surface-variant">{str('web_plan_no_recipes')}</p>
            ) : shown.length === 0 ? (
              <p className="t-body-medium c-on-surface-variant">{str('plan_no_matches')}</p>
            ) : (
              <ListGroup>
                {shown.slice(0, PICKER_LIMIT).map((recipe) => (
                  <button
                    key={recipe.id}
                    type="button"
                    className="sn-segment sn-plan-pick m3-state"
                    aria-description={str('plan_add')}
                    data-testid={`plan_pick_${recipe.id}`}
                    onClick={() => onAddRecipe(recipe, cooks)}
                  >
                    <span className="t-row-title sn-plan-pick__title">{recipe.content.title}</span>
                  </button>
                ))}
              </ListGroup>
            )}
          </div>
        </div>
      </div>
    </BottomSheet>
  );
}

/**
 * A planned meal (DESIGN 5.9, 19.11): servings as a stepper, who cooks and a note, then Save; Cooked, Move, its recipe
 * as tonal buttons, and removing it (with Undo).
 */
export function MealSheet({
  meal,
  draft,
  onDraft,
  members,
  myUid,
  recipeExists,
  recipeServings,
  weekDays,
  onSave,
  onCook,
  onNotCooked,
  onMove,
  onOpenRecipe,
  onRemove,
  onDismiss,
}: {
  meal: PlannedMeal;
  /** Servings, cooks and note as changed so far, kept through a reload (after v1, REL-09). */
  draft: MealDraft;
  onDraft: (change: Partial<MealDraft>) => void;
  members: Member[];
  myUid: string;
  recipeExists: boolean;
  /** The planned recipe's own servings, so the stepper can reach them (after v1, LOGIC-13). */
  recipeServings: number | null;
  weekDays: LocalDate[];
  onSave: (servings: number | null, cooks: string[], note: string | null) => void;
  onCook: () => void;
  onNotCooked: () => void;
  onMove: (date: LocalDate, slot: MealSlot) => void;
  onOpenRecipe: () => void;
  onRemove: () => void;
  onDismiss: () => void;
}) {
  const { servings, cooks, note } = draft;
  const setServings = (count: number | null) => onDraft({ servings: count });
  const maxServings = Scaling.maxServings(recipeServings, meal.servings);
  const setCooks = (picked: string[]) => onDraft({ cooks: picked });
  const setNote = (text: string) => onDraft({ note: text });
  const [moving, setMoving] = useState(false);
  const cookedBy =
    meal.cookedBy === null
      ? null
      : meal.cookedBy === myUid
        ? str('plan_you')
        : (members.find((m) => m.uid === meal.cookedBy)?.displayName ?? null);
  return (
    <>
      <BottomSheet label={meal.title} onDismiss={onDismiss} testId="sheet_meal">
        <div className="sn-form-sheet sn-form-sheet--look">
          <div className="sn-form-sheet__fields sn-plan-sheet">
            <div className="sn-plan-sheet__title">
              <h2 className="t-sheet-title">{meal.title}</h2>
              <p className="t-body-medium c-on-surface-variant">
                {str('plan_add_title', slotLabel(meal.slot), dayTitle(meal.date))}
              </p>
            </div>
            <div className="sn-stepper-field">
              <p className="t-field-label sn-stepper-field__label">{str('plan_servings_label')}</p>
              {/* A stepper (DESIGN 19.9): − below the least servings leaves them unset. */}
              <CountStepper
                value={servings === null ? str('plan_servings_unset') : String(servings)}
                minusLabel={str('plan_fewer')}
                plusLabel={str('plan_more')}
                onMinus={() =>
                  setServings(servings === null || servings <= MealPlanLimits.MIN_SERVINGS ? null : servings - 1)
                }
                onPlus={() => setServings(Math.min(maxServings, (servings ?? 0) + 1))}
                minusDisabled={servings === null}
                plusDisabled={(servings ?? 0) >= maxServings}
                valueTestId="plan_servings"
              />
            </div>
            <CookPicker members={members} myUid={myUid} cooks={cooks} onChange={setCooks} />
            <TextField
              label={str('plan_note')}
              value={note}
              onValueChange={(v) => setNote(v.slice(0, MealPlanLimits.NOTE))}
              autoCapitalize="sentences"
              enterKeyHint="done"
            />
            <div className="sn-plan-sheet__actions">
              <Button primary data-testid="plan_save_meal" onClick={() => onSave(servings, cooks, note)}>
                {str('plan_save')}
              </Button>
              {meal.cookedAt === null ? (
                <Button
                  variant="tonal"
                  full
                  large
                  icon="round/check"
                  className="t-button-medium"
                  data-testid="plan_cook_meal"
                  onClick={onCook}
                >
                  {str('plan_cooked')}
                </Button>
              ) : (
                <div className="sn-cooked-row">
                  <span className="c-primary">
                    <Icon name="round/check_circle" size={20} />
                  </span>
                  <span className="t-row-title">{cookedBy ? str('plan_cooked_by', cookedBy) : str('plan_cooked')}</span>
                  <Button
                    variant="text"
                    large
                    className="t-button-medium"
                    data-testid="plan_not_cooked"
                    onClick={onNotCooked}
                  >
                    {str('plan_not_cooked')}
                  </Button>
                </div>
              )}
              <Button
                variant="tonal"
                full
                large
                className="t-button-medium"
                data-testid="plan_move_meal"
                onClick={() => setMoving(true)}
              >
                {str('plan_move')}
              </Button>
              {recipeExists && (
                <Button variant="tonal" full large className="t-button-medium" onClick={onOpenRecipe}>
                  {str('plan_open_recipe')}
                </Button>
              )}
              <Button
                variant="text"
                full
                large
                danger
                className="t-button-medium"
                data-testid="plan_remove_meal"
                onClick={onRemove}
              >
                {str('plan_remove')}
              </Button>
            </div>
          </div>
        </div>
      </BottomSheet>
      {moving && (
        <MoveDialog
          meal={meal}
          days={weekDays}
          onMove={(date, slot) => {
            setMoving(false);
            onMove(date, slot);
          }}
          onDismiss={() => setMoving(false)}
        />
      )}
    </>
  );
}

/**
 * Move a meal to another day of its week or another slot (DESIGN 5.9, 19.11): the title in the dialog title style,
 * and the choices as chips with a check on the dialog's row color.
 */
function MoveDialog({
  meal,
  days,
  onMove,
  onDismiss,
}: {
  meal: PlannedMeal;
  days: LocalDate[];
  onMove: (date: LocalDate, slot: MealSlot) => void;
  onDismiss: () => void;
}) {
  const [day, setDay] = useState(meal.date);
  const [slot, setSlot] = useState(meal.slot);
  const dayId = useId();
  const slotId = useId();
  return (
    <Dialog
      title={str('plan_move_title', meal.title)}
      titleClassName="t-dialog-title"
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" className="t-button-medium" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button
            variant="text"
            className="t-button-medium"
            disabled={day === meal.date && slot === meal.slot}
            data-testid="plan_move_confirm"
            onClick={() => onMove(day, slot)}
          >
            {str('plan_move')}
          </Button>
        </>
      }
    >
      <div className="sn-plan-move">
        <div className="sn-field-group">
          <p className="t-field-label c-on-surface-variant" id={dayId}>
            {str('plan_move_day')}
          </p>
          <div className="sn-chip-flow" role="group" aria-labelledby={dayId}>
            {/* "Fri 30" keeps the week to two rows; VoiceOver reads the whole date. */}
            {days.map((date) => (
              <SpoilNotFilterChip
                key={date}
                label={shortDay(date)}
                aria-label={dayTitle(date)}
                selected={date === day}
                className="sn-filter-chip--dialog"
                data-testid={`plan_move_day_${date}`}
                onClick={() => setDay(date)}
              />
            ))}
          </div>
        </div>
        <div className="sn-field-group">
          <p className="t-field-label c-on-surface-variant" id={slotId}>
            {str('plan_move_slot')}
          </p>
          <div className="sn-chip-flow" role="group" aria-labelledby={slotId}>
            {MEAL_SLOTS.map((s) => (
              <SpoilNotFilterChip
                key={s}
                label={slotLabel(s)}
                selected={s === slot}
                className="sn-filter-chip--dialog"
                data-testid={`plan_move_slot_${s}`}
                onClick={() => setSlot(s)}
              />
            ))}
          </div>
        </div>
      </div>
    </Dialog>
  );
}

/** "Dinner · Thursday, Oct 29 · 4 servings". */
const mealWhen = (meal: PlannedMeal) =>
  [
    str('plan_add_title', slotLabel(meal.slot), dayTitle(meal.date)),
    meal.servings !== null ? plural('plan_servings', meal.servings, meal.servings) : null,
  ]
    .filter((p): p is string => p !== null)
    .join(' · ');

/**
 * "Add to grocery list" for the week: the ingredients of each recipe still to cook, under the meal they are for,
 * chosen the same way as from one recipe. Items land with the badge Meal plan.
 */
export function WeekGrocerySheet({
  lists,
  listId,
  sections,
  products,
  onChooseList,
  onAdd,
  onDismiss,
}: {
  lists: GroceryList[];
  listId: string | null;
  sections: MealGroceries[];
  products: Product[];
  onChooseList: (id: string) => void;
  onAdd: (chosen: GroceryCandidate[]) => void;
  onDismiss: () => void;
}) {
  // Choices and renames by "mealId/line"; lines not touched follow their default.
  const [choices, setChoices] = useState<Record<string, boolean>>({});
  const [renames, setRenames] = useState<Record<string, string>>({});
  const [editing, setEditing] = useState<string | null>(null);
  const shown = sections.map((section) => ({
    section,
    lines: section.candidates.map((c, i): [string, GroceryCandidate] => {
      const k = `${section.meal.id}/${i}`;
      return [k, renames[k] !== undefined ? RecipeGrocery.rename(c, renames[k], products) : c];
    }),
  }));
  const all = shown.flatMap((s) => s.lines);
  const selected = (k: string, c: GroceryCandidate) => choices[k] ?? selectedByDefault(c);
  const chosen = all.filter(([k, c]) => selected(k, c)).map(([, c]) => c);
  const count = RecipeGrocery.merge(chosen).length;
  const allChosen = all.every(([k, c]) => selected(k, c));
  const editingCandidate = editing === null ? null : (all.find(([k]) => k === editing)?.[1] ?? null);
  return (
    <>
      <BottomSheet label={str('plan_shop_title')} onDismiss={onDismiss} testId="sheet_week_grocery">
        <FormSheetColumn
          title={str('plan_shop_title')}
          button={
            <Button
              primary
              data-testid="plan_add_items"
              // A button for nothing says what to do instead (after v1, UX-18: it read "Add 0 items").
              disabled={count === 0}
              onClick={() => count > 0 && onAdd(chosen)}
            >
              {count > 0 ? plural('recipe_add_items', count, count) : str('recipe_add_none')}
            </Button>
          }
        >
          {lists.length > 1 && (
            // One list at a time: radio buttons in a group for VoiceOver (after v1, review A11Y-08).
            <div className="sn-list-chips" role="radiogroup">
              {lists.map((list) => (
                <FilterChip
                  key={list.id}
                  radio
                  label={list.name}
                  selected={list.id === listId}
                  onClick={() => onChooseList(list.id)}
                />
              ))}
            </div>
          )}
          {shown.length === 0 ? (
            <p className="t-body-large">{str('plan_shop_none')}</p>
          ) : (
            <>
              <div className="sn-sheet-hint">
                <span className="t-body-medium c-on-surface-variant">{str('recipe_pantry_hint')}</span>
                <Button
                  variant="text"
                  onClick={() => setChoices(Object.fromEntries(all.map(([k]) => [k, !allChosen])))}
                >
                  {str(allChosen ? 'recipe_select_none' : 'recipe_select_all')}
                </Button>
              </div>
              {shown.map(({ section, lines }) => (
                <div key={section.meal.id}>
                  <div className="sn-week-section">
                    <h3 className="t-title-medium">{section.recipe.content.title}</h3>
                    <span className="t-body-medium c-on-surface-variant">{mealWhen(section.meal)}</span>
                  </div>
                  {lines.map(([k, candidate]) => (
                    <CandidateRow
                      key={k}
                      candidate={candidate}
                      selected={selected(k, candidate)}
                      onChange={(v) => setChoices({ ...choices, [k]: v })}
                      onEdit={() => setEditing(k)}
                    />
                  ))}
                </div>
              ))}
            </>
          )}
        </FormSheetColumn>
      </BottomSheet>
      {editing !== null && editingCandidate && (
        <RenameDialog
          candidate={editingCandidate}
          products={products}
          onSave={(name) => {
            setRenames({ ...renames, [editing]: name });
            setChoices({ ...choices, [editing]: true });
            setEditing(null);
          }}
          onDismiss={() => setEditing(null)}
        />
      )}
    </>
  );
}
