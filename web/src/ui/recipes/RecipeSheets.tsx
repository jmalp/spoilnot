import { useState } from 'react';
import { plural, str } from '../../core/i18n';
import { GroceryRules, type GroceryList } from '../../domain/grocery/models';
import type { Product } from '../../domain/pantry/models';
import { NameKey } from '../../domain/pantry/rules';
import { CookLines, type CookLine } from '../../domain/recipes/cooking';
import { RecipeGrocery, selectedByDefault, type GroceryCandidate } from '../../domain/recipes/service';
import { BottomSheet, Button, Checkbox, Dialog, FilterChip, Icon, Progress, TextField } from '../kit';
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
        title={str('recipe_cooked_title')}
        button={
          <Button
            full
            large
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
                      <span className="t-body-large">{line.product.name}</span>
                      <span className="t-body-medium c-on-surface-variant">
                        {str('recipe_cooked_have', amountText(line.inStock, line.product.unit))}
                      </span>
                      {line.lines.map((l) => (
                        <span key={l} className="t-body-small c-on-surface-variant">
                          {l}
                        </span>
                      ))}
                    </span>
                  </label>
                  <TextField
                    label={unitLabel(line.product.unit)}
                    value={amountOf(line)}
                    onValueChange={(v) => {
                      setAmounts({ ...amounts, [line.product.id]: v });
                      setChosen({ ...chosen, [line.product.id]: true });
                    }}
                    inputMode="decimal"
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
          title={title}
          button={
            <Button
              full
              large
              data-testid="recipe_add_items"
              onClick={() => {
                if (count > 0) onAdd(candidates.filter((_, i) => selected(i)));
              }}
            >
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
          <div className="sn-sheet-hint">
            <span className="t-body-medium c-on-surface-variant">{str('recipe_pantry_hint')}</span>
            <Button variant="text" onClick={() => setChoices(Object.fromEntries(candidates.map((_, i) => [i, !all])))}>
              {str(all ? 'recipe_select_none' : 'recipe_select_all')}
            </Button>
          </div>
          {loading ? (
            <div className="sn-centered">
              <Progress />
            </div>
          ) : (
            <div className="sn-sheet-rows">
              {candidates.map((candidate, i) => (
                <CandidateRow
                  key={i}
                  candidate={candidate}
                  selected={selected(i)}
                  onChange={(v) => setChoices({ ...choices, [i]: v })}
                  onEdit={() => setEditing(i)}
                />
              ))}
            </div>
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
    <label className="sn-candidate" data-testid="candidate">
      <Checkbox checked={selected} onCheckedChange={onChange} aria-label={candidate.ingredient.raw} />
      <div className="sn-candidate__text">
        <span className="t-body-large">{candidate.ingredient.raw}</span>
        <span className="sn-candidate__adds">
          <span className="t-body-medium c-on-surface-variant">{str('recipe_adds_as', candidate.name + amount)}</span>
          <Button
            variant="text"
            large
            onClick={onEdit}
            aria-label={str('recipe_change_item_description', candidate.ingredient.raw)}
          >
            {str('recipe_change_item')}
          </Button>
        </span>
        {tag && (
          <span className="sn-candidate__tag t-label-large">
            <Icon name={tag[0]} size={18} />
            {tag[1]}
          </span>
        )}
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
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button variant="text" disabled={NameKey.clean(text) === ''} onClick={() => onSave(text)}>
            {str('action_save')}
          </Button>
        </>
      }
    >
      <p className="t-body-medium">{candidate.ingredient.raw}</p>
      <TextField
        label={str('field_name')}
        value={text}
        onValueChange={(v) => setText(v.slice(0, GroceryRules.MAX_NAME_LENGTH))}
        data-testid="rename_field"
      />
      {suggestions.length > 0 && (
        <>
          <span className="t-label-large c-on-surface-variant">{str('recipe_change_in_pantry')}</span>
          <div className="sn-rename-suggestions">
            {suggestions.map((p) => (
              <Button key={p.id} variant="text" onClick={() => setText(p.name)}>
                {p.name}
              </Button>
            ))}
          </div>
        </>
      )}
      <p className="t-body-small c-on-surface-variant">{str('recipe_change_remembered')}</p>
    </Dialog>
  );
}
