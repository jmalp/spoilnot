import { useEffect, useMemo, useRef, useState } from 'react';
import { map, of } from 'rxjs';
import { useSessionDraft } from '../../core/drafts';
import { messageId } from '../../core/errors';
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
  IconButton,
  Menu,
  MenuDivider,
  MenuItem,
  Progress,
  TextField,
  useSnackbars,
} from '../kit';
import { amountText, categoryLabel, unitLabel } from '../pantry/labels';
import { EmptyState, LoadError, OfflineBanner, TabScaffold, useOnInnerScroll } from '../Scaffold';
import { AddToPantryDrafts, type AddToPantryDraft } from './addToPantryDrafts';
import { AddToPantrySheet, EditGroceryItemSheet } from './GroceryForms';
import { GroceryRow } from './GroceryRow';
import '../pantry/pantry.css';
import './grocery.css';

const EMPTY_PANTRY: PantryData = { products: [], batches: [], fromCache: true };
const NO_ITEMS: GroceryItem[] = [];
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
  // null while loading; the list's items while it is visible (DESIGN 9.1).
  const shownListId = list?.id ?? null;
  const items = useObservable<GroceryItem[] | null>(
    useMemo(
      () => (shownListId ? grocery.observeItems(hid, shownListId).pipe(retryLoading(load)) : of(null)),
      [grocery, hid, shownListId, load],
    ),
    null,
  );
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

  const shown = items ?? NO_ITEMS;
  const aisleOrder = list?.aisleOrder;
  const view = useMemo(() => {
    const layout = GroceryLayout.of(shown);
    return { ...layout, aisles: StoreOrder.sort(layout.aisles, aisleOrder ?? []) };
  }, [shown, aisleOrder]);
  // Why the aisles move, once per phone (after v1, UX-08): once this list has learned an order and has two aisles.
  const aisleHint = !usePreferences().aisleHintDone && (aisleOrder ?? []).length > 0 && view.aisles.length >= 2;
  const remote = useRemoteChanges(
    shown,
    (i) => i.id,
    (i) => i.pendingSync,
    (i) => ({ ...i, pendingSync: false }),
  );

  // "Sam added Eggs" when someone else adds to the list being viewed (DESIGN 9.2). Only items added in the last
  // minutes: a phone whose cache starts empty would otherwise announce every item already on the list.
  const known = useRef<{ listId: string; ids: Set<string> } | null>(null);
  useEffect(() => {
    if (items === null || !list) return;
    const before = known.current;
    if (before && before.listId === list.id) {
      const recent = clock.now() - ANNOUNCE_WITHIN_MS;
      for (const item of items) {
        if (before.ids.has(item.id) || !item.createdBy || item.createdBy === me.user.uid) continue;
        if (item.createdAt !== null && item.createdAt < recent) continue;
        const who = members.get(item.createdBy);
        if (who) snackbars.show(str('grocery_someone_added', who, item.name));
      }
    }
    known.current = { listId: list.id, ids: new Set(items.map((i) => i.id)) };
  }, [items, list, members, me.user.uid, snackbars, clock]);

  const failed = (e: unknown) => snackbars.show(str(messageId(e)));
  const act = (work: () => Promise<unknown>) => void work().catch(failed);
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

  const toggle = (item: GroceryItem) => {
    if (!list) return;
    act(async () => {
      await groceryService.setChecked(hid, actor, list.id, item, !item.checked);
      // Learn the store's aisle order from the order things go into the cart (DESIGN 5.3).
      if (!item.checked) {
        const aisle = item.category ?? 'other';
        const learned = StoreOrder.learn(list.aisleOrder, trip.checked(list.id, aisle), aisle);
        if (learned) await groceryService.setAisleOrder(hid, actor, list, learned);
      }
    });
  };
  const remove = (item: GroceryItem) =>
    list && actUndo(() => groceryService.delete(hid, actor, list.id, item), str('grocery_deleted', item.name));

  const addedBy = (item: GroceryItem) =>
    item.createdBy && item.createdBy !== me.user.uid && item.source === 'manual'
      ? (members.get(item.createdBy) ?? null)
      : null;
  const rowProps = (item: GroceryItem) => ({
    item,
    backInStock: RestockEngine.isBackInStock(item, pantryData),
    addedBy: addedBy(item),
    highlight: remote.has(item.id),
    onToggle: toggle,
    onDelete: remove,
    onEdit: setEditing,
  });

  const checkedCount = view.inCart.length;
  return (
    <TabScaffold
      title={<ListMenu lists={lists ?? []} current={list} onSelect={(l) => onSelectList(l.id)} onDialog={setDialog} />}
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
      <QuickAddField
        text={text}
        onText={setText}
        suggestions={GroceryLayout.suggestions(text, shown, pantryData)}
        onAdd={(value) => quickAdd(value)}
      />
      {items === null ? (
        <div className="sn-centered">{!loadFailed && <Progress />}</div>
      ) : items.length === 0 ? (
        <EmptyState icon="outlined/shopping_cart" message={str('grocery_empty')} />
      ) : (
        <GroceryListView
          aisles={view.aisles.map(([category, rows]) => [categoryLabel(category), category, rows])}
          inCart={view.inCart}
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
  aisles,
  inCart,
  cartOpen,
  onToggleCart,
  onClear,
  row,
  aisleHint,
  onAisleHintDone,
}: {
  aisles: [string, string, GroceryItem[]][];
  inCart: GroceryItem[];
  cartOpen: boolean;
  onToggleCart: () => void;
  onClear: () => void;
  row: (item: GroceryItem) => Parameters<typeof GroceryRow>[0];
  aisleHint: boolean;
  onAisleHintDone: () => void;
}) {
  const onScroll = useOnInnerScroll();
  return (
    <div className="sn-pantry-list" data-testid="grocery_list" onScroll={onScroll}>
      {aisles.map(([label, key, rows], index) => (
        <section key={key} aria-label={label}>
          <h2 className="sn-group-header sn-aisle-header t-label-large">{label}</h2>
          {/* Under the first aisle, once: why the aisles move (after v1, UX-08). */}
          {index === 0 && aisleHint && <AisleHint onDone={onAisleHintDone} />}
          {rows.map((item) => (
            <GroceryRow key={item.id} {...row(item)} />
          ))}
        </section>
      ))}
      {inCart.length > 0 && (
        <section aria-label={plural('grocery_in_cart', inCart.length, inCart.length)}>
          <div className="sn-cart-header" data-testid="cart_header">
            {/* A heading, so VoiceOver's rotor jumps to it like the aisles, with expanded or collapsed (A11Y-09). */}
            <h2 className="sn-cart-header__heading">
              <button
                type="button"
                className="sn-cart-header__toggle m3-state"
                aria-expanded={cartOpen}
                onClick={onToggleCart}
              >
                <span className="t-label-large sn-cart-header__label">
                  {plural('grocery_in_cart', inCart.length, inCart.length)}
                </span>
                <Icon name={cartOpen ? 'round/expand_less' : 'round/expand_more'} />
              </button>
            </h2>
            <Button variant="text" large onClick={onClear} data-testid="button_clear_checked">
              {str('grocery_clear_checked')}
            </Button>
          </div>
          {cartOpen && inCart.map((item) => <GroceryRow key={item.id} {...row(item)} />)}
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

/** The quick-add field stays at the top; typing suggests pantry products and names already used (DESIGN 5.3). */
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
  return (
    <div className="sn-pantry-search sn-quick-add sn-anchor">
      <TextField
        label=""
        aria-label={str('grocery_quick_add')}
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
            setOpen(false);
            onAdd(text);
          }
        }}
        trailing={
          <IconButton
            icon="round/add"
            label={str('grocery_add_to_list')}
            disabled={text.trim() === ''}
            onClick={() => {
              setOpen(false);
              onAdd(text);
            }}
          />
        }
      />
      <Menu open={open && suggestions.length > 0} onDismiss={() => setOpen(false)} align="start" focusFirst={false}>
        {suggestions.map((name) => (
          <MenuItem
            key={name}
            label={name}
            onClick={() => {
              setOpen(false);
              onAdd(name);
            }}
          />
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
