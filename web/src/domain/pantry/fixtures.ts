// Builders for pantry tests (PantryFixtures). "Today" is fixed at 2026-10-12.
import { addDays, type Clock, type LocalDate } from '../time';
import type { Category, DateType, Location, Product, QuantityUnit, StockBatch } from './models';
import { NameKey, ShelfLife } from './rules';

export const TODAY: LocalDate = '2026-10-12';
/** Noon UTC on TODAY: the same calendar day in every time zone the tests run in. */
export const FIXED_CLOCK: Clock = { now: () => Date.parse('2026-10-12T12:00:00Z') };

export function product(
  id: string,
  {
    name = id.charAt(0).toUpperCase() + id.slice(1),
    min = null,
    location = 'fridge',
    category = 'dairy',
    recent = [],
    unit = 'pcs',
  }: {
    name?: string;
    min?: number | null;
    location?: Location;
    category?: Category;
    recent?: number[];
    unit?: QuantityUnit;
  } = {},
): Product {
  return {
    id,
    name,
    nameKey: NameKey.of(name),
    category,
    defaultLocation: location,
    unit,
    minQuantity: min,
    autoAddToGrocery: min !== null,
    buyQuantity: null,
    restockArmed: true,
    typicalShelfLifeDays: ShelfLife.median(recent),
    recentShelfLifeDays: recent,
    createdAt: null,
    lastAddedQuantity: null,
    aliases: [],
    openedShelfLifeDays: null,
  };
}

export function batch(
  id: string,
  productId: string,
  {
    quantity = 1,
    inDays = 5,
    location = 'fridge',
    dateType = 'use_by',
    addedSecondsAgo = 0,
    name = productId.charAt(0).toUpperCase() + productId.slice(1),
  }: {
    quantity?: number;
    inDays?: number | null;
    location?: Location;
    dateType?: DateType;
    addedSecondsAgo?: number;
    name?: string;
  } = {},
): StockBatch {
  return {
    id,
    productId,
    productName: name,
    unit: 'pcs',
    category: 'dairy',
    quantity,
    expiryDate: inDays === null ? null : addDays(TODAY, inDays),
    dateType,
    location,
    note: null,
    createdAt: Date.parse('2026-10-12T12:00:00Z') - addedSecondsAgo * 1000,
    pendingSync: false,
    openedOn: null,
  };
}
