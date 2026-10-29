// Pantry business rules (DESIGN 5.2, 8.5), ported from PantryService: each action builds a plan of changes from the
// current data and hands it to the HouseholdWriter, which writes it as one batch.
import { AppError } from '../../core/errors';
import { StatsChanges, type StatsChange } from '../insights/stats';
import {
  addDays,
  compareStrings,
  minDate,
  nullsLast,
  sortedWith,
  today as todayOf,
  type Clock,
  type LocalDate,
} from '../time';
import type { Actor, HouseholdChange, HouseholdWriter, Undo } from '../write';
import {
  PantryLimits,
  type BatchEdit,
  type Category,
  type DateType,
  type DiscardReason,
  type Location,
  type PantryChange,
  type PantryData,
  type Product,
  type QuantityUnit,
  type StockBatch,
} from './models';
import { NameKey, ShelfLife } from './rules';

/** What a person enters in the Add item sheet. */
export type StockDraft = {
  name: string;
  quantity: number;
  unit: QuantityUnit;
  expiryDate: LocalDate | null;
  dateType: DateType;
  location: Location;
  category: Category;
  note: string | null;
  minQuantity: number | null;
  autoAddToGrocery: boolean;
  buyQuantity: number | null;
};

/** The Edit window: one purchase and its product together. */
export type ItemEdit = {
  name: string;
  quantity: number;
  unit: QuantityUnit;
  category: Category;
  expiryDate: LocalDate | null;
  location: Location;
  autoAddToGrocery: boolean;
  minQuantity: number | null;
  buyQuantity: number | null;
};

/** Changes to a product from its detail screen. */
export type ProductEdit = {
  name: string;
  category: Category;
  defaultLocation: Location;
  unit: QuantityUnit;
  minQuantity: number | null;
  autoAddToGrocery: boolean;
  buyQuantity: number | null;
  typicalShelfLifeDays: number | null;
};

export type AddResult = { productId: string; batchId: string; merged: boolean };
/** The writes for one added batch, plus the data as it will look afterwards, so several adds plan into one write. */
export type AddPlan = { changes: HouseholdChange[]; result: AddResult; after: PantryData };

/** The changes a pantry plan writes. */
type Change = PantryChange | StatsChange;

/** Quantities are numbers; amounts this close count as equal. */
const EPSILON = 1e-9;

const undoOf = (...changes: Change[]): Undo => ({ changes });

const cleanNote = (note: string | null) => note?.trim().slice(0, PantryLimits.MAX_NOTE_LENGTH).trim() || null;

function requireName(name: string): void {
  if (name === '' || name.length > PantryLimits.MAX_NAME_LENGTH) throw new Error('Invalid name');
}

export class PantryService {
  constructor(
    private readonly writer: HouseholdWriter,
    private readonly clock: Clock,
  ) {}

  private today(): LocalDate {
    return todayOf(this.clock);
  }

  private write(householdId: string, actor: Actor, changes: Change[]): Promise<void> {
    return this.writer.apply(householdId, actor, changes);
  }

  /**
   * Saves a new batch. Reuses the product with the same name key, keeps its unit (no conversion), and increments
   * an existing batch with the same product, date and location instead of duplicating it.
   */
  async add(householdId: string, actor: Actor, draft: StockDraft, data: PantryData): Promise<AddResult> {
    const plan = this.planAdd(draft, data);
    await this.writer.apply(householdId, actor, plan.changes);
    return plan.result;
  }

  planAdd(draft: StockDraft, data: PantryData): AddPlan {
    const name = NameKey.clean(draft.name);
    requireName(name);
    if (!(draft.quantity > 0)) throw new Error('Quantity must be above 0');
    const key = NameKey.of(name);
    const existing = data.products.find((p) => p.nameKey === key);
    const changes: Change[] = [];
    let product: Product;
    if (!existing) {
      const recent = ShelfLife.record([], this.today(), draft.expiryDate);
      product = {
        id: this.writer.newId(),
        name,
        nameKey: key,
        category: draft.category,
        defaultLocation: draft.location,
        unit: draft.unit,
        minQuantity: draft.minQuantity,
        autoAddToGrocery: draft.minQuantity !== null && draft.autoAddToGrocery,
        buyQuantity: draft.minQuantity !== null ? draft.buyQuantity : null,
        restockArmed: true,
        typicalShelfLifeDays: ShelfLife.median(recent),
        recentShelfLifeDays: recent,
        createdAt: null,
        lastAddedQuantity: draft.quantity,
        aliases: [],
        openedShelfLifeDays: null,
      };
      changes.push({ type: 'pantry.createProduct', product });
    } else {
      const recent = ShelfLife.record(existing.recentShelfLifeDays, this.today(), draft.expiryDate);
      const hasStock = data.batches.some((b) => b.productId === existing.id && b.quantity > 0);
      // The unit is fixed once a product has stock (invariant 3).
      const unit = hasStock ? existing.unit : draft.unit;
      const updated = withKeepAtLeast(
        {
          ...existing,
          unit,
          recentShelfLifeDays: recent,
          typicalShelfLifeDays: ShelfLife.median(recent) ?? existing.typicalShelfLifeDays,
          // An amount in another unit would be meaningless in this one.
          lastAddedQuantity: unit === draft.unit ? draft.quantity : existing.lastAddedQuantity,
        },
        draft.minQuantity,
        draft.autoAddToGrocery,
        draft.buyQuantity,
        existing,
      );
      if (JSON.stringify(updated) !== JSON.stringify(existing))
        changes.push({ type: 'pantry.updateProduct', product: updated });
      product = updated;
    }

    // An opened batch keeps to itself: new, sealed stock is not added to it.
    const target = data.batches.find(
      (b) =>
        b.productId === product.id &&
        b.expiryDate === draft.expiryDate &&
        b.location === draft.location &&
        b.quantity > 0 &&
        b.openedOn === null,
    );
    let batches = data.batches;
    let batchId: string;
    if (target) {
      changes.push({ type: 'pantry.addToBatch', batchId: target.id, delta: draft.quantity });
      batches = batches.map((b) => (b.id === target.id ? { ...b, quantity: b.quantity + draft.quantity } : b));
      batchId = target.id;
    } else {
      batchId = this.writer.newId();
      const batch: StockBatch = {
        id: batchId,
        productId: product.id,
        productName: product.name,
        unit: product.unit,
        category: product.category,
        quantity: draft.quantity,
        expiryDate: draft.expiryDate,
        dateType: draft.dateType,
        location: draft.location,
        note: cleanNote(draft.note),
        createdAt: null,
        pendingSync: false,
        openedOn: null,
      };
      changes.push({ type: 'pantry.createBatch', batch });
      batches = [...batches, batch];
    }
    changes.push({
      type: 'pantry.logEvent',
      eventId: this.writer.newId(),
      eventType: 'added',
      productId: product.id,
      name: product.name,
      quantity: draft.quantity,
      reason: null,
    });
    return {
      changes,
      result: { productId: product.id, batchId, merged: target !== undefined },
      after: { ...data, products: [...data.products.filter((p) => p.id !== product.id), product], batches },
    };
  }

  /** +1 or -1. Going to 0 or below uses the batch up, which can be undone. */
  async adjust(householdId: string, actor: Actor, batch: StockBatch, delta: number): Promise<Undo | null> {
    if (batch.quantity + delta <= 0) return this.usedUp(householdId, actor, batch);
    await this.write(householdId, actor, [{ type: 'pantry.addToBatch', batchId: batch.id, delta }]);
    return null;
  }

  /** Deletes the batch and counts it as used for Insights (DESIGN 5.7). Undo restores it and the count. */
  async usedUp(householdId: string, actor: Actor, batch: StockBatch): Promise<Undo> {
    const counted = StatsChanges.usedUp(this.today());
    await this.write(householdId, actor, [{ type: 'pantry.deleteBatch', batchId: batch.id }, counted]);
    return undoOf({ type: 'pantry.restoreBatch', batch }, StatsChanges.undo(counted));
  }

  /**
   * Marks [count] of [batch] opened today, to be used within [days] (DESIGN 5.2). The date only ever moves earlier.
   * Opening some of a batch kept in pieces moves those to a batch of their own; otherwise the whole batch is opened.
   * [product] remembers [days]. Undo puts the batch back as it was (the remembered days stay).
   */
  async markOpened(
    householdId: string,
    actor: Actor,
    batch: StockBatch,
    count: number,
    days: number,
    product: Product | null,
  ): Promise<Undo> {
    if (!(days >= 1 && days <= PantryLimits.MAX_OPENED_DAYS)) throw new Error('Invalid days');
    const opened = this.today();
    const byDays = addDays(opened, days);
    const expiry = batch.expiryDate ? minDate(batch.expiryDate, byDays) : byDays;
    const changes: Change[] = [];
    const undo: Change[] = [];
    if (batch.unit === 'pcs' && count > 0 && count < batch.quantity - EPSILON) {
      const part: StockBatch = {
        ...batch,
        id: this.writer.newId(),
        quantity: count,
        expiryDate: expiry,
        openedOn: opened,
        createdAt: null,
      };
      changes.push(
        { type: 'pantry.addToBatch', batchId: batch.id, delta: -count },
        { type: 'pantry.createBatch', batch: part },
      );
      undo.push(
        { type: 'pantry.deleteBatch', batchId: part.id },
        { type: 'pantry.addToBatch', batchId: batch.id, delta: count },
      );
    } else {
      changes.push({ type: 'pantry.markOpened', batchId: batch.id, openedOn: opened, expiryDate: expiry });
      undo.push({
        type: 'pantry.markOpened',
        batchId: batch.id,
        openedOn: batch.openedOn,
        expiryDate: batch.expiryDate,
      });
    }
    if (product && product.openedShelfLifeDays !== days) {
      changes.push({ type: 'pantry.setOpenedShelfLife', productId: product.id, days });
    }
    await this.writer.apply(householdId, actor, changes);
    return { changes: undo };
  }

  /**
   * Takes [amounts] (product id to amount) off the pantry in one write ("I cooked this"): the batch that expires
   * first goes first, a batch taken to 0 is used up and counted, and [extra] is written with it. One Undo puts
   * everything and [undoExtra] back. Null when there is nothing to take.
   */
  async use(
    householdId: string,
    actor: Actor,
    amounts: Map<string, number>,
    data: PantryData,
    extra: HouseholdChange[] = [],
    undoExtra: HouseholdChange[] = [],
  ): Promise<Undo | null> {
    const changes: Change[] = [];
    const undo: Change[] = [];
    for (const [productId, amount] of amounts) {
      let left = amount;
      const batches = sortedWith(
        data.batches.filter((b) => b.productId === productId && b.quantity > 0),
        (a, b) => nullsLast(compareStrings)(a.expiryDate, b.expiryDate),
        (a, b) => nullsLast((x: number, y: number) => x - y)(a.createdAt, b.createdAt),
      );
      for (const batch of batches) {
        if (left <= EPSILON) break;
        if (left >= batch.quantity - EPSILON) {
          const counted = StatsChanges.usedUp(this.today());
          changes.push({ type: 'pantry.deleteBatch', batchId: batch.id }, counted);
          undo.push({ type: 'pantry.restoreBatch', batch }, StatsChanges.undo(counted));
          left -= batch.quantity;
        } else {
          changes.push({ type: 'pantry.addToBatch', batchId: batch.id, delta: -left });
          undo.push({ type: 'pantry.addToBatch', batchId: batch.id, delta: left });
          left = 0;
        }
      }
    }
    if (changes.length === 0) return null;
    await this.writer.apply(householdId, actor, [...changes, ...extra]);
    return { changes: [...undo, ...undoExtra] };
  }

  /** Deletes the batch, logs why (DESIGN 5.2) and counts it. Undo restores the batch, the log entry and the count. */
  async throwAway(householdId: string, actor: Actor, batch: StockBatch, reason: DiscardReason): Promise<Undo> {
    return (await this.throwAwayAll(householdId, actor, [batch], reason))!;
  }

  /** Throws [batches] away in one write, each logged, counted once. One Undo puts them all back. */
  async throwAwayAll(
    householdId: string,
    actor: Actor,
    batches: StockBatch[],
    reason: DiscardReason,
  ): Promise<Undo | null> {
    if (batches.length === 0) return null;
    const changes: Change[] = [];
    const undo: Change[] = [];
    for (const batch of batches) {
      const eventId = this.writer.newId();
      changes.push({ type: 'pantry.deleteBatch', batchId: batch.id }, {
        type: 'pantry.logEvent',
        eventId,
        eventType: 'discarded',
        productId: batch.productId,
        name: batch.productName,
        quantity: batch.quantity,
        reason,
      } satisfies PantryChange);
      undo.push({ type: 'pantry.restoreBatch', batch }, { type: 'pantry.deleteEvent', eventId });
    }
    // One count for all of them: one write to this month's stats instead of one each.
    const counted = StatsChanges.thrownAway(this.today(), reason, batches.length);
    changes.push(counted);
    undo.push(StatsChanges.undo(counted));
    await this.writer.apply(householdId, actor, changes);
    return { changes: undo };
  }

  /** Typed changes. A quantity of 0 uses the batch up. */
  async edit(householdId: string, actor: Actor, batch: StockBatch, edit: BatchEdit): Promise<Undo | null> {
    if (edit.quantity <= 0) return this.usedUp(householdId, actor, batch);
    await this.write(householdId, actor, [
      { type: 'pantry.updateBatch', batchId: batch.id, edit: { ...edit, note: cleanNote(edit.note) } },
    ]);
    return null;
  }

  move(householdId: string, actor: Actor, batch: StockBatch, location: Location): Promise<Undo | null> {
    return this.edit(householdId, actor, batch, {
      quantity: batch.quantity,
      expiryDate: batch.expiryDate,
      dateType: batch.dateType,
      location,
      note: batch.note,
    });
  }

  /**
   * Product settings and rename (product detail). A rename is copied to every batch in the same write; the unit
   * cannot change while there is stock; raising Keep at least re-arms the restock engine (DESIGN 9.6).
   */
  async updateProduct(
    householdId: string,
    actor: Actor,
    product: Product,
    edit: ProductEdit,
    data: PantryData,
  ): Promise<void> {
    const name = NameKey.clean(edit.name);
    requireName(name);
    const key = NameKey.of(name);
    if (data.products.some((p) => p.id !== product.id && p.nameKey === key)) throw new AppError('conflict');
    const batches = data.batches.filter((b) => b.productId === product.id);
    const unit = batches.some((b) => b.quantity > 0) ? product.unit : edit.unit;
    const raised =
      edit.minQuantity !== null && (product.minQuantity === null || edit.minQuantity > product.minQuantity);
    const updated: Product = {
      ...product,
      name,
      nameKey: key,
      category: edit.category,
      defaultLocation: edit.defaultLocation,
      unit,
      minQuantity: edit.minQuantity,
      autoAddToGrocery: edit.minQuantity !== null && edit.autoAddToGrocery,
      buyQuantity: edit.minQuantity !== null ? edit.buyQuantity : null,
      restockArmed: product.restockArmed || raised,
      typicalShelfLifeDays: edit.typicalShelfLifeDays,
    };
    const changes: PantryChange[] = [{ type: 'pantry.updateProduct', product: updated }];
    if (updated.name !== product.name || updated.unit !== product.unit || updated.category !== product.category) {
      for (const b of batches) {
        changes.push({
          type: 'pantry.syncBatchProduct',
          batchId: b.id,
          name: updated.name,
          unit: updated.unit,
          category: updated.category,
        });
      }
    }
    await this.writer.apply(householdId, actor, changes);
  }

  /** The Edit window in one write; a quantity of 0 uses the purchase up instead. */
  async editItem(
    householdId: string,
    actor: Actor,
    batch: StockBatch,
    edit: ItemEdit,
    data: PantryData,
  ): Promise<Undo | null> {
    if (edit.quantity <= 0) return this.usedUp(householdId, actor, batch);
    await this.writer.apply(householdId, actor, this.planEdit(batch, edit, data));
    return null;
  }

  /** The unit can change only when this is the product's only purchase (no unit conversion, DESIGN 2). */
  planEdit(batch: StockBatch, edit: ItemEdit, data: PantryData): HouseholdChange[] {
    const name = NameKey.clean(edit.name);
    requireName(name);
    const key = NameKey.of(name);
    const product = data.products.find((p) => p.id === batch.productId) ?? null;
    if (data.products.some((p) => p.id !== batch.productId && p.nameKey === key)) throw new AppError('nameTaken');
    const others = data.batches.filter((b) => b.productId === batch.productId && b.id !== batch.id && b.quantity > 0);
    const unit = others.length === 0 ? edit.unit : (product?.unit ?? batch.unit);
    const min = edit.autoAddToGrocery ? edit.minQuantity : null;
    const changes: PantryChange[] = [
      {
        type: 'pantry.updateBatch',
        batchId: batch.id,
        edit: {
          quantity: edit.quantity,
          expiryDate: edit.expiryDate,
          dateType: batch.dateType,
          location: edit.location,
          note: batch.note,
        },
      },
    ];
    if (product) {
      const raised = min !== null && (product.minQuantity === null || min > product.minQuantity);
      const updated: Product = {
        ...product,
        name,
        nameKey: key,
        category: edit.category,
        unit,
        minQuantity: min,
        autoAddToGrocery: min !== null,
        buyQuantity: min !== null ? edit.buyQuantity : null,
        restockArmed: product.restockArmed || raised,
      };
      if (JSON.stringify(updated) !== JSON.stringify(product))
        changes.push({ type: 'pantry.updateProduct', product: updated });
    }
    // Every purchase carries a copy of the product's name, unit and category (invariant 2).
    if (name !== batch.productName || unit !== batch.unit || edit.category !== batch.category) {
      for (const b of [...others, batch])
        changes.push({ type: 'pantry.syncBatchProduct', batchId: b.id, name, unit, category: edit.category });
    }
    return changes;
  }

  undo(householdId: string, actor: Actor, undo: Undo): Promise<void> {
    return this.writer.apply(householdId, actor, undo.changes);
  }

  /** Batches at 0 or below are hidden and deleted by whichever client notices first (DESIGN 8.5, invariant 1). */
  async deleteEmptyBatches(householdId: string, actor: Actor, data: PantryData): Promise<void> {
    const empty = data.batches.filter((b) => b.quantity <= 0 && !b.pendingSync);
    if (empty.length > 0) {
      await this.writer.apply(
        householdId,
        actor,
        empty.map((b): PantryChange => ({ type: 'pantry.deleteBatch', batchId: b.id })),
      );
    }
  }
}

/** The sheet only sets Keep at least; leaving it empty keeps what the product already has. */
function withKeepAtLeast(
  product: Product,
  min: number | null,
  autoAdd: boolean,
  buy: number | null,
  before: Product,
): Product {
  if (min === null) return product;
  const raised = before.minQuantity === null || min > before.minQuantity;
  return {
    ...product,
    minQuantity: min,
    autoAddToGrocery: autoAdd,
    buyQuantity: buy,
    restockArmed: product.restockArmed || raised,
  };
}
