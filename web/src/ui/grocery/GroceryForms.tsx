import { useState } from 'react';
import { plural, str } from '../../core/i18n';
import { GroceryRules, type GroceryItem } from '../../domain/grocery/models';
import type { PantryEntry } from '../../domain/grocery/service';
import { CATEGORIES, unitMenu, type Category, type Location, type PantryData } from '../../domain/pantry/models';
import type { QuantityUnit } from '../../domain/pantry/models';
import { NameKey, ShelfLife } from '../../domain/pantry/rules';
import type { LocalDate } from '../../domain/time';
import { BottomSheet, Button, Icon, SelectField, Switch, TextField } from '../kit';
import { DateChips, FormSheetColumn, largeText, LocationChoice, parseQuantity } from '../pantry/ItemForm';
import { amountText, categoryLabel, formatDate, formatQuantity, locationLabel, unitLabel } from '../pantry/labels';
import './grocery.css';

/** One editable line of the Add to pantry sheet. */
type EntryDraft = {
  item: GroceryItem;
  quantity: string;
  unit: QuantityUnit;
  expiry: LocalDate | null;
  location: Location;
  skip: boolean;
  /** Showing the full editor instead of the one-line summary (after v1). */
  expanded: boolean;
};

const valid = (d: EntryDraft) => d.skip || (parseQuantity(d.quantity) ?? 0) > 0;

/**
 * Done shopping (DESIGN 5.3): one line per checked item summing up what will be added (amount, location, date from
 * the product's typical shelf life). Tapping a line opens its editor: quantity, unit, quick dates, location and Skip,
 * which removes the item without adding stock. Add all adds them in one write.
 */
export function AddToPantrySheet({
  items,
  pantry,
  today,
  imperialFirst,
  onConfirm,
  onDismiss,
}: {
  items: GroceryItem[];
  pantry: PantryData;
  today: LocalDate;
  imperialFirst: boolean;
  onConfirm: (entries: PantryEntry[], skipped: GroceryItem[]) => void;
  onDismiss: () => void;
}) {
  const [drafts, setDrafts] = useState<EntryDraft[]>(() =>
    items.map((item) => {
      const product =
        pantry.products.find((p) => p.id === item.productId) ??
        pantry.products.find((p) => p.nameKey === NameKey.of(item.name)) ??
        null;
      return {
        item,
        quantity: formatQuantity(item.quantity ?? 1),
        unit: item.unit ?? product?.unit ?? 'pcs',
        expiry: ShelfLife.suggestExpiry(product?.typicalShelfLifeDays ?? null, today),
        location: product?.defaultLocation ?? 'fridge',
        skip: false,
        expanded: false,
      };
    }),
  );
  const update = (id: string, change: Partial<EntryDraft>) =>
    setDrafts((all) => all.map((d) => (d.item.id === id ? { ...d, ...change } : d)));
  const count = drafts.filter((d) => !d.skip).length;
  const label =
    count === 0
      ? str('grocery_remove_skipped')
      : count === drafts.length && count > 1
        ? plural('grocery_add_all_to_pantry', count, count)
        : plural('grocery_add_to_pantry', count, count);
  const confirm = () => {
    if (!drafts.every(valid)) {
      // Show what needs fixing.
      setDrafts((all) => all.map((d) => (valid(d) ? d : { ...d, expanded: true })));
      return;
    }
    onConfirm(
      drafts
        .filter((d) => !d.skip)
        .map((d) => ({
          item: d.item,
          quantity: parseQuantity(d.quantity)!,
          unit: d.unit,
          expiryDate: d.expiry,
          location: d.location,
          dateType: 'use_by',
        })),
      drafts.filter((d) => d.skip).map((d) => d.item),
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
          {drafts.map((d) => (
            <EntryRow
              key={d.item.id}
              draft={d}
              today={today}
              imperialFirst={imperialFirst}
              onChange={(change) => update(d.item.id, change)}
            />
          ))}
        </div>
      </FormSheetColumn>
    </BottomSheet>
  );
}

/** "2 · Fridge · Oct 26", "Skipped" or the problem, for one line of the sheet. */
function summary(d: EntryDraft, today: LocalDate): string {
  if (d.skip) return str('grocery_entry_skipped');
  const quantity = parseQuantity(d.quantity);
  if (quantity === null || quantity <= 0) return str('error_quantity');
  const date = d.expiry ? formatDate(d.expiry, today) : str('status_no_date');
  return str('grocery_entry_summary', amountText(quantity, d.unit), locationLabel(d.location), date);
}

function EntryRow({
  draft,
  today,
  imperialFirst,
  onChange,
}: {
  draft: EntryDraft;
  today: LocalDate;
  imperialFirst: boolean;
  onChange: (change: Partial<EntryDraft>) => void;
}) {
  const id = draft.item.id;
  const quantity = parseQuantity(draft.quantity);
  return (
    <div className="sn-entry" data-testid={`entry_${id}`}>
      <button
        type="button"
        className="sn-entry__summary m3-state"
        aria-expanded={draft.expanded}
        aria-label={`${draft.item.name}, ${summary(draft, today)}. ${str(draft.expanded ? 'grocery_entry_done' : 'grocery_entry_edit')}`}
        onClick={() => onChange({ expanded: !draft.expanded })}
      >
        <span className="sn-entry__text">
          <span className="t-title-medium">{draft.item.name}</span>
          <span className={`t-body-medium ${valid(draft) ? 'c-on-surface-variant' : 'c-error'}`}>
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
  imperialFirst,
  onSave,
  onDismiss,
}: {
  item: GroceryItem;
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
