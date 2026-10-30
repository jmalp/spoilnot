// Grocery lists (DESIGN 5.3, 8.2), ported from feature/grocery/domain/GroceryModels.
import type { Observable } from 'rxjs';
import type { Category, QuantityUnit } from '../pantry/models';
import type { HouseholdChange } from '../write';

/** households/{hid}/groceryLists/{listId}. [aisleOrder]: this list's store order, learned from check-offs. */
export type GroceryList = { id: string; name: string; isDefault: boolean; aisleOrder: Category[] };

/** Where an item came from. `meal_plan` (after v1) reads as manual on older app versions. */
export const ITEM_SOURCES = ['manual', 'low_stock', 'recipe', 'meal_plan'] as const;
export type ItemSource = (typeof ITEM_SOURCES)[number];
export const itemSourceOf = (value: unknown): ItemSource =>
  (ITEM_SOURCES as readonly unknown[]).includes(value) ? (value as ItemSource) : 'manual';

/** households/{hid}/groceryLists/{listId}/items/{itemId}. */
export type GroceryItem = {
  id: string;
  name: string;
  nameKey: string;
  quantity: number | null;
  unit: QuantityUnit | null;
  note: string | null;
  category: Category | null;
  checked: boolean;
  checkedBy: string | null;
  source: ItemSource;
  productId: string | null;
  recipeId: string | null;
  createdBy: string | null;
  /** Epoch milliseconds; estimated while the server timestamp is pending. */
  createdAt: number | null;
  pendingSync: boolean;
  /** The recipe name (a name key) this item was renamed from, taught to its product in the pantry (DESIGN 5.5). */
  aliasOf: string | null;
};

export type GroceryEventType = 'grocery_added' | 'grocery_checked';

/** One write in a grocery plan, applied by the HouseholdWriter together with any pantry changes. */
export type GroceryChange =
  | { type: 'grocery.createList'; list: GroceryList }
  | { type: 'grocery.renameList'; listId: string; name: string }
  /** The store order learned from check-offs (DESIGN 5.3). */
  | { type: 'grocery.setAisleOrder'; listId: string; order: Category[] }
  | { type: 'grocery.deleteList'; listId: string }
  | { type: 'grocery.createItem'; listId: string; item: GroceryItem }
  /** Puts a deleted item back under its old id (Undo). */
  | { type: 'grocery.restoreItem'; listId: string; item: GroceryItem }
  | {
      type: 'grocery.updateItem';
      listId: string;
      itemId: string;
      quantity: number | null;
      unit: QuantityUnit | null;
      note: string | null;
      category: Category | null;
    }
  /** A new name (after v1), relinked to the household's product of that name, if any. */
  | {
      type: 'grocery.renameItem';
      listId: string;
      itemId: string;
      name: string;
      nameKey: string;
      productId: string | null;
    }
  | { type: 'grocery.addToItem'; listId: string; itemId: string; delta: number }
  | { type: 'grocery.setChecked'; listId: string; itemId: string; checked: boolean }
  /** [tombstone] false only when the whole list goes, since nothing listens to a deleted list's items. */
  | { type: 'grocery.deleteItem'; listId: string; itemId: string; tombstone: boolean }
  /** grocery_added or grocery_checked in Recent activity; [count] when several came at once from recipe [name]. */
  | {
      type: 'grocery.logEvent';
      eventId: string;
      event: GroceryEventType;
      name: string;
      listName: string | null;
      count: number | null;
    }
  /**
   * The restock engine's merge-write of `low_{productId}` (DESIGN 9.6). Never writes `checked`, so an item already in
   * the cart stays there; [writeCreated] is false when the item is already in the local cache.
   */
  | {
      type: 'grocery.upsertLowStock';
      listId: string;
      productId: string;
      name: string;
      nameKey: string;
      unit: QuantityUnit;
      category: Category;
      quantity: number;
      writeCreated: boolean;
    };

export const isGroceryChange = (change: HouseholdChange): change is GroceryChange => change.type.startsWith('grocery.');

export const GroceryRules = {
  MAX_NAME_LENGTH: 80,
  MAX_LIST_NAME_LENGTH: 60,
  MAX_NOTE_LENGTH: 200,
  /** DESIGN 8.2: low-stock items use a deterministic id, so two phones write the same document. */
  lowStockItemId: (productId: string) => `low_${productId}`,
};

/** Grocery lists and their items. Writes go through the HouseholdWriter. */
export interface GroceryRepository {
  /** List names, always listened to (DESIGN 9.1). */
  observeLists(householdId: string): Observable<GroceryList[]>;
  /** Items of one list, while it is visible (DESIGN 9.1). */
  observeItems(householdId: string, listId: string): Observable<GroceryItem[]>;
  /** Whether an item is in the local cache, so the restock engine knows whether to write createdAt. */
  isCached(householdId: string, listId: string, itemId: string): Promise<boolean>;
  /** Every item id of a list, read from the server, for deleting a list. */
  itemIds(householdId: string, listId: string): Promise<string[]>;
}
