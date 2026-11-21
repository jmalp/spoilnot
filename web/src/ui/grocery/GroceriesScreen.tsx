import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { map, of } from 'rxjs';
import { useSessionDraft } from '../../core/drafts';
import { messageId } from '../../core/errors';
import { useHeldWhileScrolling } from '../../core/holdWhileScrolling';
import { useKeepUnchanged } from '../../core/keepUnchanged';
import { useLoadRetry, useObservable, useToday } from '../../core/hooks';
import { retryLoading } from '../../domain/load';
import { plural, str } from '../../core/i18n';
import { useRemoteChanges } from '../../core/remoteChanges';
import { useHouseholdSession, usePreferences, useServices } from '../../core/services';
import { ShoppingTrip, StoreOrder } from '../../domain/grocery/aisles';
import { GroceryRules, type GroceryItem, type GroceryList } from '../../domain/grocery/models';
import { RestockEngine } from '../../domain/grocery/restock';
import { canIncrease, GroceryLayout, type QuickAdd, type QuickAddResult } from '../../domain/grocery/service';
import type { PantryData } from '../../domain/pantry/models';
import type { Undo } from '../../domain/write';
import {
  Button,
  Dialog,
  Fab,
  Icon,
  Menu,
  MenuDivider,
  MenuItem,
  PillField,
  Progress,
  TextField,
  useSnackbars,
} from '../kit';
import { amountText, categoryLabel, unitLabel } from '../pantry/labels';
import { EmptyState, LoadError, OfflineBanner, TabScaffold } from '../Scaffold';
import { AddToPantryDrafts, type AddToPantryDraft } from './addToPantryDrafts';
import { AddToPantrySheet, EditGroceryItemSheet } from './GroceryForms';
import { GroceryRow } from './GroceryRow';
import '../pantry/pantry.css';
import './grocery.css';

const EMPTY_PANTRY: PantryData = { products: [], batches: [], fromCache: true };
const NO_ITEMS: GroceryItem[] = [];
/** An item's key, for keeping unchanged items across snapshots (review WEB-13). */
const itemId = (item: GroceryItem) => item.id;
/** Someone else's item is announced when it was added this recently. */
const ANNOUNCE_WITHIN_MS = 2 * 60_000;

type ListDialog = 'create' | 'rename' | 'delete';

/** The Groceries tab (DESIGN 5.3). */
export function GroceriesScreen({
  onOpenSettings,
  joinRequests,
  listId,
  onSelectList,
}: {
  onOpenSettings: () => void;
  joinRequests: number;
  /** The chosen list, kept by the shell as the app's view model keeps it; null for the default list. */
  listId: string | null;
  onSelectList: (id: string | null) => void;
}) {
  const me = useHouseholdSession();
  const { grocery, groceryService, pantry, households, clock, preferences } = useServices();
  const snackbars = useSnackbars();
  const today = useToday(clock);
  const hid = me.householdId;
  const actor = useMemo(() => ({ uid: me.user.uid, name: me.displayName }), [me]);

  // A failed listener keeps what it had and listens again, saying "Couldn't load. Retry" meanwhile (DESIGN 6.7): an
  // empty answer here left the tab loading when the quota ran out (after v1, WEB-04).
  const { load, failed: loadFailed } = useLoadRetry();
  const lists = useObservable<GroceryList[] | null>(
    useMemo(() => grocery.observeLists(hid).pipe(retryLoading(load)), [grocery, hid, load]),
    null,
  );
  const list = lists?.find((l) => l.id === listId) ?? lists?.find((l) => l.isDefault) ?? lists?.[0] ?? null;
  // null while loading; the list's items while it is visible (DESIGN 9.1), with the list they belong to: right after
  // a switch the old list's items still show until the new list's arrive.
  const shownListId = list?.id ?? null;
  const loaded = useObservable<{ listId: string; items: GroceryItem[] } | null>(
    useMemo(
      () =>
        shownListId
          ? grocery.observeItems(hid, shownListId).pipe(
              map((items) => ({ listId: shownListId, items })),
              retryLoading(load),
            )
          : of(null),
      [grocery, hid, shownListId, load],
    ),
    null,
  );
  const items = loaded?.items ?? null;
  const itemsListId = loaded?.listId ?? null;
  const pantryData = useObservable<PantryData>(
    useMemo(() => pantry.observe(hid).pipe(retryLoading(load, EMPTY_PANTRY)), [pantry, hid, load]),
    EMPTY_PANTRY,
  );
  const members = useObservable<Map<string, string>>(
    useMemo(
      () =>
        households.observeMembers(hid).pipe(
          map((all) => new Map(all.map((m) => [m.uid, m.displayName]))),
          retryLoading(undefined, new Map<string, string>()),
        ),
      [households, hid],
    ),
    new Map(),
  );

  const [text, setText] = useState('');
  const [cartOpen, setCartOpen] = useState(true);
  // The open Add to pantry sheet's draft, kept through a reload (after v1, REL-08); null while it is closed.
  const [shopping, setShopping] = useSessionDraft<AddToPantryDraft | null>(
    `addToPantry.${hid}`,
    AddToPantryDrafts.parse,
    null,
  );
  // A sheet restored after a reload brings back its list, which the shell does not keep; a deleted list closes it.
  const restoring = useRef(shopping !== null);
  useEffect(() => {
    if (!restoring.current || lists === null) return;
    restoring.current = false;
    if (shopping === null || shopping.listId === list?.id) return;
    if (lists.some((l) => l.id === shopping.listId)) onSelectList(shopping.listId);
    else setShopping(null);
  }, [lists, list, shopping, onSelectList, setShopping]);
  const [editing, setEditing] = useState<GroceryItem | null>(null);
  const [dialog, setDialog] = useState<ListDialog | null>(null);
  const [duplicate, setDuplicate] = useState<{
    result: Extract<QuickAddResult, { kind: 'alreadyOnList' }>;
    text: string;
  } | null>(null);
  // This phone's shopping trip, for learning the store's aisle order.
  const [trip] = useState(() => new ShoppingTrip(() => clock.now()));

  // Items keep their object from snapshot to snapshot while their data is the same, so memoized rows skip (WEB-13).
  const shown = useKeepUnchanged(items ?? NO_ITEMS, itemId);
  const aisleOrder = list?.aisleOrder;
  const view = useMemo(() => {
    const layout = GroceryLayout.of(shown);
    return { ...layout, aisles: StoreOrder.sort(layout.aisles, aisleOrder ?? []) };
  }, [shown, aisleOrder]);
  // Why the aisles move, once per phone (after v1, UX-08): once this list has learned an order and has two aisles.
  const aisleHint = !usePreferences().aisleHintDone && (aisleOrder ?? []).length > 0 && view.aisles.length >= 2;
  // Rows another phone changed get a short highlight (DESIGN 6.4), told one list at a time: another list's rows are
  // not new (after v1, 2026-11-18; switching lists highlighted every row of both).
  const remote = useRemoteChanges(
    shown,
    (i) => i.id,
    (i) => i.pendingSync,
    (i) => ({ ...i, pendingSync: false }),
    itemsListId,
  );

  // "Sam added Eggs" when someone else adds to the list being viewed (DESIGN 9.2). Only items added in the last
  // minutes: a phone whose cache starts empty would otherwise announce every item already on the list.
  const known = useRef<{ listId: string; ids: Set<string> } | null>(null);
  useEffect(() => {
    if (items === null || itemsListId === null) return;
    const before = known.current;
    if (before && before.listId === itemsListId) {
      const recent = clock.now() - ANNOUNCE_WITHIN_MS;
      for (const item of items) {
        if (before.ids.has(item.id) || !item.createdBy || item.createdBy === me.user.uid) continue;
        if (item.createdAt !== null && item.createdAt < recent) continue;
        const who = members.get(item.createdBy);
        if (who) snackbars.show(str('grocery_someone_added', who, item.name));
      }
    }
    known.current = { listId: itemsListId, ids: new Set(items.map((i) => i.id)) };
  }, [items, itemsListId, members, me.user.uid, snackbars, clock]);

  const failed = (e: unknown) => snackbars.show(str(messageId(e)));
  const act = (work: () => Promise<unknown>) => void work().catch(failed);
  // What the rows' handlers need, read when tapped, so the handlers keep their identity across snapshots (WEB-13).
  const latest = useRef({ list, actor, hid, snackbars, groceryService, trip });
  useEffect(() => {
    latest.current = { list, actor, hid, snackbars, groceryService, trip };
  });
  /** A destructive action: its Undo is offered with [message]. */
  const actUndo = (work: () => Promise<Undo | null>, message: string) =>
    void work().then((undo) => {
      if (undo) snackbars.showUndo(message, () => void groceryService.undo(hid, actor, undo));
    }, failed);

  const quickAdd = (value: string, addAnyway = false) => {
    if (!list || value.trim() === '') return;
    groceryService
      .quickAdd(hid, actor, list.id, value, shown, pantryData, { addAnyway, listName: list.name })
      .then((result) => {
        if (result.kind === 'added') setText('');
        else setDuplicate({ result, text: value });
      }, failed);
  };

  const toggle = useCallback((item: GroceryItem) => {
    const { list, actor, hid, snackbars, groceryService, trip } = latest.current;
    if (!list) return;
    void (async () => {
      await groceryService.setChecked(hid, actor, list.id, item, !item.checked);
      // Learn the store's aisle order from the order things go into the cart (DESIGN 5.3).
      if (!item.checked) {
        const aisle = item.category ?? 'other';
        const learned = StoreOrder.learn(list.aisleOrder, trip.checked(list.id, aisle), aisle);
        if (learned) await groceryService.setAisleOrder(hid, actor, list, learned);
      }
    })().catch((e: unknown) => snackbars.show(str(messageId(e))));
  }, []);
  const remove = useCallback((item: GroceryItem) => {
    const { list, actor, hid, snackbars, groceryService } = latest.current;
    if (!list) return;
    groceryService.delete(hid, actor, list.id, item).then(
      (undo) => {
        if (undo) {
          snackbars.showUndo(str('grocery_deleted', item.name), () => void groceryService.undo(hid, actor, undo));
        }
      },
      (e: unknown) => snackbars.show(str(messageId(e))),
    );
  }, []);

  const addedBy = (item: GroceryItem) =>
    item.createdBy && item.createdBy !== me.user.uid && item.source === 'manual'
      ? (members.get(item.createdBy) ?? null)
      : null;
  // Each row gets only its own facts and the same handlers every time, so it redraws only when they change (WEB-13).
  const rowProps = (item: GroceryItem) => ({
    item,
    backInStock: RestockEngine.isBackInStock(item, pantryData),
    addedBy: addedBy(item),
    highlight: remote.has(item.id),
    onToggle: toggle,
    onDelete: remove,
    onEdit: setEditing,
  });
  // Worked out when what is typed or the list changes, not on every render (review PERF-04's web half).
  const suggestions = useMemo(() => GroceryLayout.suggestions(text, shown, pantryData), [text, shown, pantryData]);

  const checkedCount = view.inCart.length;
  return (
    <TabScaffold
      title={<ListMenu lists={lists ?? []} current={list} onSelect={(l) => onSelectList(l.id)} onDialog={setDialog} />}
      // What is left to buy (DESIGN 19.7).
      eyebrow={
        items !== null
          ? plural('grocery_eyebrow_to_buy', shown.length - checkedCount, shown.length - checkedCount)
          : undefined
      }
      onOpenSettings={onOpenSettings}
      joinRequests={joinRequests}
      innerScroll
      testId="screen_groceries"
      fab={
        checkedCount > 0 ? (
          <Fab
            icon="round/inventory_2"
            label={plural('grocery_add_to_pantry', checkedCount, checkedCount)}
            data-testid="fab_add_to_pantry"
            onClick={() => list && setShopping(AddToPantryDrafts.open(list.id))}
          />
        ) : undefined
      }
    >
      {loadFailed ? (
        <LoadError onRetry={() => load.retry()} />
      ) : (
        pantryData.fromCache && items !== null && <OfflineBanner />
      )}
      <QuickAddField text={text} onText={setText} suggestions={suggestions} onAdd={(value) => quickAdd(value)} />
      {items === null ? (
        <div className="sn-centered">{!loadFailed && <Progress />}</div>
      ) : items.length === 0 ? (
        <EmptyState icon="outlined/shopping_cart" message={str('grocery_empty')} />
      ) : (
        <GroceryListView
          view={view}
          cartOpen={cartOpen}
          onToggleCart={() => setCartOpen(!cartOpen)}
          onClear={() =>
            list && actUndo(() => groceryService.clearChecked(hid, actor, list.id, shown), str('grocery_cleared'))
          }
          row={rowProps}
          aisleHint={aisleHint}
          onAisleHintDone={() => preferences.set({ aisleHintDone: true })}
        />
      )}
      {duplicate && (
        <Dialog
          title={str('grocery_already_title')}
          titleClassName="t-dialog-title"
          onDismiss={() => setDuplicate(null)}
          actions={
            <>
              <Button variant="text" onClick={() => setDuplicate(null)}>
                {str('action_cancel')}
              </Button>
              <Button
                variant="text"
                onClick={() => {
                  const typed = duplicate.text;
                  setDuplicate(null);
                  quickAdd(typed, true);
                }}
              >
                {str('grocery_add_anyway')}
              </Button>
              {canIncrease(duplicate.result.request, duplicate.result.existing) && (
                <Button
                  variant="text"
                  data-testid="button_increase"
                  onClick={() => {
                    const { existing, request } = duplicate.result;
                    setDuplicate(null);
                    if (list)
                      act(async () => {
                        await groceryService.increase(hid, actor, list.id, existing, request);
                        setText('');
                      });
                  }}
                >
                  {str('grocery_increase')}
                </Button>
              )}
            </>
          }
        >
          <p className="t-body-medium c-on-surface-variant">
            {alreadyBody(duplicate.result.request, duplicate.result.existing)}
          </p>
        </Dialog>
      )}
      {shopping && list && shopping.listId === list.id && items !== null && (
        <AddToPantrySheet
          items={view.inCart}
          pantry={pantryData}
          today={today}
          imperialFirst={me.units === 'imperial'}
          draft={shopping}
          onDraft={(change) => setShopping((d) => (d === null ? d : change(d)))}
          onDismiss={() => setShopping(null)}
          onConfirm={(entries, skipped) =>
            // Undo removes what was added and puts the items back (after v1, LOGIC-10).
            actUndo(
              async () => {
                const undo = await groceryService.addToPantry(hid, actor, list.id, entries, skipped, pantryData);
                setShopping(null);
                return undo;
              },
              str(entries.length > 0 ? 'grocery_added_to_pantry' : 'grocery_skipped_removed'),
            )
          }
        />
      )}
      {editing && list && (
        <EditGroceryItemSheet
          item={editing}
          reason={RestockEngine.reason(editing, pantryData)}
          imperialFirst={me.units === 'imperial'}
          onDismiss={() => setEditing(null)}
          onSave={(edit) => {
            const item = editing;
            setEditing(null);
            act(() => groceryService.edit(hid, actor, list.id, item, edit, pantryData.products));
          }}
        />
      )}
      {dialog && (
        <ListDialogs
          dialog={dialog}
          currentName={list?.name ?? ''}
          onDismiss={() => setDialog(null)}
          onCreate={(name) =>
            act(async () => {
              onSelectList(await groceryService.createList(hid, actor, name));
            })
          }
          onRename={(name) => list && act(() => groceryService.renameList(hid, actor, list, name))}
          onDelete={() =>
            list &&
            act(async () => {
              await groceryService.deleteList(hid, actor, list);
              onSelectList(null);
            })
          }
        />
      )}
    </TabScaffold>
  );
}

/**
 * Already on the list: "Add 2 more to the 6 on the list…" (after v1, UX-18); amounts in another unit aren't added
 * together (no conversion, DESIGN 2; LOGIC-08). An item without an amount counts as 1.
 */
function alreadyBody(request: QuickAdd, existing: GroceryItem): string {
  if (canIncrease(request, existing) || existing.unit === null) {
    return str(
      'grocery_already_more',
      amountText(request.quantity ?? 1, request.unit ?? existing.unit),
      amountText(existing.quantity ?? 1, existing.unit),
    );
  }
  const amount = existing.quantity === null ? unitLabel(existing.unit) : amountText(existing.quantity, existing.unit);
  return str('grocery_already_other_unit', existing.name, amount);
}

function GroceryListView({
  view,
  cartOpen,
  onToggleCart,
  onClear,
  row,
  aisleHint,
  onAisleHintDone,
}: {
  view: ReturnType<typeof GroceryLayout.of>;
  cartOpen: boolean;
  onToggleCart: () => void;
  onClear: () => void;
  row: (item: GroceryItem) => Parameters<typeof GroceryRow>[0];
  aisleHint: boolean;
  onAisleHintDone: () => void;
}) {
  // Another phone's change waits while the list moves under the finger (DESIGN 6.4, review WEB-11).
  const [scroller, setScroller] = useState<HTMLDivElement | null>(null);
  const shown = useHeldWhileScrolling(view, scroller);
  const inCart = shown.inCart;
  return (
    <div className="sn-pantry-list sn-pantry-list--look" data-testid="grocery_list" ref={setScroller}>
      {shown.aisles.map(([category, rows], index) => {
        const label = categoryLabel(category);
        return (
          <section key={category} className="sn-pantry-block" aria-label={label}>
            {/* Pinned while its rows scroll; VoiceOver's rotor jumps from aisle to aisle (A11Y-09). */}
            <h2 className="sn-pantry-heading sn-aisle-header t-group-heading">{label}</h2>
            {/* Under the first aisle's heading, once: why the aisles move (after v1, UX-08). */}
            {index === 0 && aisleHint && <AisleHint onDone={onAisleHintDone} />}
            <div className="sn-group">
              {rows.map((item) => (
                <GroceryRow key={item.id} {...row(item)} />
              ))}
            </div>
          </section>
        );
      })}
      {inCart.length > 0 && (
        <section className="sn-pantry-block" aria-label={plural('grocery_in_cart', inCart.length, inCart.length)}>
          <div className="sn-cart-header" data-testid="cart_header">
            {/* A heading, so VoiceOver's rotor jumps to it like the aisles, with expanded or collapsed (A11Y-09). */}
            <h2 className="sn-cart-header__heading">
              <button
                type="button"
                className="sn-cart-header__toggle m3-state"
                aria-expanded={cartOpen}
                onClick={onToggleCart}
              >
                <span className="t-group-heading sn-cart-header__label">
                  {plural('grocery_in_cart', inCart.length, inCart.length)}
                </span>
                <Icon name={cartOpen ? 'round/expand_less' : 'round/expand_more'} />
              </button>
            </h2>
            <Button variant="text" onClick={onClear} data-testid="button_clear_checked">
              {str('grocery_clear_checked')}
            </Button>
          </div>
          {cartOpen && (
            <div className="sn-group">
              {inCart.map((item) => (
                <GroceryRow key={item.id} {...row(item)} />
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  );
}

/** "Aisles follow the order you shop in…" with Got it, shown once per phone (after v1, UX-08). */
function AisleHint({ onDone }: { onDone: () => void }) {
  return (
    <div className="sn-aisle-hint" data-testid="grocery_aisle_hint">
      <Icon name="round/swap_vert" size={20} />
      <span className="t-body-medium sn-aisle-hint__text">{str('grocery_aisle_hint')}</span>
      <Button variant="text" onClick={onDone} data-testid="grocery_aisle_hint_ok">
        {str('grocery_aisle_hint_ok')}
      </Button>
    </div>
  );
}

/**
 * The quick-add field stays at the top (DESIGN 5.3, 19.10): the page's round field with a filled + at its end;
 * typing suggests pantry products and names already used.
 */
function QuickAddField({
  text,
  onText,
  suggestions,
  onAdd,
}: {
  text: string;
  onText: (t: string) => void;
  suggestions: string[];
  onAdd: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const add = (value: string) => {
    setOpen(false);
    onAdd(value);
  };
  return (
    <div className="sn-quick-add sn-anchor">
      <PillField
        placeholder={str('grocery_quick_add')}
        value={text}
        onValueChange={(v) => {
          onText(v);
          setOpen(true);
        }}
        autoCapitalize="sentences"
        autoComplete="off"
        enterKeyHint="done"
        maxLength={GroceryRules.MAX_NAME_LENGTH + 12}
        data-testid="grocery_quick_add"
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            add(text);
          }
        }}
        trailing={
          <button
            type="button"
            className="sn-pill-add m3-state"
            aria-label={str('grocery_add_to_list')}
            title={str('grocery_add_to_list')}
            disabled={text.trim() === ''}
            onClick={() => add(text)}
          >
            <Icon name="round/add" />
          </button>
        }
      />
      <Menu open={open && suggestions.length > 0} onDismiss={() => setOpen(false)} align="start" focusFirst={false}>
        {suggestions.map((name) => (
          <MenuItem key={name} label={name} onClick={() => add(name)} />
        ))}
      </Menu>
    </div>
  );
}

/** The list's name is the switcher (after v1), with list management (DESIGN 5.3: add, rename and delete lists). */
function ListMenu({
  lists,
  current,
  onSelect,
  onDialog,
}: {
  lists: GroceryList[];
  current: GroceryList | null;
  onSelect: (list: GroceryList) => void;
  onDialog: (dialog: ListDialog) => void;
}) {
  const [open, setOpen] = useState(false);
  const name = current?.name ?? str('tab_groceries');
  const pick = (action: () => void) => () => {
    setOpen(false);
    action();
  };
  return (
    <span className="sn-anchor sn-list-switcher-anchor">
      <button
        type="button"
        className="sn-list-switcher m3-state"
        aria-label={str('grocery_list_switcher', name)}
        aria-haspopup="menu"
        aria-expanded={open}
        data-testid="grocery_list_switcher"
        onClick={() => setOpen(true)}
      >
        <span className="sn-list-switcher__name">{name}</span>
        <Icon name="round/arrow_drop_down" />
      </button>
      <Menu open={open} onDismiss={() => setOpen(false)} align="start">
        {lists.map((list) => (
          <MenuItem
            key={list.id}
            label={list.name}
            leading={list.id === current?.id ? <Icon name="round/check" /> : <span className="sn-menu-spacer" />}
            onClick={pick(() => onSelect(list))}
          />
        ))}
        <MenuDivider />
        <MenuItem label={str('grocery_new_list')} onClick={pick(() => onDialog('create'))} />
        <MenuItem label={str('grocery_rename_list')} onClick={pick(() => onDialog('rename'))} />
        {current && !current.isDefault && (
          <MenuItem label={str('grocery_delete_list')} onClick={pick(() => onDialog('delete'))} />
        )}
      </Menu>
    </span>
  );
}

function ListDialogs({
  dialog,
  currentName,
  onDismiss,
  onCreate,
  onRename,
  onDelete,
}: {
  dialog: ListDialog;
  currentName: string;
  onDismiss: () => void;
  onCreate: (name: string) => void;
  onRename: (name: string) => void;
  onDelete: () => void;
}) {
  const [name, setName] = useState(dialog === 'rename' ? currentName : '');
  if (dialog === 'delete') {
    return (
      <Dialog
        title={str('grocery_delete_list_title', currentName)}
        titleClassName="t-dialog-title"
        onDismiss={onDismiss}
        actions={
          <>
            <Button variant="text" onClick={onDismiss}>
              {str('action_cancel')}
            </Button>
            <Button
              variant="text"
              data-testid="confirm"
              onClick={() => {
                onDelete();
                onDismiss();
              }}
            >
              {str('action_delete')}
            </Button>
          </>
        }
      >
        <p className="t-body-medium c-on-surface-variant">{str('grocery_delete_list_body')}</p>
      </Dialog>
    );
  }
  const valid = name.trim() !== '' && name.trim().length <= GroceryRules.MAX_LIST_NAME_LENGTH;
  const save = () => {
    if (!valid) return;
    if (dialog === 'create') onCreate(name);
    else onRename(name);
    onDismiss();
  };
  return (
    <Dialog
      title={str(dialog === 'create' ? 'grocery_new_list' : 'grocery_rename_list')}
      titleClassName="t-dialog-title"
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button variant="text" disabled={!valid} onClick={save} data-testid="button_save_list">
            {str('action_save')}
          </Button>
        </>
      }
    >
      <TextField
        label={str('grocery_list_name')}
        value={name}
        onValueChange={setName}
        autoCapitalize="sentences"
        enterKeyHint="done"
        onKeyDown={(e) => {
          if (e.key === 'Enter') save();
        }}
        data-testid="field_list_name"
      />
    </Dialog>
  );
}
