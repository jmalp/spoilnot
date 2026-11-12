// The restock engine (DESIGN 9.6), ported from RestockEngine and RestockRunner.
import {
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  EMPTY,
  from,
  map,
  retry,
  switchMap,
  timer,
  type Observable,
  type Subscription,
} from 'rxjs';
import type { PantryChange, PantryData, Product } from '../pantry/models';
import type { PantryRepository } from '../pantry/repository';
import type { SessionManager } from '../session';
import type { Clock } from '../time';
import type { Actor, HouseholdChange, HouseholdWriter } from '../write';
import { GroceryRules, type GroceryChange, type GroceryItem, type GroceryRepository } from './models';

/**
 * A pure function from the current products and stock to the writes to make. It runs on every phone; the plan is
 * idempotent, so two phones evaluating at once write identical data. The phone whose own change caused it writes at
 * once, the others only after OTHER_PHONE_DELAY_MS (delayMillis).
 * - Below Keep at least while armed: add `low_{productId}` to the default list once and disarm.
 * - Back at Keep at least or more while disarmed: re-arm.
 * Deleting the item therefore does not bring it back until stock recovers and drops again.
 */
export const RestockEngine = {
  plan(
    data: PantryData,
    defaultListId: string | null,
    cachedLowStockIds: Set<string>,
  ): (GroceryChange | PantryChange)[] {
    const changes: (GroceryChange | PantryChange)[] = [];
    for (const product of data.products) {
      const min = product.minQuantity;
      if (min === null || !product.autoAddToGrocery) continue;
      const total = RestockEngine.totalOf(product, data);
      if (total >= min) {
        if (!product.restockArmed) changes.push({ type: 'pantry.setRestockArmed', productId: product.id, armed: true });
      } else if (product.restockArmed && defaultListId !== null) {
        changes.push({
          type: 'grocery.upsertLowStock',
          listId: defaultListId,
          productId: product.id,
          name: product.name,
          nameKey: product.nameKey,
          unit: product.unit,
          category: product.category,
          quantity: RestockEngine.buyQuantity(product, total),
          writeCreated: !cachedLowStockIds.has(GroceryRules.lowStockItemId(product.id)),
        });
        changes.push({ type: 'pantry.setRestockArmed', productId: product.id, armed: false });
      }
    }
    return changes;
  },

  /** How long a phone that did not make the change waits before writing, so the phone that did writes first. */
  OTHER_PHONE_DELAY_MS: 5_000,

  /** A change made on this phone this recently counts as the cause of what the engine sees now. */
  OWN_CHANGE_WINDOW_MS: 5_000,

  /**
   * Whether a plan applied on this phone can change what the engine decides: stock or a product's settings. The
   * engine's own writes (the item and the armed flag) and history do not count.
   */
  movesStock: (changes: readonly HouseholdChange[]): boolean => changes.some((c) => STOCK_CHANGES.has(c.type)),

  /**
   * How long to wait before writing a plan: nothing when this phone changed stock or products within
   * OWN_CHANGE_WINDOW_MS (its own change caused it), otherwise OTHER_PHONE_DELAY_MS. A newer snapshot during the
   * wait starts over, so if the other phone's write arrives first, nothing is left to write (DESIGN 9.6).
   */
  delayMillis: (lastOwnChangeAt: number | null, now: number): number =>
    lastOwnChangeAt !== null &&
    now - lastOwnChangeAt >= 0 &&
    now - lastOwnChangeAt <= RestockEngine.OWN_CHANGE_WINDOW_MS
      ? 0
      : RestockEngine.OTHER_PHONE_DELAY_MS,

  /** Buy quantity, or Keep at least minus what is left, at least 1 (DESIGN 5.3). */
  buyQuantity: (product: Product, total: number): number =>
    product.buyQuantity ?? Math.max(1, (product.minQuantity ?? 0) - total),

  /** Total stock; batches at 0 or below do not count (DESIGN 8.5, invariant 1). */
  totalOf: (product: Product, data: PantryData): number =>
    data.batches.filter((b) => b.productId === product.id && b.quantity > 0).reduce((sum, b) => sum + b.quantity, 0),

  /** A low-stock item whose product is back at Keep at least shows "Back in stock" with a one-tap remove. */
  isBackInStock(item: GroceryItem, data: PantryData): boolean {
    if (item.source !== 'low_stock' || item.checked) return false;
    const product = data.products.find((p) => p.id === item.productId);
    if (!product || product.minQuantity === null) return false;
    return RestockEngine.totalOf(product, data) >= product.minQuantity;
  },
};

/** The changes that move stock or a product's settings (RestockEngine.movesStock). */
const STOCK_CHANGES = new Set<string>([
  'pantry.createProduct',
  'pantry.updateProduct',
  'pantry.deleteProduct',
  'pantry.createBatch',
  'pantry.addToBatch',
  'pantry.updateBatch',
  'pantry.deleteBatch',
  'pantry.restoreBatch',
] satisfies PantryChange['type'][]);

/** How long a plan the server rejected is not sent again, so a rollback does not loop. */
const REPEAT_GUARD_MS = 30_000;
const DEBOUNCE_MS = 300;

/** 1, 2, 4 … 32 seconds before listening again ([attempt] counts from 1), as the app's runner does. */
export const resubscribeDelay = (attempt: number): number => 1_000 * 2 ** Math.min(Math.max(attempt - 1, 0), 5);

type Input = { hid: string; actor: Actor; data: PantryData; listId: string | null };

/**
 * Runs the RestockEngine while the user is in a household: on every change of the products and stock, debounced by
 * 300 ms, once both have been read from the server (PantryData.serverRead: a full read of each on record on this
 * phone, so a warm cache works offline). A change made on this phone is acted on at once; one from another phone after OTHER_PHONE_DELAY_MS, so
 * usually only one phone writes. A failed listener listens again with a backoff.
 *
 * After v1 it reads the local cache only (PantryRepository.observeCached, GroceryRepository.observeCachedLists): it
 * reacts to what the screens' server listeners and this phone's own writes put there, and holds no server listener
 * of its own, so nothing is read again when the app comes back after a long time (EFF-01). The signed-in shell starts
 * it and stops it ([start], [stop]), not the page load.
 */
export class RestockRunner {
  private subscription: Subscription | null = null;
  private lastPlan = '';
  private lastPlanAt = 0;
  private lastOwnChangeAt: number | null = null;

  constructor(
    private readonly session: SessionManager,
    private readonly pantry: PantryRepository,
    private readonly grocery: GroceryRepository,
    private readonly writer: HouseholdWriter,
    private readonly clock: Clock,
  ) {}

  /** Starts the engine once; later calls do nothing until [stop]. */
  start(): void {
    if (this.subscription) return;
    this.subscription = this.session.session$
      .pipe(
        map((s) =>
          s.kind === 'inHousehold'
            ? { hid: s.householdId, actor: { uid: s.user.uid, name: s.displayName } as Actor }
            : null,
        ),
        distinctUntilChanged((a, b) => a?.hid === b?.hid && a?.actor.uid === b?.actor.uid),
        switchMap((context) => {
          if (!context) return EMPTY;
          const defaultList = this.grocery.observeCachedLists(context.hid).pipe(
            map((lists) => lists.find((l) => l.isDefault)?.id ?? null),
            distinctUntilChanged(),
          );
          return combineLatest([this.pantry.observeCached(context.hid), defaultList]).pipe(
            map(([data, listId]): Input => ({ ...context, data, listId })),
            // A failed cache listener (a cache that can't be read) listens again later instead of stopping the
            // engine for the session.
            retry({ delay: (_error, attempt) => timer(resubscribeDelay(attempt)) }),
          );
        }),
        debounceTime(DEBOUNCE_MS),
        // A newer snapshot during the wait (another phone's write) cancels the wait and evaluates again.
        switchMap((input) => this.evaluate(input)),
      )
      .subscribe();
    const own = this.writer.localPlans$?.subscribe((plan) => {
      if (RestockEngine.movesStock(plan)) this.lastOwnChangeAt = this.clock.now();
    });
    if (own) this.subscription.add(own);
  }

  stop(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
  }

  private evaluate(input: Input): Observable<void> {
    // A first sync can deliver the products before the stock: every staple would look empty (DESIGN 9.6).
    if (input.data.serverRead !== true) return EMPTY;
    const draft = RestockEngine.plan(input.data, input.listId, new Set());
    if (draft.length === 0) return EMPTY;
    const wait = RestockEngine.delayMillis(this.lastOwnChangeAt, this.clock.now());
    return timer(wait).pipe(switchMap(() => from(this.write(input))));
  }

  private async write({ hid, actor, data, listId }: Input): Promise<void> {
    try {
      // createdAt and createdBy are written only for items not already in the local cache (DESIGN 9.6).
      const cached = new Set<string>();
      for (const change of RestockEngine.plan(data, listId, new Set())) {
        if (change.type !== 'grocery.upsertLowStock' || listId === null) continue;
        const id = GroceryRules.lowStockItemId(change.productId);
        if (await this.grocery.isCached(hid, listId, id)) cached.add(id);
      }
      const plan = RestockEngine.plan(data, listId, cached);
      const key = JSON.stringify(plan);
      const now = this.clock.now();
      if (key === this.lastPlan && now - this.lastPlanAt < REPEAT_GUARD_MS) return;
      this.lastPlan = key;
      this.lastPlanAt = now;
      await this.writer.apply(hid, actor, plan);
    } catch {
      // The next snapshot triggers another evaluation.
    }
  }
}
