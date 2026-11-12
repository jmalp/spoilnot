// Grocery list rules (DESIGN 5.3), ported from GroceryService, QuickAddParser and GroceryLayout.
import { CATEGORIES, PantryLimits, UNITS, type Category, type DateType, type Location } from '../pantry/models';
import type { PantryChange, PantryData, Product, QuantityUnit } from '../pantry/models';
import { NameKey, naturalOrder, ShelfLife } from '../pantry/rules';
import type { AddPlan, PantryService } from '../pantry/service';
import { nullsLast, sortedWith, type LocalDate } from '../time';
import type { Actor, HouseholdChange, HouseholdWriter, Undo } from '../write';
import { UNITS as RECIPE_WORDS } from '../recipes/parser';
import { guessAisle } from './aisles';
import {
  GroceryRules,
  groceryItemFieldsOf,
  type GroceryChange,
  type GroceryItem,
  type GroceryItemFields,
  type GroceryList,
  type GroceryRepository,
} from './models';

/** "2 kg rice" in the quick-add field: an optional leading quantity and unit, then the name. */
export type QuickAdd = { name: string; quantity: number | null; unit: QuantityUnit | null };

/**
 * Whether "Increase quantity" can add `request` to `existing` (after v1, LOGIC-08): only in the same unit, or when
 * one side has none. There is no unit conversion (DESIGN 2), so 2 kg is never added to 500 g.
 */
export const canIncrease = (request: QuickAdd, existing: GroceryItem): boolean =>
  existing.unit === null || request.unit === null || existing.unit === request.unit;

/** The recipe parser's words for the units the pantry has (after v1, LOGIC-08: "3 lbs", "2 litres"). */
const RECIPE_UNITS: Record<string, QuantityUnit> = { g: 'g', kg: 'kg', ml: 'ml', l: 'l', oz: 'oz', lb: 'lb' };
const UNIT_WORDS = new Map<string, QuantityUnit>([
  ...UNITS.map((u): [string, QuantityUnit] => [u.replace('_', ' '), u]),
  ...RECIPE_WORDS.flatMap(([canonical, words]) => {
    const unit = RECIPE_UNITS[canonical];
    return unit ? words.map((w): [string, QuantityUnit] => [w, unit]) : [];
  }),
]);
const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
/** A unit word, an optional dot, then a space: "2 lemons" is not 2 litres of "emons". */
const UNIT = new RegExp(
  `^(${[...UNIT_WORDS.keys()]
    .sort((a, b) => b.length - a.length)
    .map(escape)
    .join('|')})\\.?\\s+(.*)$`,
  'i',
);

export function parseQuickAdd(text: string): QuickAdd {
  const clean = NameKey.clean(text);
  const match = /^(\d+(?:[.,]\d+)?)(\s*)(.*)$/.exec(clean);
  if (!match) return { name: clean, quantity: null, unit: null };
  const quantity = Number(match[1]!.replace(',', '.'));
  const rest = match[3]!;
  const unitMatch = UNIT.exec(rest);
  const unit = unitMatch ? (UNIT_WORDS.get(unitMatch[1]!.toLowerCase()) ?? null) : null;
  // A unit may follow the number directly ("2l milk", "500g rice"); a name may not ("7up").
  if (unit === null && match[2] === '') return { name: clean, quantity: null, unit: null };
  const name = unitMatch ? unitMatch[2]!.trim() : rest;
  if (name === '') return { name: clean, quantity: null, unit: null };
  // "0 eggs" means eggs: an amount of nothing is no amount.
  if (quantity <= 0) return { name, quantity: null, unit: null };
  return { name, quantity, unit };
}

/** Outcome of the quick-add field: added, or already on the list unchecked (ask to increase it or add anyway). */
export type QuickAddResult = { kind: 'added' } | { kind: 'alreadyOnList'; existing: GroceryItem; request: QuickAdd };

/** One checked item in the Add to pantry sheet. */
export type PantryEntry = {
  item: GroceryItem;
  quantity: number;
  unit: QuantityUnit;
  expiryDate: LocalDate | null;
  location: Location;
  dateType: DateType;
};

/**
 * What a line of the Add to pantry sheet starts with (DESIGN 5.3). `product` is the one the item adds to: its linked
 * product while it exists, else the one of its name (after v1, LOGIC-03). A product with stock fixes the unit
 * (`unitLocked`, DESIGN 2: no conversion, LOGIC-04), and the item's amount is used only in that unit or when the item
 * has none; otherwise the product's last added amount, or none, so the person types it.
 */
export type EntryDefaults = {
  product: Product | null;
  quantity: number | null;
  unit: QuantityUnit;
  unitLocked: boolean;
  expiry: LocalDate | null;
  location: Location;
};

export const AddToPantryRules = {
  productFor(item: GroceryItem, products: readonly Product[]): Product | null {
    return (
      (item.productId === null ? undefined : products.find((p) => p.id === item.productId)) ??
      products.find((p) => p.nameKey === NameKey.of(item.name)) ??
      null
    );
  },

  defaults(item: GroceryItem, data: PantryData, today: LocalDate): EntryDefaults {
    const product = AddToPantryRules.productFor(item, data.products);
    const unitLocked = product !== null && data.batches.some((b) => b.productId === product.id && b.quantity > 0);
    const unit = unitLocked ? product.unit : (item.unit ?? product?.unit ?? 'pcs');
    const sameUnit = item.unit === null || item.unit === unit;
    const last = product?.lastAddedQuantity ?? null;
    return {
      product,
      quantity: sameUnit ? (item.quantity ?? 1) : last !== null && last > 0 ? last : null,
      unit,
      unitLocked,
      expiry: ShelfLife.suggestExpiry(product?.typicalShelfLifeDays ?? null, today),
      location: product?.defaultLocation ?? 'fridge',
    };
  },
};

/** The list screen: unchecked items by aisle, then the In cart section (DESIGN 5.3). */
export type GroceryView = { aisles: [Category, GroceryItem[]][]; inCart: GroceryItem[] };

export const GroceryLayout = {
  of(items: GroceryItem[]): GroceryView {
    const byTime = [
      (a: GroceryItem, b: GroceryItem) => nullsLast<number>((x, y) => x - y)(a.createdAt, b.createdAt),
      (a: GroceryItem, b: GroceryItem) => naturalOrder(a.name, b.name),
    ];
    const toBuy = items.filter((i) => !i.checked);
    const aisles = CATEGORIES.map((c): [Category, GroceryItem[]] => [
      c,
      sortedWith(
        toBuy.filter((i) => (i.category ?? 'other') === c),
        ...byTime,
      ),
    ]).filter(([, list]) => list.length > 0);
    return {
      aisles,
      inCart: sortedWith(
        items.filter((i) => i.checked),
        (a, b) => naturalOrder(a.name, b.name),
      ),
    };
  },

  /** Pantry products and names already used on the list, for the quick-add suggestions; prefix matches first. */
  suggestions(query: string, items: GroceryItem[], pantry: PantryData, limit = 5): string[] {
    const key = NameKey.of(query);
    if (key === '') return [];
    const seen = new Set<string>();
    const names = [...pantry.products.map((p) => p.name), ...items.map((i) => i.name)].filter((name) => {
      const k = NameKey.of(name);
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });
    return sortedWith(
      names.filter((n) => NameKey.of(n).includes(key) && NameKey.of(n) !== key),
      (a, b) => Number(!NameKey.of(a).startsWith(key)) - Number(!NameKey.of(b).startsWith(key)),
    ).slice(0, limit);
  },
};

type Change = GroceryChange | PantryChange;

function cleanListName(name: string): string {
  const clean = NameKey.clean(name);
  if (clean === '' || clean.length > GroceryRules.MAX_LIST_NAME_LENGTH) throw new Error('Invalid list name');
  return clean;
}

/** Grocery list rules. Plans go to the HouseholdWriter, together with pantry changes when needed. */
export class GroceryService {
  constructor(
    private readonly writer: HouseholdWriter,
    private readonly repository: GroceryRepository,
    private readonly pantry: PantryService,
  ) {}

  private write(householdId: string, actor: Actor, changes: Change[]): Promise<void> {
    return this.writer.apply(householdId, actor, changes);
  }

  /** The quick-add field. Links a matching pantry product, so the item gets its category (aisle). */
  async quickAdd(
    householdId: string,
    actor: Actor,
    listId: string,
    text: string,
    items: GroceryItem[],
    pantryData: PantryData,
    { addAnyway = false, listName = null as string | null } = {},
  ): Promise<QuickAddResult> {
    const request = parseQuickAdd(text);
    if (request.name === '' || request.name.length > GroceryRules.MAX_NAME_LENGTH) throw new Error('Invalid name');
    const key = NameKey.of(request.name);
    if (!addAnyway) {
      const existing = items.find((i) => !i.checked && i.nameKey === key);
      if (existing) return { kind: 'alreadyOnList', existing, request };
    }
    const product = pantryData.products.find((p) => p.nameKey === key) ?? null;
    const item: GroceryItem = {
      id: this.writer.newId(),
      name: product?.name ?? request.name,
      nameKey: key,
      quantity: request.quantity,
      unit: request.unit ?? (request.quantity !== null ? (product?.unit ?? null) : null),
      note: null,
      // Typed names get a guessed aisle, so they sort into the store order (DESIGN 5.3).
      category: product?.category ?? guessAisle(request.name, pantryData.products),
      checked: false,
      checkedBy: null,
      source: 'manual',
      productId: product?.id ?? null,
      recipeId: null,
      createdBy: actor.uid,
      createdAt: null,
      pendingSync: false,
      aliasOf: null,
    };
    await this.write(householdId, actor, [
      { type: 'grocery.createItem', listId, item },
      {
        type: 'grocery.logEvent',
        eventId: this.writer.newId(),
        event: 'grocery_added',
        name: item.name,
        listName,
        count: null,
      },
    ]);
    return { kind: 'added' };
  }

  /** "Increase quantity" from the Already on the list prompt, offered only when canIncrease. */
  increase(householdId: string, actor: Actor, listId: string, existing: GroceryItem, request: QuickAdd) {
    if (!canIncrease(request, existing)) return Promise.reject(new Error('Different units'));
    const delta = request.quantity ?? 1;
    const change: Change =
      existing.quantity === null
        ? {
            type: 'grocery.updateItem',
            listId,
            itemId: existing.id,
            before: groceryItemFieldsOf(existing),
            after: { ...groceryItemFieldsOf(existing), quantity: 1 + delta, unit: existing.unit ?? request.unit },
          }
        : { type: 'grocery.addToItem', listId, itemId: existing.id, delta };
    return this.write(householdId, actor, [change]);
  }

  /** Saves [list]'s learned store order (DESIGN 5.3). */
  setAisleOrder(householdId: string, actor: Actor, list: GroceryList, order: Category[]) {
    return this.write(householdId, actor, [{ type: 'grocery.setAisleOrder', listId: list.id, order }]);
  }

  /**
   * Checking off or unchecking. Not logged in Recent activity since after v1 (R15): the list shows who checked what,
   * and one shopping trip pushed everything else out of the card.
   */
  setChecked(householdId: string, actor: Actor, listId: string, item: GroceryItem, checked: boolean) {
    return this.write(householdId, actor, [{ type: 'grocery.setChecked', listId, itemId: item.id, checked }]);
  }

  /** Quantity, unit, note and aisle; a new name (after v1) relinks the item to the product of that name. */
  edit(
    householdId: string,
    actor: Actor,
    listId: string,
    item: GroceryItem,
    edit: {
      quantity: number | null;
      unit: QuantityUnit | null;
      note: string | null;
      category: Category | null;
      name?: string | null;
    },
    products: Product[] = [],
  ) {
    // Only what the person changed from `item`, the copy the sheet opened with, so another member's change to the
    // item meanwhile stays (after v1, DESIGN 9.4).
    const changes: Change[] = [];
    const before = groceryItemFieldsOf(item);
    const after: GroceryItemFields = {
      quantity: edit.quantity,
      unit: edit.unit,
      note: edit.note?.trim() || null,
      category: edit.category,
    };
    if (JSON.stringify(after) !== JSON.stringify(before))
      changes.push({ type: 'grocery.updateItem', listId, itemId: item.id, before, after });
    const clean = edit.name == null ? null : NameKey.clean(edit.name).slice(0, GroceryRules.MAX_NAME_LENGTH).trim();
    if (clean && clean !== item.name) {
      const key = NameKey.of(clean);
      const product = products.find((p) => p.nameKey === key) ?? null;
      changes.push({
        type: 'grocery.renameItem',
        listId,
        itemId: item.id,
        name: product?.name ?? clean,
        nameKey: key,
        productId: product?.id ?? null,
      });
    }
    if (changes.length === 0) return Promise.resolve();
    return this.write(householdId, actor, changes);
  }

  async delete(householdId: string, actor: Actor, listId: string, item: GroceryItem): Promise<Undo> {
    await this.write(householdId, actor, [{ type: 'grocery.deleteItem', listId, itemId: item.id, tombstone: true }]);
    const restore: Change = { type: 'grocery.restoreItem', listId, item };
    return { changes: [restore] };
  }

  /** Clear checked removes everything in the cart. */
  async clearChecked(householdId: string, actor: Actor, listId: string, items: GroceryItem[]): Promise<Undo | null> {
    const checked = items.filter((i) => i.checked);
    if (checked.length === 0) return null;
    await this.write(
      householdId,
      actor,
      checked.map((i): Change => ({ type: 'grocery.deleteItem', listId, itemId: i.id, tombstone: true })),
    );
    return { changes: checked.map((item): Change => ({ type: 'grocery.restoreItem', listId, item })) };
  }

  async createList(householdId: string, actor: Actor, name: string): Promise<string> {
    const id = this.writer.newId();
    const list: GroceryList = { id, name: cleanListName(name), isDefault: false, aisleOrder: [] };
    await this.write(householdId, actor, [{ type: 'grocery.createList', list }]);
    return id;
  }

  renameList(householdId: string, actor: Actor, list: GroceryList, name: string) {
    return this.write(householdId, actor, [{ type: 'grocery.renameList', listId: list.id, name: cleanListName(name) }]);
  }

  /** Deletes a list and its items (no cascade in Firestore). The default list cannot be deleted. */
  async deleteList(householdId: string, actor: Actor, list: GroceryList): Promise<void> {
    if (list.isDefault) throw new Error('The default list cannot be deleted');
    const ids = await this.repository.itemIds(householdId, list.id);
    // The list goes first: the rules let a member delete items for real only once their list is gone, also in a
    // later batch (DESIGN 8.5).
    await this.write(householdId, actor, [
      { type: 'grocery.deleteList', listId: list.id },
      ...ids.map((itemId): Change => ({ type: 'grocery.deleteItem', listId: list.id, itemId, tombstone: false })),
    ]);
  }

  /**
   * Done shopping: creates the stock batches (and any new products) for [entries], and removes those items and the
   * [skipped] ones from the list, all in one batched write. Each entry adds to its item's linked product while it
   * exists (after v1, LOGIC-03). The Undo (LOGIC-10) removes what it added (new batches and products as tombstones,
   * amounts added to existing batches as increments) and puts the items back; null when nothing was written.
   */
  async addToPantry(
    householdId: string,
    actor: Actor,
    listId: string,
    entries: PantryEntry[],
    skipped: GroceryItem[],
    pantryData: PantryData,
  ): Promise<Undo | null> {
    let data = pantryData;
    const changes: HouseholdChange[] = [];
    const remove = (itemId: string): Change => ({ type: 'grocery.deleteItem', listId, itemId, tombstone: true });
    for (const entry of entries) {
      const plan = this.pantry.planAdd(
        {
          name: entry.item.name,
          quantity: entry.quantity,
          unit: entry.unit,
          expiryDate: entry.expiryDate,
          dateType: entry.dateType,
          location: entry.location,
          category: entry.item.category ?? 'other',
          note: null,
          minQuantity: null,
          autoAddToGrocery: false,
          buyQuantity: null,
        },
        data,
        entry.item.productId,
      );
      const learned = learnAlias(entry.item.aliasOf, plan);
      changes.push(...learned.changes, remove(entry.item.id));
      data = learned.after;
    }
    for (const item of skipped) changes.push(remove(item.id));
    if (changes.length === 0) return null;
    await this.writer.apply(householdId, actor, changes);
    const restore = [...entries.map((e) => e.item), ...skipped].map((item): Change => ({
      type: 'grocery.restoreItem',
      listId,
      item,
    }));
    return { changes: [...this.pantry.undoAdditions(changes), ...restore] };
  }

  undo(householdId: string, actor: Actor, undo: Undo) {
    return this.writer.apply(householdId, actor, undo.changes);
  }
}

/**
 * A renamed recipe item teaches its product the recipe's name (DESIGN 5.5): a new product is created with it, an
 * existing one gets it added, unless it already has it, it is the product's own name, or the product is full.
 */
function learnAlias(alias: string | null, plan: AddPlan): AddPlan {
  if (!alias) return plan;
  const created = plan.changes.find(
    (c): c is Extract<PantryChange, { type: 'pantry.createProduct' }> => c.type === 'pantry.createProduct',
  );
  const product = plan.after.products.find((p) => p.id === plan.result.productId);
  if (
    !product ||
    alias === product.nameKey ||
    product.aliases.includes(alias) ||
    product.aliases.length >= PantryLimits.MAX_ALIASES
  ) {
    return plan;
  }
  const taught: Product = { ...product, aliases: [...product.aliases, alias] };
  const replaced: PantryChange = { type: 'pantry.createProduct', product: taught };
  const added: PantryChange = { type: 'pantry.addAlias', productId: product.id, alias };
  const changes = created ? plan.changes.map((c) => (c === created ? replaced : c)) : [...plan.changes, added];
  const after = { ...plan.after, products: plan.after.products.map((p) => (p.id === product.id ? taught : p)) };
  return { ...plan, changes, after };
}
