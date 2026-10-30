// Ported from AisleGuessTest and StoreOrderTest.
import { describe, expect, it } from 'vitest';
import { product } from '../pantry/fixtures';
import type { Category } from '../pantry/models';
import { guessAisle, ShoppingTrip, StoreOrder } from './aisles';

describe('guessAisle', () => {
  it('common names get their aisle, plurals included', () => {
    expect(guessAisle('Bananas')).toBe('produce');
    expect(guessAisle('cherry tomatoes')).toBe('produce');
    expect(guessAisle('Greek yogurt')).toBe('dairy');
    expect(guessAisle('chicken thighs')).toBe('meat_fish');
    expect(guessAisle('Sourdough bread')).toBe('bakery');
    expect(guessAisle('sparkling water')).toBe('drinks');
    expect(guessAisle('tortilla chips')).toBe('snacks');
  });

  it('a longer phrase wins over the words in it', () => {
    expect(guessAisle('crunchy peanut butter')).toBe('pantry_staples');
    expect(guessAisle('frozen peas')).toBe('frozen');
    expect(guessAisle('vanilla ice cream')).toBe('frozen');
    expect(guessAisle('coconut milk')).toBe('pantry_staples');
  });

  it("the household's own products come first", () => {
    // This household files oat milk under Drinks, not Dairy.
    const oatMilk = product('oatmilk', { name: 'Oat milk', category: 'drinks' });
    expect(guessAisle('oat milk, barista', [oatMilk])).toBe('drinks');
    expect(guessAisle('milk', [oatMilk])).toBe('dairy');
  });

  it('unknown names stay without an aisle', () => {
    expect(guessAisle('batteries')).toBeNull();
    expect(guessAisle('  ')).toBeNull();
    // Whole words only: "pea" is not in "peanut", "ham" is not in "hammer".
    expect(guessAisle('hammer')).toBeNull();
  });
});

describe('StoreOrder', () => {
  it('an aisle visited after one the order puts later moves just after it', () => {
    // Default order: Produce, Dairy, Meat & fish, Bakery, Frozen, ...
    const learned = StoreOrder.learn([], ['bakery'], 'dairy')!;
    expect(learned.slice(0, 5)).toEqual(['produce', 'meat_fish', 'bakery', 'dairy', 'frozen']);
  });

  it('an order that already agrees, the first aisle of a trip and a revisited aisle change nothing', () => {
    expect(StoreOrder.learn([], ['produce'], 'bakery')).toBeNull();
    expect(StoreOrder.learn([], [], 'frozen')).toBeNull();
    expect(StoreOrder.learn([], ['dairy', 'bakery'], 'dairy')).toBeNull();
  });

  it("a few trips settle the store's order", () => {
    let order: Category[] = [];
    const trip: Category[] = ['bakery', 'produce', 'frozen', 'dairy'];
    for (let n = 0; n < 2; n++) {
      trip.forEach((aisle, i) => {
        const next = StoreOrder.learn(order, trip.slice(0, i), aisle);
        if (next) order = next;
      });
    }
    expect(StoreOrder.effective(order).filter((c) => trip.includes(c))).toEqual(trip);
  });

  it('aisles are shown in the saved order, unknown ones in the default order after', () => {
    const sorted = StoreOrder.sort(
      [
        ['produce', []],
        ['dairy', []],
        ['frozen', []],
      ],
      ['frozen', 'produce'],
    );
    expect(sorted.map(([c]) => c)).toEqual(['frozen', 'produce', 'dairy']);
  });

  it('a trip ends after two quiet hours or on another list', () => {
    let now = Date.parse('2026-10-19T10:00:00Z');
    const trip = new ShoppingTrip(() => now);
    expect(trip.checked('g1', 'produce')).toEqual([]);
    expect(trip.checked('g1', 'dairy')).toEqual(['produce']);
    expect(trip.checked('g1', 'produce')).toEqual(['produce', 'dairy']);
    expect(trip.checked('g2', 'bakery')).toEqual([]);
    now += 3 * 60 * 60 * 1000;
    expect(trip.checked('g2', 'frozen')).toEqual([]);
  });
});
