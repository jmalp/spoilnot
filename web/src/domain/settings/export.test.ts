// Ported from ExportFormatTest.
import { describe, expect, it } from 'vitest';
import { ExportFormat, Instant, type ExportData } from './export';

const created = new Instant(Date.parse('2026-10-17T12:00:00Z') / 1000);

describe('ExportFormat', () => {
  it('products, stock, lists with their items, recipes and stats, with times as ISO strings', () => {
    const data: ExportData = {
      household: { id: 'h1', fields: { name: 'Home', createdAt: created } },
      members: [{ id: 'u1', fields: { displayName: 'Sam', role: 'owner' } }],
      products: [{ id: 'p1', fields: { name: 'Milk', minQuantity: 2, recentShelfLifeDays: [5, 7] } }],
      stock: [{ id: 's1', fields: { productId: 'p1', quantity: 1.5, expiryDate: '2026-10-20', note: null } }],
      groceryLists: [
        [{ id: 'g1', fields: { name: 'Groceries' } }, [{ id: 'i1', fields: { name: 'Eggs', checked: false } }]],
      ],
      recipes: [{ id: 'r1', fields: { title: 'Pie', ingredients: [{ raw: '1 cup flour', qty: 1 }] } }],
      stats: [{ id: '2026-10', fields: { used: 4, thrownByDay: { '3': 1 } } }],
      meals: [{ id: 'm1', fields: { date: '2026-10-29', slot: 'dinner', cooks: ['u1'] } }],
    };
    const text = ExportFormat.write(data, created, '1.0.0 (5)');
    const json = JSON.parse(text);
    expect(json.format).toBe('spoilnot-export');
    expect(json.formatVersion).toBe(1);
    expect(json.appVersion).toBe('1.0.0 (5)');
    expect(json.exportedAt).toBe('2026-10-17T12:00:00Z');
    expect(json.household.createdAt).toBe('2026-10-17T12:00:00Z');
    expect(json.products[0].name).toBe('Milk');
    expect(json.stock[0].quantity).toBe(1.5);
    expect(json.stock[0].note).toBeNull();
    expect(json.groceryLists[0].items[0].name).toBe('Eggs');
    expect(json.recipes[0].ingredients[0].raw).toBe('1 cup flour');
    expect(json.stats[0].id).toBe('2026-10');
    expect(json.stats[0].used).toBe(4);
    expect(json.meals[0].slot).toBe('dinner');
    expect(json.meals[0].cooks[0]).toBe('u1');
    // The id first, then the fields in order, indented by four spaces like the app's file.
    expect(Object.keys(json.stock[0])).toEqual(['id', 'expiryDate', 'note', 'productId', 'quantity']);
    expect(text).toContain('\n    "format": "spoilnot-export"');
  });

  it("the person's profile and the activity log, when read (after v1)", () => {
    const empty: ExportData = {
      household: null,
      members: [],
      products: [],
      stock: [],
      groceryLists: [],
      recipes: [],
      stats: [],
      meals: [],
    };
    const none = JSON.parse(ExportFormat.write(empty, created, 'web 3'));
    expect(none.profile).toBeNull();
    expect(none.activity).toEqual([]);

    const json = JSON.parse(
      ExportFormat.write(
        {
          ...empty,
          profile: { id: 'u1', fields: { displayName: 'Sam', units: 'metric', createdAt: created } },
          activity: [{ id: 'a1', fields: { type: 'stock_added', params: { name: 'Milk' }, createdAt: created } }],
        },
        created,
        'web 3',
      ),
    );
    expect(json.profile).toEqual({ id: 'u1', createdAt: '2026-10-17T12:00:00Z', displayName: 'Sam', units: 'metric' });
    expect(json.activity[0].type).toBe('stock_added');
    expect(json.activity[0].params.name).toBe('Milk');
    // After the meals, in the Android app's order.
    expect(Object.keys(json).slice(-3)).toEqual(['meals', 'profile', 'activity']);
  });

  it('file name carries the date', () => {
    expect(ExportFormat.fileName('2026-10-17')).toBe('spoilnot-export-2026-10-17.json');
  });

  it('instants print like java.time.Instant: a fraction only when there is one, in groups of three', () => {
    const at = Date.parse('2026-10-17T12:00:00Z') / 1000;
    expect(new Instant(at).toString()).toBe('2026-10-17T12:00:00Z');
    expect(new Instant(at, 120_000_000).toString()).toBe('2026-10-17T12:00:00.120Z');
    expect(new Instant(at, 123_456_000).toString()).toBe('2026-10-17T12:00:00.123456Z');
    expect(new Instant(at, 123_456_789).toString()).toBe('2026-10-17T12:00:00.123456789Z');
    expect(Instant.ofMillis(at * 1000 + 5).toString()).toBe('2026-10-17T12:00:00.005Z');
  });
});
