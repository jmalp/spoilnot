import { describe, expect, it } from 'vitest';
import type { GroceryItem } from '../../domain/grocery/models';
import { batch, product, TODAY } from '../../domain/pantry/fixtures';
import type { PantryData } from '../../domain/pantry/models';
import { AddToPantryDrafts, type EntryEdit } from './addToPantryDrafts';

function item(id: string, name: string, quantity: number | null = null): GroceryItem {
  return {
    id,
    name,
    nameKey: name.toLowerCase(),
    quantity,
    unit: null,
    note: null,
    category: null,
    checked: true,
    checkedBy: 'u2',
    source: 'manual',
    productId: null,
    recipeId: null,
    createdBy: 'u1',
    createdAt: null,
    pendingSync: false,
    aliasOf: null,
  };
}

const empty: PantryData = { products: [], batches: [], fromCache: false };
const butter: EntryEdit = { quantity: '3', unit: 'pcs', expiry: '2026-10-26', location: 'freezer', skip: false };

describe('AddToPantryDrafts (REL-08 on the web)', () => {
  it("keeps an edited line when someone else's check-off changes the cart", () => {
    let draft = AddToPantryDrafts.open('list1');
    const before = [item('b', 'Butter'), item('m', 'Milk', 2)];
    draft = AddToPantryDrafts.setEdit(draft, 'b', butter);
    draft = AddToPantryDrafts.setExpanded(draft, ['b'], true);

    // Another phone checks off Sugar and unchecks Milk.
    const after = [item('b', 'Butter'), item('s', 'Sugar')];
    const lines = AddToPantryDrafts.lines(after, empty, TODAY, draft);
    expect(lines.map((l) => l.item.id)).toEqual(['b', 's']);
    expect(lines[0]!.value).toEqual(butter);
    expect(lines[0]!.expanded).toBe(true);
    // The new line starts from its defaults.
    expect(lines[1]!.value).toEqual({ quantity: '1', unit: 'pcs', expiry: null, location: 'fridge', skip: false });
    expect(lines[1]!.expanded).toBe(false);
    // Milk, checked again, comes back with its defaults.
    expect(AddToPantryDrafts.lines(before, empty, TODAY, draft)[1]!.value.quantity).toBe('2');
  });

  it('untouched lines follow their product, and a product with stock keeps its unit over an edit', () => {
    const flour = product('flour', { unit: 'kg', location: 'pantry' });
    const pantry: PantryData = {
      products: [flour],
      batches: [batch('b1', 'flour', { quantity: 1 })],
      fromCache: false,
    };
    let draft = AddToPantryDrafts.open('list1');
    expect(AddToPantryDrafts.lines([item('f', 'Flour')], pantry, TODAY, draft)[0]).toMatchObject({
      value: { unit: 'kg', location: 'pantry' },
      unitLocked: true,
    });
    draft = AddToPantryDrafts.setEdit(draft, 'f', { ...butter, unit: 'g' });
    expect(AddToPantryDrafts.lines([item('f', 'Flour')], pantry, TODAY, draft)[0]!.value).toMatchObject({
      quantity: '3',
      unit: 'kg',
    });
  });

  it('expanding several lines adds each once; collapsing removes it', () => {
    let draft = AddToPantryDrafts.setExpanded(AddToPantryDrafts.open('l'), ['a', 'b'], true);
    draft = AddToPantryDrafts.setExpanded(draft, ['b', 'c'], true);
    expect(draft.expanded).toEqual(['a', 'b', 'c']);
    expect(AddToPantryDrafts.setExpanded(draft, ['b'], false).expanded).toEqual(['a', 'c']);
  });

  it('reads back what it saved, and refuses or trims what it did not', () => {
    const draft = AddToPantryDrafts.setEdit(AddToPantryDrafts.open('list1'), 'b', butter);
    expect(AddToPantryDrafts.parse(JSON.parse(JSON.stringify(draft)))).toEqual(draft);
    expect(AddToPantryDrafts.parse(null)).toBeNull();
    expect(AddToPantryDrafts.parse({ listId: 1, edits: {}, expanded: [] })).toBeNull();
    expect(
      AddToPantryDrafts.parse({
        listId: 'l',
        edits: {
          ok: butter,
          badUnit: { ...butter, unit: 'cup' },
          badDate: { ...butter, expiry: 'tomorrow' },
          noDate: { ...butter, expiry: null },
        },
        expanded: ['ok', 3],
      }),
    ).toEqual({ listId: 'l', edits: { ok: butter, noDate: { ...butter, expiry: null } }, expanded: ['ok'] });
  });
});
