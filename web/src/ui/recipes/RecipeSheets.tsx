import { useState } from 'react';
import { plural, str } from '../../core/i18n';
import { GroceryRules, type GroceryList } from '../../domain/grocery/models';
import type { Product } from '../../domain/pantry/models';
import { NameKey } from '../../domain/pantry/rules';
import { CookLines, type CookLine } from '../../domain/recipes/cooking';
import { RecipeGrocery, selectedByDefault, type GroceryCandidate } from '../../domain/recipes/service';
import {
  BottomSheet,
  Button,
  Checkbox,
  Dialog,
  LabelChip,
  ListGroup,
  Progress,
  SpoilNotFilterChip,
  TextField,
} from '../kit';
import { FormSheetColumn, parseQuantity } from '../pantry/ItemForm';
import { amountText, formatQuantity, unitLabel } from '../pantry/labels';
import './recipes.css';

/**
 * "I cooked this" (DESIGN 5.5, after v1): the pantry products the recipe uses, each with the amount to take off.
 * Lines the pantry can count start chosen with the recipe's amount; the others start unchosen with the whole stock,
 * so choosing one means "I finished it". Amounts can be changed. [markCooked] (the meal plan, DESIGN 5.9) lets the
 * button work with nothing chosen.
 */
export function CookedSheet({
  lines,
  onUse,
  onDismiss,
  markCooked = false,
}: {
  lines: CookLine[];
  onUse: (amounts: Map<string, number>) => void;
  onDismiss: () => void;
  markCooked?: boolean;
}) {
  const [chosen, setChosen] = useState<Record<string, boolean>>({});
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const isChosen = (l: CookLine) => chosen[l.product.id] ?? CookLines.selectedByDefault(l);
  const amountOf = (l: CookLine) => amounts[l.product.id] ?? formatQuantity(CookLines.defaultAmount(l));
  const use = new Map<string, number>();
  for (const line of lines.filter(isChosen)) {
    const amount = parseQuantity(amountOf(line));
    if (amount !== null && amount > 0) use.set(line.product.id, amount);
  }
  const label =
    markCooked && use.size === 0 ? str('plan_mark_cooked') : plural('recipe_cooked_use', use.size, use.size);
  return (
    <BottomSheet label={str('recipe_cooked_title')} onDismiss={onDismiss} testId="sheet_cooked">
      <FormSheetColumn
        look
        title={str('recipe_cooked_title')}
        button={
          <Button
            primary
            data-testid="recipe_cooked_use"
            onClick={() => {
              if (use.size > 0 || markCooked) onUse(use);
            }}
          >
            {label}
          </Button>
        }
      >
        {lines.length === 0 ? (
          <p className="t-body-large">{str('recipe_cooked_none')}</p>
        ) : (
          <>
            <p className="t-body-medium c-on-surface-variant">{str('recipe_cooked_hint')}</p>
            <div className="sn-sheet-rows">
              {lines.map((line) => (
                <div key={line.product.id} className="sn-cook-row">
                  <label className="sn-cook-row__main">
                    <Checkbox
                      checked={isChosen(line)}
                      onCheckedChange={(c) => setChosen({ ...chosen, [line.product.id]: c })}
                    />
                    <span className="sn-cook-row__text">
                      <span className="t-row-title">{line.product.name}</span>
                      <span className="t-row-supporting c-on-surface-variant">
                        {str('recipe_cooked_have', amountText(line.inStock, line.product.unit))}
                      </span>
                      {line.lines.map((l) => (
                        <span key={l} className="t-body-small c-on-surface-variant">
                          {l}
                        </span>
                      ))}
                    </span>
                  </label>
                  {/* The amount in a field of the 2026-11 look, its unit as the label above it (DESIGN 19.10). */}
                  <TextField
                    label={unitLabel(line.product.unit)}
                    value={amountOf(line)}
                    onValueChange={(v) => {
                      setAmounts({ ...amounts, [line.product.id]: v });
                      setChosen({ ...chosen, [line.product.id]: true });
                    }}
                    inputMode="decimal"
                    // "Amount of Flour to use, in kg", not just the unit (after v1, review A11Y-07).
                    aria-label={str(
                      'recipe_cooked_amount_description',
                      line.product.name,
                      unitLabel(line.product.unit),
                    )}
                    className="sn-cook-row__amount"
                    data-testid={`cook_amount_${line.product.id}`}
                  />
                </div>
              ))}
            </div>
          </>
        )}
      </FormSheetColumn>
    </BottomSheet>
  );
}

/**
 * Choose all or some ingredients (DESIGN 5.5). Ones that look like pantry items start unchecked with "Probably in
 * pantry"; ones already on the list start unchecked with "On the list". "Change" says what a line goes on the list as.
 */
export function AddToGrocerySheet({
  lists,
  listId,
  candidates: base,
  products,
  loading,
  onChooseList,
  onAdd,
  onDismiss,
  title = str('recipe_add_to_list'),
}: {
  lists: GroceryList[];
  listId: string | null;
  candidates: GroceryCandidate[];
  products: Product[];
  loading: boolean;
  onChooseList: (id: string) => void;
  onAdd: (chosen: GroceryCandidate[]) => void;
  onDismiss: () => void;
  title?: string;
}) {
  // User choices and renames by line; lines not touched follow their default.
  const [choices, setChoices] = useState<Record<number, boolean>>({});
  const [renames, setRenames] = useState<Record<number, string>>({});
  const [editing, setEditing] = useState<number | null>(null);
  const candidates = base.map((c, i) => (renames[i] !== undefined ? RecipeGrocery.rename(c, renames[i], products) : c));
  const selected = (i: number) => choices[i] ?? selectedByDefault(candidates[i]!);
  const count = candidates.filter((_, i) => selected(i)).length;
  const all = candidates.every((_, i) => selected(i));
  return (
    <>
      <BottomSheet label={title} onDismiss={onDismiss} testId="sheet_add_to_list">
        <FormSheetColumn
          look
          title={title}
          button={
            <Button
              primary
              data-testid="recipe_add_items"
              // A button for nothing says what to do instead (after v1, UX-18: it read "Add 0 items").
              disabled={count === 0}
              onClick={() => {
                if (count > 0) onAdd(candidates.filter((_, i) => selected(i)));
              }}
            >
              {count > 0 ? plural('recipe_add_items', count, count) : str('recipe_add_none')}
            </Button>
          }
        >
          <ListChips lists={lists} listId={listId} onChoose={onChooseList} />
          <SelectAllRow
            all={all}
            onAll={(chosen) => setChoices(Object.fromEntries(candidates.map((_, i) => [i, chosen])))}
          />
          {loading ? (
            <div className="sn-centered">
              <Progress />
            </div>
          ) : (
            <ListGroup className="sn-group--field">
              {candidates.map((candidate, i) => (
                <CandidateRow
                  key={i}
                  candidate={candidate}
                  selected={selected(i)}
                  onChange={(v) => setChoices({ ...choices, [i]: v })}
                  onEdit={() => setEditing(i)}
                />
              ))}
            </ListGroup>
          )}
        </FormSheetColumn>
      </BottomSheet>
      {editing !== null && candidates[editing] && (
        <RenameDialog
          candidate={candidates[editing]}
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

/**
 * Which grocery list to add to, when there is more than one (DESIGN 19.10): filter chips on the field color, the chosen
 * one filled with a check. One list at a time: radio buttons in a group for VoiceOver (after v1, review A11Y-08).
 */
export function ListChips({
  lists,
  listId,
  onChoose,
}: {
  lists: GroceryList[];
  listId: string | null;
  onChoose: (id: string) => void;
}) {
  if (lists.length <= 1) return null;
  return (
    <div className="sn-list-chips" role="radiogroup">
      {lists.map((list) => (
        <SpoilNotFilterChip
          key={list.id}
          radio
          label={list.name}
          selected={list.id === listId}
          className="sn-filter-chip--field"
          onClick={() => onChoose(list.id)}
        />
      ))}
    </div>
  );
}

/** "Items that look like they're in your pantry start unchecked" with Select all or none beside it. */
export function SelectAllRow({ all, onAll }: { all: boolean; onAll: (all: boolean) => void }) {
  return (
    <div className="sn-sheet-hint">
      <span className="t-body-medium c-on-surface-variant">{str('recipe_pantry_hint')}</span>
      <Button variant="text" className="t-button-medium" onClick={() => onAll(!all)}>
        {str(all ? 'recipe_select_none' : 'recipe_select_all')}
      </Button>
    </div>
  );
}

/**
 * One recipe line to add (DESIGN 5.5, 19.5): a row of a group (on the field color in a sheet) with its checkbox, the
 * line, what it lands on the list as with Change, and Probably in pantry or On the list as a label chip.
 */
export function CandidateRow({
  candidate,
  selected,
  onChange,
  onEdit,
}: {
  candidate: GroceryCandidate;
  selected: boolean;
  onChange: (selected: boolean) => void;
  onEdit: () => void;
}) {
  // What lands on the list: the shopping name and amount, not the recipe's wording.
  const amount = candidate.quantity !== null ? ` × ${amountText(candidate.quantity, candidate.unit)}` : '';
  const tag = candidate.inPantry
    ? (['round/kitchen', str('recipe_probably_in_pantry')] as const)
    : candidate.onList
      ? (['round/playlist_add_check', str('recipe_on_the_list')] as const)
      : null;
  return (
    // The whole row ticks the line; Change, a button inside it, does not.
    <label className="sn-segment sn-candidate" data-testid="candidate">
      <Checkbox checked={selected} onCheckedChange={onChange} aria-label={candidate.ingredient.raw} />
      <div className="sn-candidate__text">
        <span className="t-row-title">{candidate.ingredient.raw}</span>
        <span className="sn-candidate__adds">
          <span className="t-row-supporting c-on-surface-variant">
            {str('recipe_adds_as', candidate.name + amount)}
          </span>
          <Button
            variant="text"
            large
            className="t-button-medium"
            onClick={onEdit}
            aria-label={str('recipe_change_item_description', candidate.ingredient.raw)}
          >
            {str('recipe_change_item')}
          </Button>
        </span>
        {tag && <LabelChip text={tag[1]} icon={tag[0]} />}
      </div>
    </label>
  );
}

const MAX_SUGGESTIONS = 4;

/**
 * "Change": what a recipe line goes on the list as. Pantry products that fit what is typed are offered; picking one
 * uses its name and usual amount, and the household remembers the choice for this ingredient.
 */
export function RenameDialog({
  candidate,
  products,
  onSave,
  onDismiss,
}: {
  candidate: GroceryCandidate;
  products: Product[];
  onSave: (name: string) => void;
  onDismiss: () => void;
}) {
  const [text, setText] = useState(candidate.name);
  const key = NameKey.of(text);
  const suggestions = products
    .filter((p) => key !== '' && p.nameKey !== key && p.nameKey.includes(key))
    .sort((a, b) => a.nameKey.length - b.nameKey.length)
    .slice(0, MAX_SUGGESTIONS);
  return (
    <Dialog
      title={str('recipe_change_title')}
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
            disabled={NameKey.clean(text) === ''}
            onClick={() => onSave(text)}
          >
            {str('action_save')}
          </Button>
        </>
      }
    >
      <div className="sn-dialog-fields">
        <p className="t-body-medium">{candidate.ingredient.raw}</p>
        <TextField
          label={str('field_name')}
          value={text}
          onValueChange={(v) => setText(v.slice(0, GroceryRules.MAX_NAME_LENGTH))}
          autoCapitalize="words"
          data-testid="rename_field"
        />
        {suggestions.length > 0 && (
          <>
            <p className="t-field-label c-on-surface-variant">{str('recipe_change_in_pantry')}</p>
            <div className="sn-rename-suggestions">
              {suggestions.map((p) => (
                <SpoilNotFilterChip
                  key={p.id}
                  label={p.name}
                  selected={false}
                  className="sn-filter-chip--dialog"
                  onClick={() => setText(p.name)}
                />
              ))}
            </div>
          </>
        )}
        <p className="t-body-small c-on-surface-variant">{str('recipe_change_remembered')}</p>
      </div>
    </Dialog>
  );
}
