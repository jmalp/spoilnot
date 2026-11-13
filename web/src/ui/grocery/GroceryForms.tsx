import { useState } from 'react';
import { plural, str } from '../../core/i18n';
import { GroceryRules, type GroceryItem } from '../../domain/grocery/models';
import type { RestockReason } from '../../domain/grocery/restock';
import type { PantryEntry } from '../../domain/grocery/service';
import { CATEGORIES, unitMenu, type Category, type PantryData } from '../../domain/pantry/models';
import type { QuantityUnit } from '../../domain/pantry/models';
import type { LocalDate } from '../../domain/time';
import { BottomSheet, Button, Icon, SelectField, Switch, TextField } from '../kit';
import { DateChips, FormSheetColumn, largeText, LocationChoice, parseQuantity } from '../pantry/ItemForm';
import { amountText, categoryLabel, formatDate, formatQuantity, locationLabel, unitLabel } from '../pantry/labels';
import { AddToPantryDrafts, type AddToPantryDraft, type EntryEdit, type EntryLine } from './addToPantryDrafts';
import './grocery.css';

const valid = (line: EntryLine) => line.value.skip || (parseQuantity(line.value.quantity) ?? 0) > 0;

/**
 * Done shopping (DESIGN 5.3): one line per checked item summing up what will be added (amount, location, date from
 * the product's typical shelf life). Tapping a line opens its editor: quantity, unit, quick dates, location and Skip,
 * which removes the item without adding stock. Add all adds them in one write. What the person sets is kept in
 * [draft] by item id, so the current [items] can change under it (after v1, REL-08).
 */
export function AddToPantrySheet({
  items,
  pantry,
  today,
  imperialFirst,
  draft,
  onDraft,
  onConfirm,
  onDismiss,
}: {
  items: GroceryItem[];
  pantry: PantryData;
  today: LocalDate;
  imperialFirst: boolean;
  draft: AddToPantryDraft;
  onDraft: (change: (draft: AddToPantryDraft) => AddToPantryDraft) => void;
  onConfirm: (entries: PantryEntry[], skipped: GroceryItem[]) => void;
  onDismiss: () => void;
}) {
  // The current items with the edits kept for them: a check-off elsewhere adds a line and keeps the others.
  const lines = AddToPantryDrafts.lines(items, pantry, today, draft);
  const count = lines.filter((l) => !l.value.skip).length;
  const label =
    count === 0
      ? str('grocery_remove_skipped')
      : count === lines.length && count > 1
        ? plural('grocery_add_all_to_pantry', count, count)
        : plural('grocery_add_to_pantry', count, count);
  const confirm = () => {
    if (!lines.every(valid)) {
      // Show what needs fixing.
      const wrong = lines.filter((l) => !valid(l)).map((l) => l.item.id);
      onDraft((d) => AddToPantryDrafts.setExpanded(d, wrong, true));
      return;
    }
    onConfirm(
      lines
        .filter((l) => !l.value.skip)
        .map((l) => ({
          item: l.item,
          quantity: parseQuantity(l.value.quantity)!,
          unit: l.value.unit,
          expiryDate: l.value.expiry,
          location: l.value.location,
          dateType: 'use_by',
        })),
      lines.filter((l) => l.value.skip).map((l) => l.item),
    );
  };
  return (
    <BottomSheet label={str('grocery_add_to_pantry_title')} onDismiss={onDismiss} testId="sheet_add_to_pantry">
      <FormSheetColumn
        title={str('grocery_add_to_pantry_title')}
        button={
          <Button full large onClick={confirm} data-testid="button_confirm_add_to_pantry">
            {label}
          </Button>
        }
      >
        <div className="sn-entries">
          {lines.map((line) => (
            <EntryRow
              key={line.item.id}
              line={line}
              today={today}
              imperialFirst={imperialFirst}
              onChange={(change) =>
                onDraft((d) => AddToPantryDrafts.setEdit(d, line.item.id, { ...line.value, ...change }))
              }
              onToggle={() => onDraft((d) => AddToPantryDrafts.setExpanded(d, [line.item.id], !line.expanded))}
            />
          ))}
        </div>
      </FormSheetColumn>
    </BottomSheet>
  );
}

/** "2 · Fridge · Oct 26", "Skipped" or the problem, for one line of the sheet. */
function summary(d: EntryEdit, today: LocalDate): string {
  if (d.skip) return str('grocery_entry_skipped');
  const quantity = parseQuantity(d.quantity);
  if (quantity === null || quantity <= 0) return str('error_quantity');
  const date = d.expiry ? formatDate(d.expiry, today) : str('status_no_date');
  return str('grocery_entry_summary', amountText(quantity, d.unit), locationLabel(d.location), date);
}

function EntryRow({
  line,
  today,
  imperialFirst,
  onChange,
  onToggle,
}: {
  line: EntryLine;
  today: LocalDate;
  imperialFirst: boolean;
  onChange: (change: Partial<EntryEdit>) => void;
  onToggle: () => void;
}) {
  const id = line.item.id;
  const draft = { ...line.value, item: line.item, expanded: line.expanded, unitLocked: line.unitLocked };
  const quantity = parseQuantity(draft.quantity);
  return (
    <div className="sn-entry" data-testid={`entry_${id}`}>
      <button
        type="button"
        className="sn-entry__summary m3-state"
        aria-expanded={draft.expanded}
        aria-label={`${draft.item.name}, ${summary(draft, today)}. ${str(draft.expanded ? 'grocery_entry_done' : 'grocery_entry_edit')}`}
        onClick={onToggle}
      >
        <span className="sn-entry__text">
          <span className="t-title-medium">{draft.item.name}</span>
          <span className={`t-body-medium ${valid(line) ? 'c-on-surface-variant' : 'c-error'}`}>
            {summary(draft, today)}
          </span>
        </span>
        <Icon name={draft.expanded ? 'round/expand_less' : 'round/expand_more'} />
      </button>
      {draft.expanded && (
        <div className="sn-entry__editor">
          <label className="sn-entry__skip">
            <span className="t-body-medium sn-entry__skip-help">{str('grocery_skip_help')}</span>
            <span className="t-body-medium">{str('grocery_skip')}</span>
            <Switch
              checked={draft.skip}
              onCheckedChange={(skip) => onChange({ skip })}
              data-testid={`entry_skip_${id}`}
            />
          </label>
          {!draft.skip && (
            <>
              <div className="sn-entry__amount">
                <TextField
                  label={str('field_quantity')}
                  value={draft.quantity}
                  onValueChange={(q) => onChange({ quantity: q })}
                  inputMode="decimal"
                  enterKeyHint="next"
                  error={quantity === null || quantity <= 0 ? str('error_quantity') : undefined}
                  className="sn-entry__quantity"
                />
                <div className="sn-entry__unit">
                  <SelectField
                    label={str('field_unit')}
                    value={draft.unit}
                    options={unitMenu(imperialFirst)}
                    optionLabel={unitLabel}
                    onPick={(unit) => onChange({ unit })}
                    disabled={draft.unitLocked}
                    supportingText={draft.unitLocked ? str('unit_locked_short') : undefined}
                  />
                </div>
              </div>
              <span className="t-label-large">
                {draft.expiry ? formatDate(draft.expiry, today) : str('status_no_date')}
              </span>
              <DateChips
                value={draft.expiry}
                today={today}
                twoRows={largeText()}
                onChange={(expiry) => onChange({ expiry })}
              />
              <LocationChoice
                selected={draft.location}
                grid={largeText()}
                onPick={(location) => onChange({ location })}
              />
            </>
          )}
        </div>
      )}
    </div>
  );
}

/** Name (after v1), quantity, unit, note and aisle of one item. */
export function EditGroceryItemSheet({
  item,
  reason = null,
  imperialFirst,
  onSave,
  onDismiss,
}: {
  item: GroceryItem;
  /** Why the app put it here (after v1, UX-08): the row says only "Added automatically". */
  reason?: RestockReason | null;
  imperialFirst: boolean;
  onSave: (edit: {
    name: string;
    quantity: number | null;
    unit: QuantityUnit | null;
    note: string | null;
    category: Category | null;
  }) => void;
  onDismiss: () => void;
}) {
  const [name, setName] = useState(item.name);
  const [quantity, setQuantity] = useState(item.quantity === null ? '' : formatQuantity(item.quantity));
  const [unit, setUnit] = useState<QuantityUnit>(item.unit ?? 'pcs');
  const [note, setNote] = useState(item.note ?? '');
  const [category, setCategory] = useState<Category>(item.category ?? 'other');
  const qty = quantity.trim() === '' ? null : parseQuantity(quantity);
  const qtyError = quantity.trim() !== '' && (qty === null || qty <= 0) ? str('error_quantity') : undefined;
  return (
    <BottomSheet label={str('grocery_edit_item', item.name)} onDismiss={onDismiss} testId="sheet_edit_grocery_item">
      <FormSheetColumn
        title={str('grocery_edit_item', item.name)}
        button={
          <Button
            full
            large
            data-testid="button_save_grocery_item"
            onClick={() => {
              if (qtyError || name.trim() === '') return;
              onSave({ name, quantity: qty, unit: qty !== null ? unit : null, note, category });
            }}
          >
            {str('action_save')}
          </Button>
        }
      >
        {reason && (
          <p className="t-body-medium c-on-surface-variant sn-grocery-reason" data-testid="grocery_auto_reason">
            {reasonText(reason)}
          </p>
        )}
        <TextField
          label={str('field_name')}
          value={name}
          onValueChange={(v) => setName(v.slice(0, GroceryRules.MAX_NAME_LENGTH))}
          error={name.trim() === '' ? str('error_name_required') : undefined}
          autoCapitalize="sentences"
          enterKeyHint="next"
        />
        <TextField
          label={str('field_quantity')}
          value={quantity}
          onValueChange={setQuantity}
          error={qtyError}
          inputMode="decimal"
          enterKeyHint="next"
        />
        <SelectField
          label={str('field_unit')}
          value={unit}
          options={unitMenu(imperialFirst)}
          optionLabel={unitLabel}
          onPick={setUnit}
        />
        <SelectField
          label={str('field_category')}
          value={category}
          options={CATEGORIES}
          optionLabel={categoryLabel}
          onPick={setCategory}
        />
        <TextField
          label={str('field_notes')}
          value={note}
          onValueChange={(v) => setNote(v.slice(0, GroceryRules.MAX_NOTE_LENGTH))}
          autoCapitalize="sentences"
        />
      </FormSheetColumn>
    </BottomSheet>
  );
}

/** "Added automatically: you have 0 and keep at least 2." (after v1, UX-08) */
function reasonText(reason: RestockReason): string {
  return reason.kind === 'low'
    ? str('grocery_auto_reason', amountText(reason.have, reason.unit), amountText(reason.keepAtLeast, reason.unit))
    : str('grocery_auto_reason_gone');
}
