// Grocery list rules (DESIGN 5.3), ported from GroceryService, QuickAddParser and GroceryLayout.
import { CATEGORIES, PantryLimits, UNITS, type Category, type DateType, type Location } from '../pantry/models';
import type { PantryChange, PantryData, Product, QuantityUnit } from '../pantry/models';
import { NameKey, naturalOrder } from '../pantry/rules';
import type { AddPlan, PantryService } from '../pantry/service';
import { nullsLast, sortedWith, type LocalDate } from '../time';
import type { Actor, HouseholdChange, HouseholdWriter, Undo } from '../write';
import { guessAisle } from './aisles';
import { GroceryRules, type GroceryChange, type GroceryItem, type GroceryList, type GroceryRepository } from './models';

/** "2 kg rice" in the quick-add field: an optional leading quantity and unit, then the name. */
export type QuickAdd = { name: string; quantity: number | null; unit: QuantityUnit | null };

export function parseQuickAdd(text: string): QuickAdd {
  const clean = NameKey.clean(text);
  const match = /^(\d+(?:[.,]\d+)?)\s+(.*)$/.exec(clean);
  if (!match) return { name: clean, quantity: null, unit: null };
  const quantity = Number(match[1]!.replace(',', '.'));
  const rest = match[2]!;
  const unit =
    [...UNITS]
      .sort((a, b) => b.length - a.length)
      .find((u) => rest.toLowerCase().startsWith(`${u.replace('_', ' ')} `)) ?? null;
  const name = unit === null ? rest : rest.slice(unit.length).trim();
  return name === '' ? { name: clean, quantity: null, unit: null } : { name, quantity, unit };
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

  /** "Increase quantity" from the Already on the list prompt. */
  increase(householdId: string, actor: Actor, listId: string, existing: GroceryItem, request: QuickAdd) {
    const delta = request.quantity ?? 1;
    const change: Change =
      existing.quantity === null
        ? {
            type: 'grocery.updateItem',
            listId,
            itemId: existing.id,
            quantity: 1 + delta,
            unit: existing.unit ?? request.unit,
            note: existing.note,
            category: existing.category,
          }
        : { type: 'grocery.addToItem', listId, itemId: existing.id, delta };
    return this.write(householdId, actor, [change]);
  }

  /** Saves [list]'s learned store order (DESIGN 5.3). */
  setAisleOrder(householdId: string, actor: Actor, list: GroceryList, order: Category[]) {
    return this.write(householdId, actor, [{ type: 'grocery.setAisleOrder', listId: list.id, order }]);
  }

  /** Checking off is logged (DESIGN 5.6); unchecking is not. */
  setChecked(
    householdId: string,
    actor: Actor,
    listId: string,
    item: GroceryItem,
    checked: boolean,
    listName: string | null = null,
  ) {
    const changes: Change[] = [{ type: 'grocery.setChecked', listId, itemId: item.id, checked }];
    if (checked) {
      changes.push({
        type: 'grocery.logEvent',
        eventId: this.writer.newId(),
        event: 'grocery_checked',
        name: item.name,
        listName,
        count: null,
      });
    }
    return this.write(householdId, actor, changes);
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
    const changes: Change[] = [
      {
        type: 'grocery.updateItem',
        listId,
        itemId: item.id,
        quantity: edit.quantity,
        unit: edit.unit,
        note: edit.note?.trim() || null,
        category: edit.category,
      },
    ];
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
    await this.write(householdId, actor, [
      ...ids.map((itemId): Change => ({ type: 'grocery.deleteItem', listId: list.id, itemId, tombstone: false })),
      { type: 'grocery.deleteList', listId: list.id },
    ]);
  }

  /**
   * Done shopping: creates the stock batches (and any new products) for [entries], and removes those items and the
   * [skipped] ones from the list, all in one batched write.
   */
  async addToPantry(
    householdId: string,
    actor: Actor,
    listId: string,
    entries: PantryEntry[],
    skipped: GroceryItem[],
    pantryData: PantryData,
  ): Promise<void> {
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
      );
      const learned = learnAlias(entry.item.aliasOf, plan);
      changes.push(...learned.changes, remove(entry.item.id));
      data = learned.after;
    }
    for (const item of skipped) changes.push(remove(item.id));
    if (changes.length > 0) await this.writer.apply(householdId, actor, changes);
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
