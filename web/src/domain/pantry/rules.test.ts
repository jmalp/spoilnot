// Ported from ExpiryStatusTest, NameKeyAndShelfLifeTest and NaturalOrderTest.
import { describe, expect, it } from 'vitest';
import { addDays, daysBetween } from '../time';
import { TODAY } from './fixtures';
import { Expiry, NameKey, naturalOrder, ShelfLife } from './rules';

const status = (days: number | null) => Expiry.of(days === null ? null : addDays(TODAY, days), TODAY);

describe('Expiry', () => {
  it('every row of the status table', () => {
    expect([-30, -1, 0, 1, 2, 3, 4, 7, 8, 400].map(status)).toEqual([
      'expired',
      'expired',
      'today',
      'tomorrow',
      'soon',
      'soon',
      'week',
      'week',
      'fresh',
      'fresh',
    ]);
    expect(status(null)).toBe('noDate');
  });

  it('calendar days across month, year and DST boundaries', () => {
    expect(daysBetween('2026-10-31', '2026-11-01')).toBe(1);
    expect(daysBetween('2026-12-31', '2027-01-01')).toBe(1);
    // US daylight saving ends on 2026-11-01: still one calendar day.
    expect(Expiry.of('2026-11-02', '2026-11-01')).toBe('tomorrow');
    expect(daysBetween('2028-01-01', '2029-01-01')).toBe(366);
  });

  it('needs attention means expired or within 3 days', () => {
    for (const d of [-1, 0, 1, 2, 3]) expect(Expiry.needsAttention(status(d))).toBe(true);
    for (const d of [4, 8, null]) expect(Expiry.needsAttention(status(d))).toBe(false);
  });
});

describe('NameKey and ShelfLife', () => {
  it('name key lowercases, trims, collapses spaces and drops diacritics', () => {
    expect(NameKey.of('  Crème   Fraîche ')).toBe('creme fraiche');
    expect(NameKey.of('JALAPEÑO')).toBe('jalapeno');
    expect(NameKey.of('Greek yogurt')).toBe(NameKey.of('greek  YOGURT'));
    expect(NameKey.clean('  Greek   yogurt ')).toBe('Greek yogurt');
  });

  it('shelf life keeps the last 5 and ignores undated or backdated batches', () => {
    let recent: number[] = [];
    for (const d of [3, 5, 7, 9, 11, 13]) recent = ShelfLife.record(recent, TODAY, addDays(TODAY, d));
    expect(recent).toEqual([5, 7, 9, 11, 13]);
    expect(ShelfLife.record(recent, TODAY, null)).toEqual(recent);
    expect(ShelfLife.record(recent, TODAY, addDays(TODAY, -1))).toEqual(recent);
  });

  it('median of odd and even counts', () => {
    expect(ShelfLife.median([])).toBeNull();
    expect(ShelfLife.median([7])).toBe(7);
    expect(ShelfLife.median([30, 3, 7])).toBe(7);
    expect(ShelfLife.median([5, 7])).toBe(6);
    expect(ShelfLife.median([5, 8])).toBe(7);
    expect(ShelfLife.median([13, 5, 9, 11, 7])).toBe(9);
  });

  it('suggested expiry comes from the typical shelf life', () => {
    expect(ShelfLife.suggestExpiry(7, TODAY)).toBe(addDays(TODAY, 7));
    expect(ShelfLife.suggestExpiry(null, TODAY)).toBeNull();
  });
});

describe('naturalOrder', () => {
  it('numbers sort by value, letters ignore case and accents', () => {
    const names = ['Item 100', 'item 20', 'Item 3', 'Bag 10', 'bag 2', 'Éclair', 'eggs', 'Apples'];
    expect(names.sort(naturalOrder)).toEqual([
      'Apples',
      'bag 2',
      'Bag 10',
      'Éclair',
      'eggs',
      'Item 3',
      'item 20',
      'Item 100',
    ]);
  });

  it('long numbers and leading zeros do not overflow or reorder', () => {
    expect(['Lot 99999999999999999999', 'Lot 12', 'Lot 007'].sort(naturalOrder)).toEqual([
      'Lot 007',
      'Lot 12',
      'Lot 99999999999999999999',
    ]);
  });
});
