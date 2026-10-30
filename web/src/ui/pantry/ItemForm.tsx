import { useId, useState, type ReactNode } from 'react';
import { str, type StringId } from '../../core/i18n';
import {
  CATEGORIES,
  LOCATIONS,
  PantryLimits,
  unitMenu,
  type Category,
  type Location,
  type Product,
  type QuantityUnit,
  type StockBatch,
} from '../../domain/pantry/models';
import { NameKey, ShelfLife } from '../../domain/pantry/rules';
import type { ItemEdit, StockDraft } from '../../domain/pantry/service';
import { addDays, addMonths, type LocalDate } from '../../domain/time';
import {
  BottomSheet,
  Button,
  DatePickerDialog,
  Icon,
  IconButton,
  Menu,
  MenuItem,
  SelectField,
  Switch,
  TextField,
} from '../kit';
import {
  categoryLabel,
  formatDate,
  formatQuantity,
  locationIcon,
  locationLabel,
  statusText,
  unitLabel,
} from './labels';
import './form.css';

/** Parses a typed quantity; accepts "1,5" as well as "1.5". Null when not a number. */
export function parseQuantity(text: string): number | null {
  const value = text.trim().replace(',', '.');
  if (!/^\d*\.?\d+$|^\d+\.$/.test(value)) return null;
  return Number(value);
}

/** From this text size (root font scale) the date tiles take two rows and quantity and unit stack. */
const LARGE_TEXT_SCALE = 1.3;
export const largeText = () => parseFloat(getComputedStyle(document.documentElement).fontSize) / 16 >= LARGE_TEXT_SCALE;

type FormState = {
  name: string;
  quantity: string;
  unit: QuantityUnit;
  category: Category;
  expiry: LocalDate | null;
  location: Location;
  autoAdd: boolean;
  keepAtLeast: string;
  buyQuantity: string;
  submitted: boolean;
};

function fillFrom(form: FormState, product: Product, today: LocalDate): FormState {
  return {
    ...form,
    name: product.name,
    unit: product.unit,
    location: product.defaultLocation,
    category: product.category,
    expiry: ShelfLife.suggestExpiry(product.typicalShelfLifeDays, today) ?? form.expiry,
    autoAdd: product.autoAddToGrocery,
    keepAtLeast: product.minQuantity !== null ? formatQuantity(product.minQuantity) : '',
    buyQuantity: product.buyQuantity !== null ? formatQuantity(product.buyQuantity) : '',
  };
}

const optional = (text: string) => (text.trim() === '' ? null : parseQuantity(text));
const cleanName = (form: FormState) => NameKey.clean(form.name);
const nameOk = (form: FormState) => cleanName(form) !== '' && cleanName(form).length <= PantryLimits.MAX_NAME_LENGTH;
/** With auto-add on, Keep at least is required and above 0. */
const minOk = (form: FormState) => !form.autoAdd || (optional(form.keepAtLeast) ?? 0) > 0;
const buyOk = (form: FormState) =>
  !form.autoAdd || form.buyQuantity.trim() === '' || (optional(form.buyQuantity) ?? 0) > 0;

/**
 * The Add item sheet (DESIGN 5.2). Choosing an existing product fills unit, category, location, the expiry date from
 * its typical shelf life, and its grocery settings; [prefill] opens it for a product, such as an Out staple.
 */
export function AddItemSheet({
  products,
  productsWithStock,
  today,
  imperialFirst,
  prefill,
  onSave,
  onDismiss,
}: {
  products: Product[];
  productsWithStock: Set<string>;
  today: LocalDate;
  imperialFirst: boolean;
  prefill: Product | null;
  onSave: (draft: StockDraft) => void;
  onDismiss: () => void;
}) {
  const [form, setForm] = useState<FormState>(() => {
    const empty: FormState = {
      name: '',
      quantity: '1',
      unit: 'pcs',
      category: 'other',
      expiry: null,
      location: 'fridge',
      autoAdd: false,
      keepAtLeast: '',
      buyQuantity: '',
      submitted: false,
    };
    return prefill ? fillFrom(empty, prefill, today) : empty;
  });
  const match = products.find((p) => p.nameKey === NameKey.of(form.name));
  const unitLockedTo = match && productsWithStock.has(match.id) ? match.unit : null;
  const save = () => {
    const qty = parseQuantity(form.quantity);
    setForm({ ...form, submitted: true });
    if (!nameOk(form) || qty === null || qty <= 0 || !minOk(form) || !buyOk(form)) return;
    onSave({
      name: cleanName(form),
      quantity: qty,
      unit: unitLockedTo ?? form.unit,
      expiryDate: form.expiry,
      // Date type and notes left the form (beta feedback): new items are Use by.
      dateType: 'use_by',
      location: form.location,
      category: form.category,
      note: null,
      minQuantity: form.autoAdd ? optional(form.keepAtLeast) : null,
      autoAddToGrocery: form.autoAdd,
      buyQuantity: form.autoAdd ? optional(form.buyQuantity) : null,
    });
  };
  return (
    <BottomSheet label={str('add_item_title')} onDismiss={onDismiss} testId="sheet_add_item">
      <FormSheetColumn
        title={str('add_item_title')}
        button={
          <Button full large onClick={save} data-testid="button_save_item">
            {str('action_add_item')}
          </Button>
        }
      >
        <ItemFields
          form={form}
          setForm={setForm}
          products={products}
          today={today}
          imperialFirst={imperialFirst}
          unitLockedTo={unitLockedTo}
          onChooseProduct={(p) => setForm(fillFrom(form, p, today))}
          allowZero={false}
        />
      </FormSheetColumn>
    </BottomSheet>
  );
}

/**
 * The Edit window, the same form as Add: the purchase's quantity, date and location, and its product's name, unit,
 * category and grocery settings. The unit can change only when this is the product's only purchase.
 */
export function EditItemSheet({
  batch,
  product,
  canChangeUnit,
  today,
  imperialFirst,
  onSave,
  onDismiss,
}: {
  batch: StockBatch;
  product: Product | null;
  canChangeUnit: boolean;
  today: LocalDate;
  imperialFirst: boolean;
  onSave: (edit: ItemEdit) => void;
  onDismiss: () => void;
}) {
  const [form, setForm] = useState<FormState>({
    name: product?.name ?? batch.productName,
    quantity: formatQuantity(batch.quantity),
    unit: product?.unit ?? batch.unit,
    category: product?.category ?? batch.category,
    expiry: batch.expiryDate,
    location: batch.location,
    autoAdd: product?.autoAddToGrocery === true,
    keepAtLeast: product?.minQuantity != null ? formatQuantity(product.minQuantity) : '',
    buyQuantity: product?.buyQuantity != null ? formatQuantity(product.buyQuantity) : '',
    submitted: false,
  });
  const save = () => {
    const qty = parseQuantity(form.quantity);
    setForm({ ...form, submitted: true });
    if (!nameOk(form) || qty === null || qty < 0 || !minOk(form) || !buyOk(form)) return;
    onSave({
      name: cleanName(form),
      quantity: qty,
      unit: form.unit,
      category: form.category,
      expiryDate: form.expiry,
      location: form.location,
      autoAddToGrocery: form.autoAdd,
      minQuantity: form.autoAdd ? optional(form.keepAtLeast) : null,
      buyQuantity: form.autoAdd ? optional(form.buyQuantity) : null,
    });
  };
  return (
    <BottomSheet label={str('edit_item_title')} onDismiss={onDismiss} testId="sheet_edit_item">
      <FormSheetColumn
        title={str('edit_item_title')}
        button={
          <Button full large onClick={save} data-testid="button_save_item">
            {str('action_save')}
          </Button>
        }
      >
        <ItemFields
          form={form}
          setForm={setForm}
          products={[]}
          today={today}
          imperialFirst={imperialFirst}
          unitLockedTo={canChangeUnit ? null : (product?.unit ?? batch.unit)}
          onChooseProduct={() => undefined}
          allowZero
        />
      </FormSheetColumn>
    </BottomSheet>
  );
}

/** Scrollable fields with the save button pinned below them, so saving never needs a scroll. */
export function FormSheetColumn({
  title,
  button,
  children,
}: {
  title: string;
  button: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="sn-form-sheet">
      <div className="sn-form-sheet__fields">
        <h2 className="t-headline-small">{title}</h2>
        {children}
      </div>
      <div className="sn-form-sheet__button">{button}</div>
    </div>
  );
}

/** Name, quantity, unit, date, location, category, and Add to grocery list automatically. */
function ItemFields({
  form,
  setForm,
  products,
  today,
  imperialFirst,
  unitLockedTo,
  onChooseProduct,
  allowZero,
}: {
  form: FormState;
  setForm: (f: FormState) => void;
  products: Product[];
  today: LocalDate;
  imperialFirst: boolean;
  unitLockedTo: QuantityUnit | null;
  onChooseProduct: (p: Product) => void;
  allowZero: boolean;
}) {
  const set = (patch: Partial<FormState>) => setForm({ ...form, ...patch });
  const name = cleanName(form);
  const nameError = !form.submitted
    ? undefined
    : name === ''
      ? str('error_name_required')
      : name.length > PantryLimits.MAX_NAME_LENGTH
        ? str('error_name_80')
        : undefined;
  const qty = parseQuantity(form.quantity);
  const qtyBad = qty === null || (allowZero ? qty < 0 : qty <= 0);
  const large = largeText();
  return (
    <>
      <NameField
        value={form.name}
        onChange={(v) => set({ name: v })}
        products={products}
        error={nameError}
        onChoose={onChooseProduct}
      />
      <div className={large ? 'sn-form-stack' : 'sn-form-row'}>
        <QuantityRow
          value={form.quantity}
          onChange={(v) => set({ quantity: v })}
          error={form.submitted && qtyBad ? str('error_quantity') : undefined}
          compact={!large}
        />
        <SelectField
          label={str('field_unit')}
          value={unitLockedTo ?? form.unit}
          options={unitMenu(imperialFirst)}
          optionLabel={unitLabel}
          onPick={(unit) => set({ unit })}
          disabled={unitLockedTo !== null}
          supportingText={unitLockedTo !== null ? str('unit_locked_short') : undefined}
          testId="field_unit"
        />
      </div>
      <ExpiryField value={form.expiry} today={today} twoRows={large} onChange={(expiry) => set({ expiry })} />
      <LocationChoice selected={form.location} grid={large} onPick={(location) => set({ location })} />
      {/* Usually filled in from the product or the name, so it comes after what changes every time. */}
      <SelectField
        label={str('field_category')}
        value={form.category}
        options={CATEGORIES}
        optionLabel={categoryLabel}
        onPick={(category) => set({ category })}
        testId="field_category"
      />
      <label className="sn-switch-row" data-testid="switch_auto_add">
        <span className="t-body-large">{str('field_auto_add')}</span>
        <Switch checked={form.autoAdd} onCheckedChange={(autoAdd) => set({ autoAdd })} />
      </label>
      {form.autoAdd && (
        <>
          <TextField
            label={str('field_keep_at_least')}
            value={form.keepAtLeast}
            onValueChange={(keepAtLeast) => set({ keepAtLeast })}
            inputMode="decimal"
            supportingText={str('keep_at_least_help')}
            error={form.submitted && !minOk(form) ? str('error_keep_at_least') : undefined}
            data-testid="field_keep_at_least"
          />
          <TextField
            label={str('field_buy_quantity')}
            value={form.buyQuantity}
            onValueChange={(buyQuantity) => set({ buyQuantity })}
            inputMode="decimal"
            supportingText={str('optional')}
            error={form.submitted && !buyOk(form) ? str('error_number') : undefined}
          />
        </>
      )}
    </>
  );
}

/** Name with suggestions from existing products, case-insensitive (DESIGN 5.2). */
function NameField({
  value,
  onChange,
  products,
  error,
  onChoose,
}: {
  value: string;
  onChange: (v: string) => void;
  products: Product[];
  error?: string;
  onChoose: (p: Product) => void;
}) {
  const [open, setOpen] = useState(false);
  const key = NameKey.of(value);
  const suggestions =
    key === ''
      ? []
      : products
          .filter((p) => p.nameKey.includes(key) && p.nameKey !== key)
          .sort((a, b) => Number(!a.nameKey.startsWith(key)) - Number(!b.nameKey.startsWith(key)))
          .slice(0, 5);
  return (
    <div className="sn-anchor sn-name-field">
      <TextField
        label={str('field_name')}
        value={value}
        onValueChange={(v) => {
          onChange(v);
          setOpen(true);
        }}
        autoCapitalize="sentences"
        autoComplete="off"
        error={error}
        data-testid="field_name"
      />
      <Menu open={open && suggestions.length > 0} onDismiss={() => setOpen(false)} align="start" focusFirst={false}>
        {suggestions.map((p) => (
          <MenuItem
            key={p.id}
            label={p.name}
            onClick={() => {
              onChoose(p);
              setOpen(false);
            }}
          />
        ))}
      </Menu>
    </div>
  );
}

/** Stepper plus keyboard; decimals allowed (DESIGN 5.2). */
function QuantityRow({
  value,
  onChange,
  error,
  compact,
}: {
  value: string;
  onChange: (v: string) => void;
  error?: string;
  compact: boolean;
}) {
  return (
    <div className="sn-quantity">
      <IconButton
        icon="round/remove"
        label={str('action_minus_one')}
        onClick={() => onChange(formatQuantity(Math.max(0, (parseQuantity(value) ?? 0) - 1)))}
      />
      <TextField
        label={str('field_quantity')}
        value={value}
        onValueChange={onChange}
        inputMode="decimal"
        error={error}
        className={compact ? 'sn-quantity__field--compact' : 'sn-quantity__field'}
        data-testid="field_quantity"
      />
      <IconButton
        icon="round/add"
        label={str('action_plus_one')}
        onClick={() => onChange(formatQuantity((parseQuantity(value) ?? 0) + 1))}
      />
    </div>
  );
}

/** The date button (opening the picker) and the quick date tiles (DESIGN 5.2). */
function ExpiryField({
  value,
  today,
  twoRows,
  onChange,
}: {
  value: LocalDate | null;
  today: LocalDate;
  twoRows: boolean;
  onChange: (d: LocalDate | null) => void;
}) {
  const [picking, setPicking] = useState(false);
  // Far-off dates read as the date itself; show it once ("Nov 2", not "Nov 2 · Nov 2").
  const text = value
    ? ((date, status) => (status === date ? date : `${date} · ${status}`))(
        formatDate(value, today),
        statusText(value, 'use_by', today),
      )
    : str('status_no_date');
  return (
    <div className="sn-field-group">
      <span className="t-label-large">{str('field_expiry')}</span>
      <Button
        variant="outlined"
        full
        large
        icon="round/calendar_month"
        onClick={() => setPicking(true)}
        data-testid="field_expiry"
      >
        {text}
      </Button>
      <DateChips value={value} today={today} twoRows={twoRows} onChange={onChange} />
      {picking && (
        <DatePickerDialog
          value={value}
          today={today}
          onDismiss={() => setPicking(false)}
          onPick={(date) => {
            onChange(date);
            setPicking(false);
          }}
        />
      )}
    </div>
  );
}

/**
 * The quick date tiles: four equal tiles in one row (two rows at large text sizes). Tapping the chosen one again
 * clears the date; no date is the default.
 */
export function DateChips({
  value,
  today,
  twoRows,
  onChange,
}: {
  value: LocalDate | null;
  today: LocalDate;
  twoRows: boolean;
  onChange: (d: LocalDate | null) => void;
}) {
  const options: [StringId, LocalDate][] = [
    ['chip_plus_3_days', addDays(today, 3)],
    ['chip_plus_1_week', addDays(today, 7)],
    ['chip_plus_2_weeks', addDays(today, 14)],
    ['chip_plus_1_month', addMonths(today, 1)],
  ];
  return (
    <div className={`sn-tiles ${twoRows ? 'sn-tiles--two-rows' : ''}`}>
      {options.map(([label, date]) => {
        const selected = value === date;
        return (
          <button
            key={label}
            type="button"
            className={`sn-tile sn-tile--small m3-state ${selected ? 'sn-tile--selected' : ''}`}
            aria-pressed={selected}
            data-testid={`date_chip_${date}`}
            onClick={() => onChange(selected ? null : date)}
          >
            <span className="t-label-large">{str(label)}</span>
          </button>
        );
      })}
    </div>
  );
}

/** Location as four equal tiles, icon over label (a 2 by 2 grid at large text sizes); the chosen one is bold with a border. */
export function LocationChoice({
  selected,
  grid,
  onPick,
}: {
  selected: Location;
  grid: boolean;
  onPick: (l: Location) => void;
}) {
  const labelId = useId();
  return (
    <div className="sn-field-group">
      <span className="t-label-large" id={labelId}>
        {str('field_location')}
      </span>
      <div className={`sn-tiles ${grid ? 'sn-tiles--two-rows' : ''}`} role="radiogroup" aria-labelledby={labelId}>
        {LOCATIONS.map((location) => (
          <button
            key={location}
            type="button"
            role="radio"
            aria-checked={location === selected}
            className={`sn-tile sn-tile--location m3-state ${location === selected ? 'sn-tile--selected' : ''}`}
            data-testid={`location_${location}`}
            onClick={() => onPick(location)}
          >
            <span className="sn-tile__icon">
              <Icon name={locationIcon(location)} />
            </span>
            <span className="t-label-large">{locationLabel(location)}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
