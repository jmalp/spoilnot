import { useState } from 'react';
import { plural, str } from '../../core/i18n';
import type { GroceryList } from '../../domain/grocery/models';
import type { Member } from '../../domain/household';
import type { Product } from '../../domain/pantry/models';
import { NameKey } from '../../domain/pantry/rules';
import { MEAL_SLOTS, MealPlanLimits, type MealSlot, type PlannedMeal } from '../../domain/plan/models';
import type { MealGroceries } from '../../domain/plan/service';
import type { UseItUpSuggestion } from '../../domain/recipes/cooking';
import type { Recipe } from '../../domain/recipes/models';
import { RecipeGrocery, selectedByDefault, type GroceryCandidate } from '../../domain/recipes/service';
import type { LocalDate } from '../../domain/time';
import { BottomSheet, Button, Dialog, FilterChip, Icon, IconButton, TextField } from '../kit';
import { FormSheetColumn } from '../pantry/ItemForm';
import { CandidateRow, RenameDialog } from '../recipes/RecipeSheets';
import { dayTitle, shortDay, slotLabel } from './components';
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
  return (
    <>
      <span className="t-label-large">{str('plan_whos_cooking')}</span>
      <div className="sn-chip-flow">
        {members.map((m) => {
          const selected = cooks.includes(m.uid);
          return (
            <FilterChip
              key={m.uid}
              label={m.uid === myUid ? str('plan_you') : m.displayName}
              selected={selected}
              data-testid={`plan_cook_${m.uid}`}
              onClick={() => onChange(selected ? cooks.filter((c) => c !== m.uid) : [...cooks, m.uid])}
            />
          );
        })}
      </div>
    </>
  );
}

/**
 * Adding to a slot (DESIGN 5.9): who cooks, then a saved recipe (searchable) or something else typed in ("Leftovers").
 * Tapping a recipe adds it at once. Recipes that use food expiring soon come first, under Use it up.
 */
export function AddMealSheet({
  date,
  slot,
  recipes,
  useItUp,
  members,
  myUid,
  onAddRecipe,
  onAddOther,
  onDismiss,
}: {
  date: LocalDate;
  slot: MealSlot;
  recipes: Recipe[];
  useItUp: UseItUpSuggestion[];
  members: Member[];
  myUid: string;
  onAddRecipe: (recipe: Recipe, cooks: string[]) => void;
  onAddOther: (title: string, cooks: string[]) => void;
  onDismiss: () => void;
}) {
  const [cooks, setCooks] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [other, setOther] = useState('');
  const key = NameKey.of(search);
  const shown = recipes.filter((r) => key === '' || NameKey.of(r.content.title).includes(key));
  const title = str('plan_add_title', slotLabel(slot), dayTitle(date));
  return (
    <BottomSheet label={title} onDismiss={onDismiss} testId="sheet_add_meal">
      <div className="sn-form-sheet__fields">
        <h2 className="t-headline-small">{title}</h2>
        <CookPicker members={members} myUid={myUid} cooks={cooks} onChange={setCooks} />
        <span className="t-label-large">{str('plan_something_else')}</span>
        <div className="sn-other-row">
          <TextField
            label={str('plan_something_else_hint')}
            value={other}
            onValueChange={(v) => setOther(v.slice(0, MealPlanLimits.TITLE))}
            autoCapitalize="sentences"
            enterKeyHint="done"
            data-testid="plan_other_field"
            onKeyDown={(e) => {
              if (e.key === 'Enter' && other.trim() !== '') onAddOther(other, cooks);
            }}
          />
          <Button
            variant="text"
            large
            disabled={other.trim() === ''}
            data-testid="plan_add_other"
            onClick={() => onAddOther(other, cooks)}
          >
            {str('plan_add')}
          </Button>
        </div>
        <hr className="sn-plan-divider" />
        {useItUp.length > 0 && key === '' && (
          <>
            <span className="t-label-large">{str('plan_use_it_up')}</span>
            <div className="sn-picker" data-testid="plan_use_it_up">
              {useItUp.map(({ recipe, uses }) => (
                <button
                  key={recipe.id}
                  type="button"
                  className="sn-picker__item m3-state"
                  onClick={() => onAddRecipe(recipe, cooks)}
                >
                  <span className="t-body-large sn-picker__title">{recipe.content.title}</span>
                  <span className="t-body-medium c-on-surface-variant">
                    {str('home_use_it_up_uses', uses.map((u) => u.product.name).join(', '))}
                  </span>
                </button>
              ))}
            </div>
          </>
        )}
        <TextField
          label={str('plan_search_recipes')}
          value={search}
          onValueChange={setSearch}
          type="search"
          enterKeyHint="search"
          data-testid="plan_recipe_search"
        />
        {recipes.length === 0 ? (
          <p className="t-body-medium c-on-surface-variant">{str('web_plan_no_recipes')}</p>
        ) : shown.length === 0 ? (
          <p className="t-body-medium c-on-surface-variant">{str('plan_no_matches')}</p>
        ) : (
          <div className="sn-picker">
            {shown.slice(0, PICKER_LIMIT).map((recipe) => (
              <button
                key={recipe.id}
                type="button"
                className="sn-picker__item m3-state"
                data-testid={`plan_pick_${recipe.id}`}
                onClick={() => onAddRecipe(recipe, cooks)}
              >
                <span className="t-body-large sn-picker__title">{recipe.content.title}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </BottomSheet>
  );
}

/** A planned meal: servings, who cooks and a note, Cooked, Move, its recipe, and removing it (with Undo). */
export function MealSheet({
  meal,
  members,
  myUid,
  recipeExists,
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
  members: Member[];
  myUid: string;
  recipeExists: boolean;
  weekDays: LocalDate[];
  onSave: (servings: number | null, cooks: string[], note: string | null) => void;
  onCook: () => void;
  onNotCooked: () => void;
  onMove: (date: LocalDate, slot: MealSlot) => void;
  onOpenRecipe: () => void;
  onRemove: () => void;
  onDismiss: () => void;
}) {
  const [servings, setServings] = useState(meal.servings);
  const [cooks, setCooks] = useState(meal.cooks);
  const [note, setNote] = useState(meal.note ?? '');
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
        <div className="sn-form-sheet__fields">
          <h2 className="t-headline-small">{meal.title}</h2>
          <p className="t-body-medium c-on-surface-variant">
            {str('plan_add_title', slotLabel(meal.slot), dayTitle(meal.date))}
          </p>
          <span className="t-label-large">{str('plan_servings_label')}</span>
          <div className="sn-meal-servings">
            <IconButton
              icon="round/remove"
              label={str('plan_fewer')}
              disabled={servings === null}
              onClick={() =>
                setServings(servings === null || servings <= MealPlanLimits.MIN_SERVINGS ? null : servings - 1)
              }
            />
            <span className="t-title-medium sn-meal-servings__value" data-testid="plan_servings">
              {servings ?? str('plan_servings_unset')}
            </span>
            <IconButton
              icon="round/add"
              label={str('plan_more')}
              disabled={(servings ?? 0) >= MealPlanLimits.MAX_SERVINGS}
              onClick={() => setServings(Math.min(MealPlanLimits.MAX_SERVINGS, (servings ?? 0) + 1))}
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
          <Button full large data-testid="plan_save_meal" onClick={() => onSave(servings, cooks, note)}>
            {str('plan_save')}
          </Button>
          {meal.cookedAt === null ? (
            <Button variant="outlined" full large icon="round/check" data-testid="plan_cook_meal" onClick={onCook}>
              {str('plan_cooked')}
            </Button>
          ) : (
            <div className="sn-cooked-row">
              <span className="c-primary">
                <Icon name="round/check_circle" size={20} />
              </span>
              <span className="t-body-large">{cookedBy ? str('plan_cooked_by', cookedBy) : str('plan_cooked')}</span>
              <Button variant="text" large data-testid="plan_not_cooked" onClick={onNotCooked}>
                {str('plan_not_cooked')}
              </Button>
            </div>
          )}
          <Button variant="outlined" full large data-testid="plan_move_meal" onClick={() => setMoving(true)}>
            {str('plan_move')}
          </Button>
          {recipeExists && (
            <Button variant="outlined" full large onClick={onOpenRecipe}>
              {str('plan_open_recipe')}
            </Button>
          )}
          <Button variant="text" full large danger data-testid="plan_remove_meal" onClick={onRemove}>
            {str('plan_remove')}
          </Button>
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

/** Move a meal to another day of its week or another slot; the choices are chips with a check. */
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
  return (
    <Dialog
      title={str('plan_move_title', meal.title)}
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button
            variant="text"
            disabled={day === meal.date && slot === meal.slot}
            data-testid="plan_move_confirm"
            onClick={() => onMove(day, slot)}
          >
            {str('plan_move')}
          </Button>
        </>
      }
    >
      <span className="t-label-large">{str('plan_move_day')}</span>
      <div className="sn-chip-flow">
        {/* "Fri 30" keeps the week to two rows; VoiceOver reads the whole date. */}
        {days.map((date) => (
          <FilterChip
            key={date}
            label={shortDay(date)}
            aria-label={dayTitle(date)}
            selected={date === day}
            data-testid={`plan_move_day_${date}`}
            onClick={() => setDay(date)}
          />
        ))}
      </div>
      <span className="t-label-large">{str('plan_move_slot')}</span>
      <div className="sn-chip-flow">
        {MEAL_SLOTS.map((s) => (
          <FilterChip
            key={s}
            label={slotLabel(s)}
            selected={s === slot}
            data-testid={`plan_move_slot_${s}`}
            onClick={() => setSlot(s)}
          />
        ))}
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
            <Button full large data-testid="plan_add_items" onClick={() => count > 0 && onAdd(chosen)}>
              {plural('recipe_add_items', count, count)}
            </Button>
          }
        >
          {lists.length > 1 && (
            <div className="sn-list-chips">
              {lists.map((list) => (
                <FilterChip
                  key={list.id}
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
