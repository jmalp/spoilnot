import { memo, useState } from 'react';
import { plural, str } from '../../core/i18n';
import {
  DISCARD_REASONS,
  LOCATIONS,
  type DiscardReason,
  type Location,
  type StockBatch,
} from '../../domain/pantry/models';
import type { OutItem, PantryItem, PurchaseItem } from '../../domain/pantry/items';
import type { LocalDate } from '../../domain/time';
import { Button, Dialog, Icon, IconButton, Menu, MenuItem, Radio, StatusTile } from '../kit';
import { useSwipe } from '../swipe';
import {
  amountDescription,
  chipText,
  locationLabel,
  reasonLabel,
  rowAmount,
  statusIcon,
  statusText,
  tone,
  type StatusTone,
} from './labels';
import './pantry.css';

/** What can be done to one batch. Every swipe has a menu equivalent (DESIGN 5.2, 6.8). */
export type BatchActions = {
  onMinus: (b: StockBatch) => void;
  onPlus: (b: StockBatch) => void;
  onUsedUp: (b: StockBatch) => void;
  onThrowAway: (b: StockBatch) => void;
  onMove: (b: StockBatch) => void;
  /** Offered only while the batch is sealed. */
  onOpened: (b: StockBatch) => void;
};

/**
 * One purchase as a row of its group (DESIGN 19.5, 19.6; PantryItemRow in the app): the status tile, the name, the
 * status in words and the location, the amount. Tap to edit; swipe right to use up, left to throw away; the menu has
 * the same actions plus one less, one more and Move. An Out staple's row adds stock. A row of a `sn-group`, which
 * gives its corners.
 *
 * Memoized (review WEB-13): with the same item object (`keepUnchanged`) and handlers, another phone's change to some
 * other row doesn't redraw it.
 */
export const PantryItemRow = memo(function PantryItemRow({
  item,
  today,
  actions,
  onOpen,
  highlight = false,
}: {
  item: PantryItem;
  today: LocalDate;
  actions: BatchActions;
  onOpen: (item: PantryItem) => void;
  /** Changed on another phone a moment ago (DESIGN 6.4). */
  highlight?: boolean;
}) {
  return item.kind === 'purchase' ? (
    <PurchaseRow item={item} today={today} actions={actions} onOpen={onOpen} highlight={highlight} />
  ) : (
    <OutRow item={item} onOpen={onOpen} />
  );
});

function PurchaseRow({
  item,
  today,
  actions,
  onOpen,
  highlight,
}: {
  item: PurchaseItem;
  today: LocalDate;
  actions: BatchActions;
  onOpen: (item: PantryItem) => void;
  highlight: boolean;
}) {
  const batch = item.batch;
  const swipe = useSwipe((direction) => (direction === 'right' ? actions.onUsedUp(batch) : actions.onThrowAway(batch)));
  const amount = rowAmount(batch.quantity, batch.unit);
  const location = locationLabel(batch.location);
  const description =
    str(
      'row_description',
      item.name,
      amountDescription(batch.quantity, batch.unit),
      statusText(batch.expiryDate, batch.dateType, today),
      location,
    ) + (batch.openedOn ? str('row_opened_suffix') : '');

  return (
    <div
      className={`sn-segment sn-pantry-row ${highlight ? 'sn-remote-highlight' : ''}`}
      data-testid={`pantry_item_${batch.id}`}
      data-remote={highlight || undefined}
    >
      {swipe.dx !== 0 && <SwipeBackground direction={swipe.dx > 0 ? 'usedUp' : 'throwAway'} />}
      <div className="sn-pantry-row__front" style={swipe.style} {...swipe.handlers}>
        <button
          type="button"
          className="sn-pantry-row__main m3-state"
          aria-label={description}
          onClick={() => {
            if (!swipe.wasDragged()) onOpen(item);
          }}
        >
          <StatusTile tone={tone(item.status)} icon={statusIcon(item.status)} />
          <span className="sn-pantry-row__text">
            <NameLine name={item.name} low={item.isLow} pending={batch.pendingSync} />
            <SecondLine
              status={chipText(batch.expiryDate, batch.dateType, today)}
              tone={tone(item.status)}
              rest={[location, ...(batch.openedOn ? [str('pantry_opened')] : [])]}
              // From 1.5x text the amount joins the second line, so the name keeps the row's width (review A11Y-04).
              amount={amount}
            />
          </span>
          {amount && <span className="sn-pantry-row__amount t-row-title">{amount}</span>}
        </button>
        <PurchaseMenu batch={batch} actions={actions} />
      </div>
    </div>
  );
}

/** A staple with nothing left: "Out" in the Expired color, words and tile (DESIGN 19.6). */
function OutRow({ item, onOpen }: { item: OutItem; onOpen: (item: PantryItem) => void }) {
  const product = item.product;
  return (
    <div className="sn-segment sn-pantry-row" data-testid={`pantry_item_out_${product.id}`}>
      <div className="sn-pantry-row__front">
        <button
          type="button"
          className="sn-pantry-row__main sn-pantry-row__main--out m3-state"
          aria-label={str('row_description_out', product.name)}
          onClick={() => onOpen(item)}
        >
          <StatusTile tone="expired" icon="round/remove_circle_outline" />
          <span className="sn-pantry-row__text">
            <NameLine name={product.name} low={false} pending={false} />
            <SecondLine status={str('badge_out')} tone="expired" rest={[locationLabel(product.defaultLocation)]} />
          </span>
        </button>
      </div>
    </div>
  );
}

/** A no-break space: a second line wraps only after a "·", never inside a part ("× 2", "500 g"). */
const NBSP = ' ';

/**
 * "Tomorrow · Fridge · Opened": the status in its color and heavier, then the rest (DESIGN 19.6). No date reads like
 * the rest of the line. [amount] shows here only from 1.5x text (CSS), where it leaves the end of the row.
 */
function SecondLine({
  status,
  tone,
  rest,
  amount = null,
}: {
  status: string;
  tone: StatusTone;
  rest: string[];
  amount?: string | null;
}) {
  const part = (text: string) => `${NBSP}· ${text.replaceAll(' ', NBSP)}`;
  return (
    <span className="sn-pantry-row__second t-row-supporting">
      <span className={tone === 'no-date' ? undefined : `t-row-status sn-tone--${tone}`}>{status}</span>
      {rest.map(part).join('')}
      {amount && <span className="sn-pantry-row__amount-inline">{part(amount)}</span>}
    </span>
  );
}

function NameLine({ name, low, pending }: { name: string; low: boolean; pending: boolean }) {
  return (
    <span className="sn-pantry-row__name">
      <span className="t-row-title sn-pantry-row__name-text">{name}</span>
      {low && <Badge text={str('badge_low')} />}
      {pending && (
        <span className="c-on-surface-variant">
          <Icon name="round/cloud_off" size={16} label={str('pending_sync')} />
        </span>
      )}
    </span>
  );
}

/** Small pill such as Low. */
export function Badge({ text, hidden = false }: { text: string; hidden?: boolean }) {
  return (
    <span className="sn-badge t-label-medium" aria-hidden={hidden || undefined}>
      {text}
    </span>
  );
}

function PurchaseMenu({ batch, actions }: { batch: StockBatch; actions: BatchActions }) {
  const [open, setOpen] = useState(false);
  const entries: [string, (b: StockBatch) => void][] = [
    [str('action_use_one'), actions.onMinus],
    [str('action_add_one'), actions.onPlus],
    ...(batch.openedOn === null
      ? [[str('action_mark_opened'), actions.onOpened] as [string, (b: StockBatch) => void]]
      : []),
    [str('action_used_up'), actions.onUsedUp],
    [str('action_throw_away'), actions.onThrowAway],
    [str('action_move'), actions.onMove],
  ];
  return (
    <span className="sn-anchor sn-pantry-row__menu">
      <IconButton
        icon="round/more_vert"
        label={str('batch_options')}
        onClick={() => setOpen(true)}
        data-testid={`menu_${batch.id}`}
      />
      <Menu open={open} onDismiss={() => setOpen(false)}>
        {entries.map(([label, action]) => (
          <MenuItem
            key={label}
            label={label}
            onClick={() => {
              setOpen(false);
              action(batch);
            }}
          />
        ))}
      </Menu>
    </span>
  );
}

/** The swipe's tint, icon and words, in the row's shape: right is Used up, left is Throw away (DESIGN 19.6). */
function SwipeBackground({ direction }: { direction: 'usedUp' | 'throwAway' }) {
  const usedUp = direction === 'usedUp';
  return (
    <div className={`sn-swipe sn-swipe--${direction}`} aria-hidden>
      <Icon name={usedUp ? 'round/done' : 'round/delete_outline'} />
      <span className="t-button-medium">{str(usedUp ? 'action_used_up' : 'action_throw_away')}</span>
    </div>
  );
}

/**
 * "65 expired items" with Show or Hide: the expired backlog folded into one row of its group, the first of Use first
 * in the default view (after v1, DESIGN 19.12).
 */
export function ExpiredFoldRow({ count, shown, onToggle }: { count: number; shown: boolean; onToggle: () => void }) {
  return (
    <div className="sn-segment sn-pantry-fold" data-testid="pantry_expired_header">
      <StatusTile tone="expired" icon="round/delete_outline" />
      <span className="sn-pantry-fold__text t-row-title">{plural('pantry_expired_count', count, count)}</span>
      <Button variant="text" onClick={onToggle} data-testid="pantry_expired_toggle">
        {str(shown ? 'pantry_expired_hide' : 'pantry_expired_show')}
      </Button>
    </div>
  );
}

/** Throw away asks for a reason (DESIGN 5.2). */
export function ThrowAwayDialog({
  name,
  onReason,
  onDismiss,
}: {
  name: string;
  onReason: (r: DiscardReason) => void;
  onDismiss: () => void;
}) {
  return (
    <ChoiceDialog
      title={str('throw_away_title', name)}
      options={[...DISCARD_REASONS]}
      label={reasonLabel}
      onPick={onReason}
      onDismiss={onDismiss}
    />
  );
}

export function MoveDialog({
  current,
  onPick,
  onDismiss,
}: {
  current: Location;
  onPick: (l: Location) => void;
  onDismiss: () => void;
}) {
  return (
    <ChoiceDialog
      title={str('move_title')}
      options={LOCATIONS.filter((l) => l !== current)}
      label={locationLabel}
      onPick={onPick}
      onDismiss={onDismiss}
    />
  );
}

/**
 * A dialog that asks for one choice (DESIGN 19.11): its title in the dialog title style and the choices as a group of
 * rows on the dialog row color, each a radio button that picks and closes; Cancel at the end.
 */
function ChoiceDialog<T extends string>({
  title,
  options,
  label,
  onPick,
  onDismiss,
}: {
  title: string;
  options: T[];
  label: (t: T) => string;
  onPick: (t: T) => void;
  onDismiss: () => void;
}) {
  return (
    <Dialog
      title={title}
      titleClassName="t-dialog-title"
      onDismiss={onDismiss}
      actions={
        <Button variant="text" onClick={onDismiss}>
          {str('action_cancel')}
        </Button>
      }
    >
      <div role="radiogroup" className="sn-group sn-group--dialog sn-choices">
        {options.map((option) => (
          <label key={option} className="sn-segment sn-choice t-body-large">
            <Radio
              name="choice"
              checked={false}
              onCheckedChange={() => {
                onPick(option);
                onDismiss();
              }}
            />
            {label(option)}
          </label>
        ))}
      </div>
    </Dialog>
  );
}
