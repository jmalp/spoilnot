import { useEffect, useMemo, useState } from 'react';
import { shareReplay } from 'rxjs';
import { messageId } from '../../core/errors';
import { pushStatus } from '../../core/push';
import { useObservable, useToday } from '../../core/hooks';
import { useRemoteChanges } from '../../core/remoteChanges';
import { str, type StringId } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import {
  ExpiredFold,
  PantryItems,
  type PantryFilter,
  type PantryGrouping,
  type PantryItem,
  type PantryQuery,
  type PantrySort,
} from '../../domain/pantry/items';
import type { PantryData, Product, StockBatch } from '../../domain/pantry/models';
import type { Undo } from '../../domain/write';
import { NotificationRules } from '../../domain/notifications';
import { NotificationExplainer } from '../notifications/NotificationExplainer';
import { Button, Fab, FilterChip, Icon, IconButton, Menu, MenuItem, Progress, TextField, useSnackbars } from '../kit';
import { EmptyState, OfflineBanner, TabScaffold, useOnInnerScroll } from '../Scaffold';
import { categoryLabel, locationLabel } from './labels';
import { AddItemSheet, EditItemSheet } from './ItemForm';
import { OpenedSheet } from './OpenedSheet';
import { ExpiredHeader, MoveDialog, PantryItemRow, ThrowAwayDialog, type BatchActions } from './PantryRow';
import './pantry.css';

const FILTERS: [PantryFilter, StringId][] = [
  ['fridge', 'location_fridge'],
  ['freezer', 'location_freezer'],
  ['pantry', 'location_pantry'],
  ['other', 'location_other'],
  ['expiringSoon', 'filter_expiring_soon'],
  ['expired', 'filter_expired'],
  ['lowStock', 'filter_low_stock'],
];

/** The Pantry tab (DESIGN 5.2): search, filters, one row per purchase, swipe and menu actions with Undo. */
export function PantryScreen({
  onOpenSettings,
  joinRequests,
  query,
  onQueryChange: setQuery,
}: {
  onOpenSettings: () => void;
  joinRequests: number;
  /** Kept by the shell, so the filter and search survive leaving the tab, and Home's cards can set the filter. */
  query: PantryQuery;
  onQueryChange: (query: PantryQuery) => void;
}) {
  const me = useHouseholdSession();
  const { pantry, pantryService, writer, clock, preferences } = useServices();
  const snackbars = useSnackbars();
  const today = useToday(clock);
  const data$ = useMemo(
    () => pantry.observe(me.householdId).pipe(shareReplay({ bufferSize: 1, refCount: true })),
    [pantry, me.householdId],
  );
  const data = useObservable<PantryData | null>(data$, null);
  const [throwing, setThrowing] = useState<StockBatch | null>(null);
  const [moving, setMoving] = useState<StockBatch | null>(null);
  const [opening, setOpening] = useState<StockBatch | null>(null);
  const [editing, setEditing] = useState<StockBatch | null>(null);
  // An Out staple opens Add item for that product.
  const [adding, setAdding] = useState<{ prefill: Product | null } | null>(null);
  const [explaining, setExplaining] = useState(false);
  /**
   * After the third dated item, explain notifications once (DESIGN 5.4): already allowed or refused, it is not
   * needed; in Safari outside the Home Screen it waits until the app is installed.
   */
  const askForNotifications = (hasDate: boolean, datedBefore: number) => {
    if (!NotificationRules.shouldExplain(hasDate, datedBefore, preferences.get().notificationExplainerShown)) return;
    const status = pushStatus();
    if (status === 'default') setExplaining(true);
    else if (status === 'granted' || status === 'denied') preferences.set({ notificationExplainerShown: true });
  };
  const actor = useMemo(() => ({ uid: me.user.uid, name: me.displayName }), [me]);

  // Invariant 1: delete batches that reached 0, whoever notices first.
  useEffect(() => {
    if (data) void pantryService.deleteEmptyBatches(me.householdId, actor, data).catch(() => undefined);
  }, [data, pantryService, me.householdId, actor]);
  // A server rejection of a write arrives after it was applied locally.
  useEffect(() => {
    const subscription = writer.errors$.subscribe((e) => snackbars.show(str(messageId(e))));
    return () => subscription.unsubscribe();
  }, [writer, snackbars]);

  const act = (work: () => Promise<Undo | null>, message?: (undo: Undo) => string) => {
    work().then(
      (undo) => {
        if (undo && message)
          snackbars.showUndo(message(undo), () => void pantryService.undo(me.householdId, actor, undo));
      },
      (e: unknown) => snackbars.show(str(messageId(e))),
    );
  };
  const hid = me.householdId;
  const actions: BatchActions = {
    onMinus: (b) =>
      act(
        () => pantryService.adjust(hid, actor, b, -1),
        () => str('undo_used_up', b.productName),
      ),
    onPlus: (b) => act(() => pantryService.adjust(hid, actor, b, 1)),
    onUsedUp: (b) =>
      act(
        () => pantryService.usedUp(hid, actor, b),
        () => str('undo_used_up', b.productName),
      ),
    onThrowAway: setThrowing,
    onMove: setMoving,
    onOpened: setOpening,
  };

  const groups = data ? PantryItems.build(data.products, data.batches, query, today) : [];
  const empty = data !== null && PantryItems.all(data.products, data.batches, today).length === 0;

  return (
    <TabScaffold
      title={str('tab_pantry')}
      onOpenSettings={onOpenSettings}
      joinRequests={joinRequests}
      innerScroll
      testId="screen_pantry"
      actions={
        <SortGroupMenu
          sort={query.sort}
          grouping={query.grouping}
          onSort={(sort) => setQuery({ ...query, sort })}
          onGroup={(grouping) => setQuery({ ...query, grouping })}
        />
      }
      fab={
        data && !empty ? (
          <Fab
            icon="round/add"
            label={str('action_add_item')}
            data-testid="fab_add_item"
            onClick={() => setAdding({ prefill: null })}
          />
        ) : undefined
      }
    >
      {data?.fromCache && <OfflineBanner />}
      {data === null ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : empty ? (
        <EmptyState icon="outlined/kitchen" message={str('pantry_empty_body')}>
          <Button full large data-testid="button_add_first_item" onClick={() => setAdding({ prefill: null })}>
            {str('action_add_first_item')}
          </Button>
        </EmptyState>
      ) : (
        <>
          <div className="sn-pantry-search">
            <TextField
              label=""
              aria-label={str('pantry_search')}
              placeholder={str('pantry_search')}
              value={query.search}
              onValueChange={(search) => setQuery({ ...query, search })}
              type="search"
              enterKeyHint="search"
              data-testid="pantry_search"
              leading={
                <span className="sn-pantry-search__leading">
                  <Icon name="round/search" />
                </span>
              }
              trailing={
                query.search ? (
                  <IconButton
                    icon="round/clear"
                    label={str('action_cancel')}
                    onClick={() => setQuery({ ...query, search: '' })}
                  />
                ) : undefined
              }
            />
          </div>
          <div className="sn-filter-row">
            {/* No All chip: everything is the default, and tapping the chosen filter again goes back to it. */}
            {FILTERS.map(([filter, label]) => (
              <FilterChip
                key={filter}
                label={str(label)}
                selected={query.filter === filter}
                data-testid={`pantry_filter_${filter}`}
                onClick={() => setQuery({ ...query, filter: query.filter === filter ? 'all' : filter })}
              />
            ))}
          </div>
          <PantryList
            groups={groups}
            query={query}
            today={today}
            actions={actions}
            onOpen={(item: PantryItem) =>
              item.kind === 'purchase' ? setEditing(item.batch) : setAdding({ prefill: item.product })
            }
          />
        </>
      )}
      {adding && data && (
        <AddItemSheet
          products={data.products}
          productsWithStock={new Set(data.batches.filter((b) => b.quantity > 0).map((b) => b.productId))}
          today={today}
          imperialFirst={me.units === 'imperial'}
          prefill={adding.prefill}
          onDismiss={() => setAdding(null)}
          onSave={(draft) => {
            const datedBefore = data.batches.filter((b) => b.expiryDate !== null).length;
            void pantryService.add(hid, actor, draft, data).then(
              (result) => {
                setAdding(null);
                snackbars.show(str(result.merged ? 'added_merged_message' : 'added_message', draft.name.trim()));
                askForNotifications(draft.expiryDate !== null, datedBefore);
              },
              (e: unknown) => snackbars.show(str(messageId(e))),
            );
          }}
        />
      )}
      {explaining && (
        <NotificationExplainer
          onDone={() => {
            setExplaining(false);
            preferences.set({ notificationExplainerShown: true });
          }}
        />
      )}
      {editing && data && (
        <EditItemSheet
          batch={editing}
          product={data.products.find((p) => p.id === editing.productId) ?? null}
          canChangeUnit={
            !data.batches.some((b) => b.productId === editing.productId && b.id !== editing.id && b.quantity > 0)
          }
          today={today}
          imperialFirst={me.units === 'imperial'}
          onDismiss={() => setEditing(null)}
          onSave={(edit) => {
            const batch = editing;
            act(
              async () => {
                const undo = await pantryService.editItem(hid, actor, batch, edit, data);
                setEditing(null);
                return undo;
              },
              () => str('undo_used_up', batch.productName),
            );
          }}
        />
      )}
      {throwing && (
        <ThrowAwayDialog
          name={throwing.productName}
          onReason={(reason) =>
            act(
              () => pantryService.throwAway(hid, actor, throwing, reason),
              () => str('undo_thrown_away', throwing.productName),
            )
          }
          onDismiss={() => setThrowing(null)}
        />
      )}
      {moving && (
        <MoveDialog
          current={moving.location}
          onPick={(l) => act(() => pantryService.move(hid, actor, moving, l))}
          onDismiss={() => setMoving(null)}
        />
      )}
      {opening && (
        <OpenedSheet
          batch={opening}
          rememberedDays={data?.products.find((p) => p.id === opening.productId)?.openedShelfLifeDays ?? null}
          today={today}
          onDismiss={() => setOpening(null)}
          onSave={(count, days) => {
            const product = data?.products.find((p) => p.id === opening.productId) ?? null;
            act(
              () => pantryService.markOpened(hid, actor, opening, count, days, product),
              () => str('undo_opened', opening.productName),
            );
            setOpening(null);
          }}
        />
      )}
    </TabScaffold>
  );
}

function PantryList({
  groups,
  query,
  today,
  actions,
  onOpen,
}: {
  groups: ReturnType<typeof PantryItems.build>;
  query: PantryQuery;
  today: string;
  actions: BatchActions;
  onOpen: (item: PantryItem) => void;
}) {
  const onScroll = useOnInnerScroll();
  const [showExpired, setShowExpired] = useState(false);
  // Rows another phone changed get a short highlight (DESIGN 6.4).
  const batches = useMemo(
    () => groups.flatMap((g) => g.items).flatMap((i) => (i.kind === 'purchase' ? [i.batch] : [])),
    [groups],
  );
  const remote = useRemoteChanges(
    batches,
    (b) => b.id,
    (b) => b.pendingSync,
    (b) => ({ ...b, pendingSync: false }),
  );
  if (groups.length === 0) return <EmptyState icon="round/search" message={str('pantry_no_matches')} />;
  // A backlog of expired food folds into one header; its rows show on request.
  const fold = ExpiredFold.of(groups, query);
  return (
    <div className="sn-pantry-list" data-testid="pantry_list" onScroll={onScroll}>
      {fold && (
        <>
          <ExpiredHeader
            count={fold.expired.length}
            shown={showExpired}
            onToggle={() => setShowExpired(!showExpired)}
          />
          {showExpired &&
            fold.expired.map((item) => (
              <PantryItemRow
                key={`expired-${item.key}`}
                item={item}
                today={today}
                actions={actions}
                onOpen={onOpen}
                highlight={remote.has(item.key)}
              />
            ))}
        </>
      )}
      {(fold?.rest ?? groups).map((group) => {
        const header = group.location
          ? locationLabel(group.location)
          : group.category
            ? categoryLabel(group.category)
            : null;
        const key = group.location ?? group.category ?? 'all';
        return (
          <section key={key} aria-label={header ?? undefined}>
            {header && <h2 className="sn-group-header t-label-large">{header}</h2>}
            {group.items.map((item) => (
              <PantryItemRow
                key={`${key}-${item.key}`}
                item={item}
                today={today}
                actions={actions}
                onOpen={onOpen}
                highlight={remote.has(item.key)}
              />
            ))}
          </section>
        );
      })}
    </div>
  );
}

function SortGroupMenu({
  sort,
  grouping,
  onSort,
  onGroup,
}: {
  sort: PantrySort;
  grouping: PantryGrouping;
  onSort: (s: PantrySort) => void;
  onGroup: (g: PantryGrouping) => void;
}) {
  const [open, setOpen] = useState(false);
  const check = (on: boolean) => <span className="sn-menu-check">{on && <Icon name="round/check" />}</span>;
  return (
    <span className="sn-anchor">
      <IconButton icon="round/sort" label={str('sort_and_group')} onClick={() => setOpen(true)} />
      <Menu open={open} onDismiss={() => setOpen(false)}>
        <p className="sn-menu-header t-label-medium">{str('sort_title')}</p>
        {(
          [
            ['soonestExpiry', 'sort_soonest'],
            ['name', 'sort_name'],
            ['recentlyAdded', 'sort_recent'],
          ] as const
        ).map(([value, label]) => (
          <MenuItem key={value} label={str(label)} leading={check(sort === value)} onClick={() => onSort(value)} />
        ))}
        <hr className="sn-divider" />
        <p className="sn-menu-header t-label-medium">{str('group_title')}</p>
        {(
          [
            ['none', 'group_none'],
            ['location', 'group_location'],
            ['category', 'group_category'],
          ] as const
        ).map(([value, label]) => (
          <MenuItem key={value} label={str(label)} leading={check(grouping === value)} onClick={() => onGroup(value)} />
        ))}
      </Menu>
    </span>
  );
}
