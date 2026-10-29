// Pantry wording and icons (PantryLabels in the app): status texts and chips, labels for locations, categories,
// units and reasons, and amounts.
import { plural, str, type StringId } from '../../core/i18n';
import type { Category, DateType, DiscardReason, Location, QuantityUnit } from '../../domain/pantry/models';
import { Expiry, type ExpiryStatus } from '../../domain/pantry/rules';
import type { LocalDate } from '../../domain/time';
import type { IconName } from '../kit';

/** Fixed status tones (DESIGN 6.4): a week counts as fresh. */
export type StatusTone = 'fresh' | 'soon' | 'urgent' | 'expired' | 'no-date';

export function tone(status: ExpiryStatus): StatusTone {
  switch (status) {
    case 'expired':
      return 'expired';
    case 'today':
    case 'tomorrow':
      return 'urgent';
    case 'soon':
      return 'soon';
    case 'week':
    case 'fresh':
      return 'fresh';
    case 'noDate':
      return 'no-date';
  }
}

export function statusIcon(status: ExpiryStatus): IconName {
  switch (status) {
    case 'expired':
      return 'round/delete_outline';
    case 'today':
    case 'tomorrow':
      return 'round/warning_amber';
    case 'soon':
      return 'round/schedule';
    case 'week':
    case 'fresh':
      return 'round/check_circle';
    case 'noDate':
      return 'round/remove';
  }
}

const shortMonth = new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' });

/** "Oct 20", or "Mar 2027" for a date in another year. */
export function formatDate(date: LocalDate, today: LocalDate): string {
  const month = shortMonth.format(new Date(`${date}T00:00:00Z`));
  return date.slice(0, 4) === today.slice(0, 4)
    ? `${month} ${Number(date.slice(8, 10))}`
    : `${month} ${date.slice(0, 4)}`;
}

/** The full wording from the status table (DESIGN 5.2), for rows and screen readers. */
export function statusText(expiry: LocalDate | null, dateType: DateType, today: LocalDate): string {
  const status = Expiry.of(expiry, today);
  const days = expiry ? Expiry.daysUntil(expiry, today) : 0;
  const useBy = dateType === 'use_by';
  switch (status) {
    case 'expired':
      return useBy
        ? plural('status_expired_use_by', Math.abs(days), Math.abs(days))
        : str('status_expired_best_before');
    case 'today':
      return str(useBy ? 'status_today_use_by' : 'status_today_best_before');
    case 'tomorrow':
      return str(useBy ? 'status_tomorrow_use_by' : 'status_tomorrow_best_before');
    case 'soon':
      return plural(useBy ? 'status_soon_use_by' : 'status_soon_best_before', days, days);
    case 'week':
      return plural('status_week', days, days);
    case 'fresh':
      return formatDate(expiry!, today);
    case 'noDate':
      return str('status_no_date');
  }
}

/** Short text for the chip on a list row. */
export function chipText(expiry: LocalDate | null, dateType: DateType, today: LocalDate): string {
  const status = Expiry.of(expiry, today);
  const days = expiry ? Expiry.daysUntil(expiry, today) : 0;
  switch (status) {
    case 'expired':
      return str(dateType === 'use_by' ? 'chip_expired' : 'chip_past_best_before');
    case 'today':
      return str('chip_today');
    case 'tomorrow':
      return str('chip_tomorrow');
    case 'soon':
    case 'week':
      return plural('chip_days', days, days);
    case 'fresh':
      return formatDate(expiry!, today);
    case 'noDate':
      return str('status_no_date');
  }
}

export const locationLabel = (l: Location) => str(`location_${l}` as StringId);
export const categoryLabel = (c: Category) => str(`category_${c}` as StringId);
export const unitLabel = (u: QuantityUnit) => str(`unit_${u}` as StringId);
export const reasonLabel = (r: DiscardReason) => str(`reason_${r}` as StringId);

export function locationIcon(location: Location): IconName {
  return {
    fridge: 'outlined/kitchen',
    freezer: 'outlined/ac_unit',
    pantry: 'outlined/inventory_2',
    other: 'outlined/place',
  }[location] as IconName;
}

/** 2, 1.5 or 0.25: no trailing zeros (DecimalFormat("0.##")). */
export function formatQuantity(value: number): string {
  return String(Math.round(value * 100) / 100);
}

/** "2" or "500 g": a count of pieces needs no unit. */
export function amountText(quantity: number, unit: QuantityUnit | null): string {
  return unit === null || unit === 'pcs' ? formatQuantity(quantity) : `${formatQuantity(quantity)} ${unitLabel(unit)}`;
}

/** The amount at the end of a row: "× 3" for pieces, nothing for a single one, "500 g" otherwise. */
export function rowAmount(quantity: number, unit: QuantityUnit): string | null {
  if (unit !== 'pcs') return amountText(quantity, unit);
  return quantity === 1 ? null : `× ${formatQuantity(quantity)}`;
}

/** The amount for VoiceOver: "2 pieces", "500 g". */
export function amountDescription(quantity: number, unit: QuantityUnit | null): string {
  return unit === null || unit === 'pcs'
    ? plural('amount_pieces', quantity === 1 ? 1 : 2, formatQuantity(quantity))
    : amountText(quantity, unit);
}
