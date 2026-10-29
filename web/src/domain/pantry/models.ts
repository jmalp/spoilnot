// Pantry data (DESIGN 8.2), ported from feature/pantry/domain/PantryModels. Enum values are the stored wire values;
// the arrays keep the app's enum order, which decides how groups are sorted.
import type { LocalDate } from '../time';
import type { HouseholdChange } from '../write';

export const LOCATIONS = ['fridge', 'freezer', 'pantry', 'other'] as const;
export type Location = (typeof LOCATIONS)[number];

export const CATEGORIES = [
  'produce',
  'dairy',
  'meat_fish',
  'bakery',
  'frozen',
  'pantry_staples',
  'drinks',
  'snacks',
  'cooked',
  'other',
] as const;
export type Category = (typeof CATEGORIES)[number];

/** No conversion between units (DESIGN 2). */
export const UNITS = ['pcs', 'g', 'kg', 'oz', 'lb', 'ml', 'l', 'fl_oz'] as const;
export type QuantityUnit = (typeof UNITS)[number];

/** The unit menu: pieces first, then the preferred system (Settings > Appearance), then the other. */
export function unitMenu(imperialFirst: boolean): QuantityUnit[] {
  const metric: QuantityUnit[] = ['g', 'kg', 'ml', 'l'];
  const imperial: QuantityUnit[] = ['oz', 'lb', 'fl_oz'];
  return ['pcs', ...(imperialFirst ? [...imperial, ...metric] : [...metric, ...imperial])];
}

/** Use by is a safety date; best before is a quality date (DESIGN 5.2). */
export type DateType = 'use_by' | 'best_before';

export const DISCARD_REASONS = ['expired', 'spoiled', 'didnt_like', 'other'] as const;
export type DiscardReason = (typeof DISCARD_REASONS)[number];

const wire =
  <T extends string>(values: readonly T[], fallback: T) =>
  (value: unknown): T =>
    values.includes(value as T) ? (value as T) : fallback;

export const locationOf = wire(LOCATIONS, 'other');
export const categoryOf = wire(CATEGORIES, 'other');
export const unitOf = wire(UNITS, 'pcs');
export const dateTypeOf = wire<DateType>(['use_by', 'best_before'], 'use_by');
export const discardReasonOf = (value: unknown): DiscardReason | null =>
  DISCARD_REASONS.includes(value as DiscardReason) ? (value as DiscardReason) : null;

/** households/{hid}/products/{id}: a kind of food. */
export type Product = {
  id: string;
  name: string;
  nameKey: string;
  category: Category;
  defaultLocation: Location;
  unit: QuantityUnit;
  /** Keep at least. */
  minQuantity: number | null;
  autoAddToGrocery: boolean;
  buyQuantity: number | null;
  restockArmed: boolean;
  typicalShelfLifeDays: number | null;
  /** Shelf lives of the most recent batches, newest last. */
  recentShelfLifeDays: number[];
  /** Milliseconds; null until the server stamps it. */
  createdAt: number | null;
  /** How much was added the last time, in [unit]: the usual amount for recipe grocery items. */
  lastAddedQuantity: number | null;
  /** Other names this product goes by in recipes, as name keys. */
  aliases: string[];
  /** How many days it keeps once opened, as last chosen in Mark opened. */
  openedShelfLifeDays: number | null;
};

export const isStaple = (product: Product) => product.minQuantity !== null;

/** households/{hid}/stock/{id}: one purchase with one date and one location. */
export type StockBatch = {
  id: string;
  productId: string;
  productName: string;
  unit: QuantityUnit;
  category: Category;
  quantity: number;
  expiryDate: LocalDate | null;
  dateType: DateType;
  location: Location;
  note: string | null;
  createdAt: number | null;
  /** True while a local change has not reached the server (DESIGN 6.7). */
  pendingSync: boolean;
  /** When it was opened; null while sealed. */
  openedOn: LocalDate | null;
};

/** The household's products and batches as the listeners see them (DESIGN 9.1). */
export type PantryData = { products: Product[]; batches: StockBatch[]; fromCache: boolean };

/** Changes to one batch from the edit sheet or Move. */
export type BatchEdit = {
  quantity: number;
  expiryDate: LocalDate | null;
  dateType: DateType;
  location: Location;
  note: string | null;
};

/** A pantry event in the activity log, as product history shows it. */
export type PantryEventType = 'added' | 'discarded';

export const PantryLimits = {
  MAX_OPENED_DAYS: 365,
  MAX_ALIASES: 30,
  MAX_NAME_LENGTH: 80,
  MAX_NOTE_LENGTH: 200,
} as const;

/** One write in a pantry plan (PantryChange). */
export type PantryChange =
  | { type: 'pantry.createProduct'; product: Product }
  | { type: 'pantry.updateProduct'; product: Product }
  | { type: 'pantry.setRestockArmed'; productId: string; armed: boolean }
  | { type: 'pantry.markOpened'; batchId: string; openedOn: LocalDate | null; expiryDate: LocalDate | null }
  | { type: 'pantry.setOpenedShelfLife'; productId: string; days: number }
  | { type: 'pantry.addAlias'; productId: string; alias: string }
  | { type: 'pantry.createBatch'; batch: StockBatch }
  /** An atomic increment, so +1 and -1 from two phones commute (DESIGN 9.3). */
  | { type: 'pantry.addToBatch'; batchId: string; delta: number }
  | { type: 'pantry.updateBatch'; batchId: string; edit: BatchEdit }
  | { type: 'pantry.syncBatchProduct'; batchId: string; name: string; unit: QuantityUnit; category: Category }
  | { type: 'pantry.deleteBatch'; batchId: string }
  /** Puts a deleted batch back under its old id (Undo). */
  | { type: 'pantry.restoreBatch'; batch: StockBatch }
  | {
      type: 'pantry.logEvent';
      eventId: string;
      eventType: PantryEventType;
      productId: string;
      name: string;
      quantity: number | null;
      reason: DiscardReason | null;
    }
  | { type: 'pantry.deleteEvent'; eventId: string };

export const isPantryChange = (change: HouseholdChange): change is PantryChange => change.type.startsWith('pantry.');
