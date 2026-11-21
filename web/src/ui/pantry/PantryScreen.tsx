import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { catchError, firstValueFrom, map, of, timeout } from 'rxjs';
import { messageId } from '../../core/errors';
import { useHeldWhileScrolling } from '../../core/holdWhileScrolling';
import { useLoadRetry, useObservable, useToday } from '../../core/hooks';
import { retryLoading } from '../../domain/load';
import { useRemoteChanges } from '../../core/remoteChanges';
import { plural, str, type StringId } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import { pantryBlocks, type PantryHeading } from '../../domain/pantry/blocks';
import { useKeepUnchanged } from '../../core/keepUnchanged';
import {
  PantryItems,
  type PantryFilter,
  type PantryGrouping,
  type PantryItem,
  type PantryItemGroup,
  type PantryQuery,
  type PantrySort,
} from '../../domain/pantry/items';
import { checkEdit, itemEditOf, type ItemEditBase } from '../../domain/pantry/edit-watch';
import type { Location, PantryData, Product, StockBatch } from '../../domain/pantry/models';
import type { Undo } from '../../domain/write';
import {
  Button,
  Fab,
  Icon,
  IconButton,
  Menu,
  MenuItem,
  Progress,
  SearchField,
  SpoilNotFilterChip,
  useSnackbars,
} from '../kit';
import { EmptyState, LoadError, OfflineBanner, TabScaffold } from '../Scaffold';
import { categoryLabel, locationIcon, locationLabel } from './labels';
import { AddItemSheet, EditItemSheet } from './ItemForm';
import { OpenedSheet } from './OpenedSheet';
import { ExpiredFoldRow, MoveDialog, PantryItemRow, ThrowAwayDialog, type BatchActions } from './PantryRow';
import './pantry.css';

const FILTERS: [PantryFilter, StringId][] = [
  ['fridge', 'location_fridge'],
  ['freezer', 'location_freezer'],
  ['pantry', 'location_pantry'],
  ['other', 'location_other'],
  ['needsAttention', 'filter_needs_attention'],
  ['expired', 'filter_expired'],
  ['lowStock', 'filter_low_stock'],
];

/** The place a location filter shows, for its chip's icon; the others have none (DECISIONS 2026-11-16). */
const filterLocation = (filter: PantryFilter): Location | null =>
  filter === 'fridge' || filter === 'freezer' || filter === 'pantry' || filter === 'other' ? filter : null;

/** How long the Edit window waits for the member list to name who removed its item. */
const MEMBER_NAME_WAIT_MS = 3_000;

/** A pantry row's key, for keeping unchanged items across snapshots (review WEB-13). */
const itemKey = (item: PantryItem) => item.key;

/**
 * The Pantry tab (DESIGN 5.2, 19.12): how many rows the list has above the title, the search field, the filter chips
 * and one row per purchase, in two groups in the default view; swipe and menu actions with Undo.
 */
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
  const { pantry, pantryService, clock, households } = useServices();
  const snackbars = useSnackbars();
  const today = useToday(clock);
  // The household's shared pantry listeners (after v1, DESIGN 9.1).
  // "Couldn't load. Retry" (DESIGN 6.7): the listener listens again instead of spinning (after v1, WEB-10).
  const { load, failed } = useLoadRetry();
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(me.householdId).pipe(retryLoading(load)), [pantry, me.householdId, load]),
    null,
  );
  const [throwing, setThrowing] = useState<StockBatch | null>(null);
  const [moving, setMoving] = useState<StockBatch | null>(null);
  const [opening, setOpening] = useState<StockBatch | null>(null);
  // What the open Edit window compares with (after v1, DESIGN 9.2 and 9.4).
  const [editing, setEditing] = useState<ItemEditBase | null>(null);
  // An Out staple opens Add item for that product.
  const [adding, setAdding] = useState<{ prefill: Product | null } | null>(null);
  const actor = useMemo(() => ({ uid: me.user.uid, name: me.displayName }), [me]);
  const hid = me.householdId;

  // Invariant 1: delete batches that reached 0, whoever notices first.
  useEffect(() => {
    if (data) void pantryService.deleteEmptyBatches(me.householdId, actor, data).catch(() => undefined);
  }, [data, pantryService, me.householdId, actor]);

  const act = useCallback(
    (work: () => Promise<Undo | null>, message?: (undo: Undo) => string) => {
      work().then(
        (undo) => {
          if (undo && message) snackbars.showUndo(message(undo), () => void pantryService.undo(hid, actor, undo));
        },
        (e: unknown) => snackbars.show(str(messageId(e))),
      );
    },
    [snackbars, pantryService, hid, actor],
  );
  // The same handlers across snapshots, so memoized rows skip what didn't change (review WEB-13).
  const actions: BatchActions = useMemo(
    () => ({
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
    }),
    [act, pantryService, hid, actor],
  );
  // The newest pantry for handlers that run on a tap, so they keep their identity from snapshot to snapshot.
  const latest = useRef(data);
  useEffect(() => {
    latest.current = data;
  }, [data]);
  const onOpen = useCallback(
    (item: PantryItem) =>
      item.kind === 'purchase'
        ? setEditing({
            batch: item.batch,
            product: latest.current?.products.find((p) => p.id === item.batch.productId) ?? null,
          })
        : setAdding({ prefill: item.product }),
    [],
  );

  // Members' names, only while the Edit window is open (it names who changed its item).
  const editOpen = editing !== null;
  const members = useObservable<Map<string, string>>(
    useMemo(
      () =>
        editOpen
          ? households.observeMembers(hid).pipe(
              map((all) => new Map(all.map((m) => [m.uid, m.displayName]))),
              retryLoading(undefined, new Map<string, string>()),
            )
          : of(new Map<string, string>()),
      [households, hid, editOpen],
    ),
    new Map(),
  );
  const watch = editing && data ? checkEdit(editing, data, me.user.uid) : null;
  // The item is gone: close the window and say who removed it, unless it was this person. The sheet is hidden at
  // once (below); the window closes once the remover is known, and only once.
  const closing = useRef<ItemEditBase | null>(null);
  const removed = editing !== null && watch?.kind === 'removed' ? watch : null;
  useEffect(() => {
    if (!editing || !removed || closing.current === editing) return;
    closing.current = editing;
    void (async () => {
      const who = removed.byUid ?? (await pantry.removedBy(hid, editing.batch.id));
      setEditing((open) => (open === editing ? null : open));
      if (who === me.user.uid) return;
      // The member list starts with the window, so a removal right after opening it may come first: wait a moment.
      const name =
        who === null
          ? null
          : (members.get(who) ??
            (await firstValueFrom(
              households.observeMembers(hid).pipe(
                map((all) => all.find((m) => m.uid === who)?.displayName ?? null),
                timeout(MEMBER_NAME_WAIT_MS),
                catchError(() => of(null)),
              ),
            )));
      snackbars.show(name !== null ? str('edit_removed_by', name) : str('edit_removed'));
    })();
  }, [editing, removed, pantry, households, hid, me.user.uid, members, snackbars]);

  const all = useKeepUnchanged(
    useMemo(() => (data ? PantryItems.all(data.products, data.batches, today) : []), [data, today]),
    itemKey,
  );
  const groups = useMemo(() => PantryItems.group(all, query), [all, query]);
  const empty = data !== null && all.length === 0;
  // Once per change of the pantry, not on every render while the sheet is open (review PERF-08).
  const productsWithStock = useMemo(
    () => new Set(data?.batches.filter((b) => b.quantity > 0).map((b) => b.productId)),
    [data],
  );

  return (
    <TabScaffold
      title={str('tab_pantry')}
      eyebrow={data ? plural('pantry_eyebrow_items', all.length, all.length) : undefined}
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
      {failed ? <LoadError onRetry={() => load.retry()} /> : <OfflineBanner offline={!!data?.fromCache} />}
      {data === null ? (
        <div className="sn-centered">{!failed && <Progress />}</div>
      ) : empty ? (
        <div className="sn-pantry-empty">
          <span className="sn-pantry-empty__leaf" aria-hidden>
            <Icon name="outlined/kitchen" />
          </span>
          <p>{str('pantry_empty_body')}</p>
          <Button primary data-testid="button_add_first_item" onClick={() => setAdding({ prefill: null })}>
            {str('action_add_first_item')}
          </Button>
        </div>
      ) : (
        <>
          <div className="sn-pantry-search sn-pantry-search--look">
            <SearchField
              value={query.search}
              onValueChange={(search) => setQuery({ ...query, search })}
              placeholder={str('pantry_search')}
              clearLabel={str('action_cancel')}
              testId="pantry_search"
            />
          </div>
          <div className="sn-filter-row sn-filter-row--look">
            {/* No All chip: everything is the default, and tapping the chosen filter again goes back to it. */}
            {FILTERS.map(([filter, label]) => {
              const location = filterLocation(filter);
              return (
                <SpoilNotFilterChip
                  key={filter}
                  label={str(label)}
                  selected={query.filter === filter}
                  icon={location ? locationIcon(location) : undefined}
                  data-testid={`pantry_filter_${filter}`}
                  onClick={() => setQuery({ ...query, filter: query.filter === filter ? 'all' : filter })}
                />
              );
            })}
          </div>
          <PantryList
            groups={groups}
            query={query}
            batches={data.batches}
            today={today}
            actions={actions}
            onOpen={onOpen}
          />
        </>
      )}
      {adding && data && (
        <AddItemSheet
          products={data.products}
          productsWithStock={productsWithStock}
          today={today}
          imperialFirst={me.units === 'imperial'}
          prefill={adding.prefill}
          onDismiss={() => setAdding(null)}
          onSave={(draft) => {
            void pantryService.add(hid, actor, draft, data).then(
              (result) => {
                setAdding(null);
                snackbars.show(str(result.merged ? 'added_merged_message' : 'added_message', draft.name.trim()));
              },
              (e: unknown) => snackbars.show(str(messageId(e))),
            );
          }}
        />
      )}
      {editing && data && watch?.kind !== 'removed' && (
        <EditItemSheet
          key={editing.batch.id}
          batch={editing.batch}
          product={editing.product}
          base={itemEditOf(editing)}
          changed={watch?.kind === 'changed' ? watch.live : null}
          changedBy={watch?.kind === 'changed' && watch.byUid !== null ? (members.get(watch.byUid) ?? null) : null}
          onReviewed={setEditing}
          canChangeUnit={
            !data.batches.some(
              (b) => b.productId === editing.batch.productId && b.id !== editing.batch.id && b.quantity > 0,
            )
          }
          today={today}
          imperialFirst={me.units === 'imperial'}
          onDismiss={() => setEditing(null)}
          onSave={(edit) => {
            const base = editing;
            act(
              async () => {
                const undo = await pantryService.editItem(hid, actor, base, edit, data);
                setEditing(null);
                return undo;
              },
              () => str('undo_used_up', base.batch.productName),
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

function headingText(heading: PantryHeading): string {
  switch (heading.kind) {
    case 'useFirst':
      return str('pantry_use_first');
    case 'everythingElse':
      return str('pantry_everything_else');
    case 'location':
      return locationLabel(heading.location);
    case 'category':
      return categoryLabel(heading.category);
  }
}

/**
 * The list as groups of rows (DESIGN 19.5, 19.12): Use first and Everything else in the default view, a group per
 * location or category when grouped. A change from another phone waits while the list scrolls (DESIGN 6.4, WEB-11).
 * A new search, filter, sort or grouping replaces the rows at once: rows have no fade or slide on the web.
 */
function PantryList({
  groups,
  query,
  batches,
  today,
  actions,
  onOpen,
}: {
  groups: PantryItemGroup[];
  query: PantryQuery;
  batches: StockBatch[];
  today: string;
  actions: BatchActions;
  onOpen: (item: PantryItem) => void;
}) {
  const [scroller, setScroller] = useState<HTMLDivElement | null>(null);
  // The rows are held with the query that picked them and every batch, so all of them change at once (Android's
  // ShownRows).
  const rows = useMemo(() => ({ groups, query, batches }), [groups, query, batches]);
  const { groups: shown, query: shownQuery, batches: allBatches } = useHeldWhileScrolling(rows, scroller);
  const [showExpired, setShowExpired] = useState(false);
  // Rows another phone changed get a short highlight (DESIGN 6.4). Told from every batch, not the rows shown: a row
  // that a wider search or filter brings back isn't new (after v1, 2026-11-17; it was highlighted as if another
  // phone had added it).
  const remote = useRemoteChanges(
    allBatches,
    (b) => b.id,
    (b) => b.pendingSync,
    (b) => ({ ...b, pendingSync: false }),
  );
  const blocks = useMemo(() => pantryBlocks(shown, shownQuery, showExpired), [shown, shownQuery, showExpired]);
  if (shown.length === 0) return <EmptyState icon="round/search" message={str('pantry_no_matches')} />;
  return (
    <div className="sn-pantry-list sn-pantry-list--look" data-testid="pantry_list" ref={setScroller}>
      {blocks.map((block) => {
        const heading = block.heading ? headingText(block.heading) : null;
        return (
          <section key={block.key} className="sn-pantry-block" aria-label={heading ?? undefined}>
            {heading && <h2 className="sn-pantry-heading t-group-heading">{heading}</h2>}
            <div className="sn-group">
              {block.lines.map((line) =>
                line.kind === 'folded' ? (
                  <ExpiredFoldRow
                    key={line.key}
                    count={line.count}
                    shown={showExpired}
                    onToggle={() => setShowExpired(!showExpired)}
                  />
                ) : (
                  <PantryItemRow
                    key={line.key}
                    item={line.item}
                    today={today}
                    actions={actions}
                    onOpen={onOpen}
                    highlight={remote.has(line.key)}
                  />
                ),
              )}
            </div>
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
