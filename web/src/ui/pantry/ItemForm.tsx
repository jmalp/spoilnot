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
import { guessCategoryForNewProduct } from '../../domain/grocery/aisles';
import { Amounts } from '../../domain/pantry/amounts';
import { NameKey, ShelfLife } from '../../domain/pantry/rules';
import { itemEditOf, rebaseEdit, type ItemEditBase } from '../../domain/pantry/edit-watch';
import type { ItemEdit, StockDraft } from '../../domain/pantry/service';
import { addDays, addMonths, type LocalDate } from '../../domain/time';
import { isLargeText } from '../../theme/textScale';
import {
  BottomSheet,
  Button,
  ConnectedButtonGroup,
  DatePickerDialog,
  Icon,
  Menu,
  MenuItem,
  SelectField,
  Switch,
  TextField,
} from '../kit';
import { FieldMessage } from '../kit/TextField';
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

/** Parses a typed quantity; accepts "1,5" as well as "1.5", and the phone's own digits (Amounts). */
export const parseQuantity = (text: string): number | null => Amounts.parse(text);

type FormState = {
  name: string;
  quantity: string;
  unit: QuantityUnit;
  category: Category;
  /** Category was picked by hand, so the aisle guess no longer changes it (after v1, UX-09). */
  categoryChosen?: boolean;
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
  // A new name takes the aisle Groceries would guess (after v1, UX-09), until a category is picked by hand; a product
  // chosen from the suggestions brings its own.
  const setGuessing = (next: FormState) =>
    setForm(
      next.name !== form.name && !next.categoryChosen && !products.some((p) => p.nameKey === NameKey.of(next.name))
        ? { ...next, category: guessCategoryForNewProduct(next.name, products) }
        : next,
    );
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
        look
        title={str('add_item_title')}
        button={
          <Button primary onClick={save} data-testid="button_save_item">
            {str('action_add_item')}
          </Button>
        }
      >
        <ItemFields
          form={form}
          setForm={setGuessing}
          products={products}
          today={today}
          imperialFirst={imperialFirst}
          unitLockedTo={unitLockedTo}
          onChooseProduct={(p) => setForm(fillFrom(form, p, today))}
          allowZero={false}
          // The keyboard opens on Name (after v1, UX-09), except for a product chosen beforehand (an Out row).
          focusName={prefill === null}
        />
      </FormSheetColumn>
    </BottomSheet>
  );
}

/**
 * The Edit window, the same form as Add: the purchase's quantity, date and location, and its product's name, unit,
 * category and grocery settings. The unit can change only when this is the product's only purchase.
 */
/**
 * The Edit window on `batch` and its `product`, as it compares with them (`base`, DESIGN 9.4). When someone else
 * changed the item (`changed`, the item as it is now), it keeps the draft and says so (9.2); Review shows the item as
 * it is now with this person's changes kept, and hands `changed` to `onReviewed`.
 */
export function EditItemSheet({
  batch,
  product,
  canChangeUnit,
  today,
  imperialFirst,
  onSave,
  onDismiss,
  base = itemEditOf({ batch, product }),
  changed = null,
  changedBy = null,
  onReviewed = () => undefined,
}: {
  batch: StockBatch;
  product: Product | null;
  canChangeUnit: boolean;
  today: LocalDate;
  imperialFirst: boolean;
  onSave: (edit: ItemEdit) => void;
  onDismiss: () => void;
  base?: ItemEdit;
  changed?: ItemEditBase | null;
  changedBy?: string | null;
  onReviewed?: (live: ItemEditBase) => void;
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
        look
        title={str('edit_item_title')}
        button={
          <Button primary onClick={save} data-testid="button_save_item">
            {str('action_save')}
          </Button>
        }
      >
        {changed && (
          <ChangedNotice
            text={changedBy !== null ? str('edit_changed_by', changedBy) : str('edit_changed')}
            onReview={() => {
              setForm(review(form, base, changed));
              onReviewed(changed);
            }}
          />
        )}
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

/** "Sam changed this item." with Review (DESIGN 9.2); the words say it, not a color (6.8). */
function ChangedNotice({ text, onReview }: { text: string; onReview: () => void }) {
  return (
    <div className="sn-form__notice" role="status" data-testid="notice_item_changed">
      <Icon name="round/sync" size={20} />
      <span className="sn-form__notice-text">{text}</span>
      <Button variant="text" onClick={onReview} data-testid="button_review_changes">
        {str('action_review')}
      </Button>
    </div>
  );
}

/**
 * Review: the fields the person didn't touch take the item's values as they are now (`live`); the ones they changed
 * keep theirs, and a changed quantity keeps its difference (rebaseEdit).
 */
function review(form: FormState, base: ItemEdit, live: ItemEditBase): FormState {
  const now = itemEditOf(live);
  const qty = parseQuantity(form.quantity);
  const draft: ItemEdit = {
    name: form.name,
    quantity: qty ?? base.quantity,
    unit: form.unit,
    category: form.category,
    expiryDate: form.expiry,
    location: form.location,
    autoAddToGrocery: form.autoAdd,
    minQuantity: form.autoAdd ? optional(form.keepAtLeast) : null,
    buyQuantity: form.autoAdd ? optional(form.buyQuantity) : null,
  };
  const rebased = rebaseEdit(draft, base, now);
  const keepUntouched =
    draft.autoAddToGrocery === base.autoAddToGrocery &&
    draft.minQuantity === base.minQuantity &&
    draft.buyQuantity === base.buyQuantity;
  return {
    ...form,
    name: rebased.name,
    quantity: qty !== null ? formatQuantity(rebased.quantity) : form.quantity,
    unit: rebased.unit,
    category: rebased.category,
    expiry: rebased.expiryDate,
    location: rebased.location,
    ...(keepUntouched
      ? {
          autoAdd: now.autoAddToGrocery,
          keepAtLeast: live.product?.minQuantity != null ? formatQuantity(live.product.minQuantity) : '',
          buyQuantity: live.product?.buyQuantity != null ? formatQuantity(live.product.buyQuantity) : '',
        }
      : {}),
  };
}

/**
 * Scrollable fields with the save button pinned below them, so saving never needs a scroll. With [look], a sheet of
 * the 2026-11 look (DESIGN 19.11, step 3): the title in the sheet title style, 20 px from the sides, 18 px between
 * fields; the other sheets take it with their steps.
 */
export function FormSheetColumn({
  title,
  button,
  children,
  look = false,
}: {
  title: string;
  button: ReactNode;
  children: ReactNode;
  look?: boolean;
}) {
  return (
    <div className={`sn-form-sheet ${look ? 'sn-form-sheet--look' : ''}`}>
      <div className="sn-form-sheet__fields">
        <h2 className={look ? 't-sheet-title' : 't-headline-small'}>{title}</h2>
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
  focusName = false,
}: {
  form: FormState;
  setForm: (f: FormState) => void;
  products: Product[];
  today: LocalDate;
  imperialFirst: boolean;
  unitLockedTo: QuantityUnit | null;
  onChooseProduct: (p: Product) => void;
  allowZero: boolean;
  focusName?: boolean;
}) {
  const id = useId();
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
  // A compact stepper beside the unit; stacked from 1.5x text, as in the app (isLargeText).
  const large = isLargeText();
  return (
    <>
      <NameField
        value={form.name}
        onChange={(v) => set({ name: v })}
        products={products}
        error={nameError}
        onChoose={onChooseProduct}
        focus={focusName}
      />
      <div className={large ? 'sn-form-stack' : 'sn-form-row'}>
        <QuantityStepper
          value={form.quantity}
          onChange={(v) => set({ quantity: v })}
          error={form.submitted && qtyBad ? str('error_quantity') : undefined}
          fill={large}
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
      <ExpiryField value={form.expiry} today={today} onChange={(expiry) => set({ expiry })} />
      <LocationChoice selected={form.location} onPick={(location) => set({ location })} />
      {/* Usually filled in from the product or the name, so it comes after what changes every time. */}
      <SelectField
        label={str('field_category')}
        value={form.category}
        options={CATEGORIES}
        optionLabel={categoryLabel}
        onPick={(category) => set({ category, categoryChosen: true })}
        testId="field_category"
      />
      {/* Keep some in stock (after v1, UX-07): while off it says what it does; the whole row toggles. */}
      <label className="sn-switch-row" data-testid="switch_auto_add">
        <span className="sn-switch-row__text">
          <span className="t-row-title" id={`${id}-auto-add`}>
            {str('field_auto_add')}
          </span>
          {!form.autoAdd && (
            <span className="t-body-medium c-on-surface-variant" id={`${id}-auto-add-help`}>
              {str('field_auto_add_help')}
            </span>
          )}
        </span>
        <Switch
          checked={form.autoAdd}
          aria-labelledby={`${id}-auto-add`}
          aria-describedby={form.autoAdd ? undefined : `${id}-auto-add-help`}
          onCheckedChange={(autoAdd) => set({ autoAdd })}
        />
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
  focus = false,
}: {
  value: string;
  onChange: (v: string) => void;
  products: Product[];
  error?: string;
  onChoose: (p: Product) => void;
  /** Takes the focus when the sheet opens (BottomSheet's data-autofocus). */
  focus?: boolean;
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
        data-autofocus={focus || undefined}
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

/**
 * Quantity as a stepper (DESIGN 19.9): − and + joined to the typed amount, its label above; decimals allowed (5.2).
 * [fill] stretches the amount across the row, at large text sizes.
 */
export function QuantityStepper({
  value,
  onChange,
  error,
  fill,
  testId = 'field_quantity',
}: {
  value: string;
  onChange: (v: string) => void;
  error?: string;
  fill: boolean;
  testId?: string;
}) {
  const id = useId();
  return (
    <div
      className={`sn-stepper-field ${error ? 'sn-stepper-field--error' : ''} ${fill ? 'sn-stepper-field--fill' : ''}`}
    >
      <label className="t-field-label sn-stepper-field__label" htmlFor={id}>
        {str('field_quantity')}
      </label>
      <div className="sn-joined-stepper">
        <StepButton
          icon="round/remove"
          label={str('action_minus_one')}
          start
          onClick={() => onChange(formatQuantity(Math.max(0, (parseQuantity(value) ?? 0) - 1)))}
        />
        <input
          id={id}
          className="sn-joined-stepper__value"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          inputMode="decimal"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          data-testid={testId}
        />
        <StepButton
          icon="round/add"
          label={str('action_plus_one')}
          onClick={() => onChange(formatQuantity((parseQuantity(value) ?? 0) + 1))}
        />
      </div>
      <FieldMessage id={error ? `${id}-error` : undefined} error={error} />
    </div>
  );
}

/** − or + of a stepper: 56 px, secondaryContainer, round outside and 8 px where it joins the value (DESIGN 19.9). */
export function StepButton({
  icon,
  label,
  start = false,
  disabled = false,
  onClick,
}: {
  icon: 'round/remove' | 'round/add';
  label: string;
  start?: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={`sn-step-button m3-state ${start ? 'sn-step-button--start' : 'sn-step-button--end'}`}
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
    >
      <Icon name={icon} />
    </button>
  );
}

/**
 * The expiration date (DESIGN 5.2, 19.10): a picker that looks like a field, with the date and its status, then the
 * quick dates.
 */
function ExpiryField({
  value,
  today,
  onChange,
}: {
  value: LocalDate | null;
  today: LocalDate;
  onChange: (d: LocalDate | null) => void;
}) {
  const [picking, setPicking] = useState(false);
  const labelId = useId();
  // Far-off dates read as the date itself; show it once ("Nov 2", not "Nov 2 · Nov 2").
  const text = value
    ? ((date, status) => (status === date ? date : `${date} · ${status}`))(
        formatDate(value, today),
        statusText(value, 'use_by', today),
      )
    : str('status_no_date');
  return (
    <div className="sn-field-group">
      <span className="t-field-label c-on-surface-variant" id={labelId}>
        {str('field_expiry')}
      </span>
      <button
        type="button"
        className="sn-date-button m3-state"
        aria-describedby={labelId}
        onClick={() => setPicking(true)}
        data-testid="field_expiry"
      >
        <span className="sn-date-button__icon">
          <Icon name="round/calendar_month" />
        </span>
        <span className="sn-date-button__text t-field-text">{text}</span>
        <Icon name="round/expand_more" size={20} />
      </button>
      <DateChips value={value} today={today} onChange={onChange} />
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
 * The quick dates, shared by Add item and Add to pantry: a connected group of four (DESIGN 19.9), radio buttons from
 * 1.5x text. One date at a time, radio buttons for VoiceOver (review A11Y-08). Tapping the chosen one again clears the
 * date; no date is the default.
 */
export function DateChips({
  value,
  today,
  onChange,
}: {
  value: LocalDate | null;
  today: LocalDate;
  onChange: (d: LocalDate | null) => void;
}) {
  const options: [StringId, LocalDate][] = [
    ['chip_plus_3_days', addDays(today, 3)],
    ['chip_plus_1_week', addDays(today, 7)],
    ['chip_plus_2_weeks', addDays(today, 14)],
    ['chip_plus_1_month', addMonths(today, 1)],
  ];
  return (
    <ConnectedButtonGroup
      label={str('field_expiry')}
      options={options}
      selected={options.find(([, date]) => date === value) ?? null}
      optionLabel={([label]) => str(label)}
      optionKey={([label]) => label}
      optionTestId={([, date]) => `date_chip_${date}`}
      onPick={([, date]) => onChange(date === value ? null : date)}
    />
  );
}

/**
 * Location as four equal tiles, icon over label (a 2 by 2 grid from 1.5x text). The chosen one is filled
 * primaryContainer, rounder and heavier (DESIGN 19.10), so it never depends on color alone.
 */
export function LocationChoice({ selected, onPick }: { selected: Location; onPick: (l: Location) => void }) {
  const labelId = useId();
  return (
    <div className="sn-field-group">
      <span className="t-field-label c-on-surface-variant" id={labelId}>
        {str('field_location')}
      </span>
      <div
        className={`sn-tiles ${isLargeText() ? 'sn-tiles--two-rows' : ''}`}
        role="radiogroup"
        aria-labelledby={labelId}
      >
        {LOCATIONS.map((location) => (
          <button
            key={location}
            type="button"
            role="radio"
            aria-checked={location === selected}
            className={`sn-tile m3-state ${location === selected ? 'sn-tile--selected' : ''}`}
            data-testid={`location_${location}`}
            onClick={() => onPick(location)}
          >
            <Icon name={locationIcon(location)} />
            <span className="t-button-medium">{locationLabel(location)}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
