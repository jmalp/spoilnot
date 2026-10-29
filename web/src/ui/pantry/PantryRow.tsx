import { useRef, useState, type PointerEvent } from 'react';
import { plural, str } from '../../core/i18n';
import {
  DISCARD_REASONS,
  LOCATIONS,
  type DateType,
  type DiscardReason,
  type Location,
  type StockBatch,
} from '../../domain/pantry/models';
import type { OutItem, PantryItem, PurchaseItem } from '../../domain/pantry/items';
import { Expiry } from '../../domain/pantry/rules';
import type { LocalDate } from '../../domain/time';
import { Button, Dialog, Icon, IconButton, Menu, MenuItem, Radio } from '../kit';
import {
  amountDescription,
  chipText,
  locationIcon,
  locationLabel,
  reasonLabel,
  rowAmount,
  statusIcon,
  statusText,
  tone,
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
 * One purchase: status, name, date and location, quantity. Tap to edit; swipe right to use up, left to throw away;
 * the menu has the same actions plus one less, one more and Move. An Out staple's row adds stock.
 */
export function PantryItemRow({
  item,
  today,
  actions,
  onOpen,
}: {
  item: PantryItem;
  today: LocalDate;
  actions: BatchActions;
  onOpen: (item: PantryItem) => void;
}) {
  return item.kind === 'purchase' ? (
    <PurchaseRow item={item} today={today} actions={actions} onOpen={onOpen} />
  ) : (
    <OutRow item={item} onOpen={onOpen} />
  );
}

/** How far a row must be swiped, as a share of its width, to act (SwipeToDismissBox's default threshold). */
const SWIPE_THRESHOLD = 0.4;

function PurchaseRow({
  item,
  today,
  actions,
  onOpen,
}: {
  item: PurchaseItem;
  today: LocalDate;
  actions: BatchActions;
  onOpen: (item: PantryItem) => void;
}) {
  const batch = item.batch;
  const [dx, setDx] = useState(0);
  const [dragging, setDragging] = useState(false);
  const drag = useRef<{ x: number; y: number; horizontal: boolean | null; width: number } | null>(null);
  const moved = useRef(false);
  const amount = rowAmount(batch.quantity, batch.unit);
  const location = locationLabel(batch.location);
  const status = statusText(batch.expiryDate, batch.dateType, today);
  const description =
    str('row_description', item.name, amountDescription(batch.quantity, batch.unit), status, location) +
    (batch.openedOn ? str('row_opened_suffix') : '');

  const down = (e: PointerEvent<HTMLDivElement>) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    drag.current = { x: e.clientX, y: e.clientY, horizontal: null, width: e.currentTarget.offsetWidth };
    moved.current = false;
  };
  const move = (e: PointerEvent<HTMLDivElement>) => {
    const d = drag.current;
    if (!d) return;
    const x = e.clientX - d.x;
    const y = e.clientY - d.y;
    if (d.horizontal === null && Math.hypot(x, y) > 10) {
      d.horizontal = Math.abs(x) > Math.abs(y);
      if (d.horizontal) e.currentTarget.setPointerCapture(e.pointerId);
    }
    if (d.horizontal) {
      moved.current = true;
      setDragging(true);
      setDx(x);
    }
  };
  const up = () => {
    const d = drag.current;
    drag.current = null;
    setDragging(false);
    if (!d?.horizontal) return;
    if (dx > d.width * SWIPE_THRESHOLD) actions.onUsedUp(batch);
    else if (dx < -d.width * SWIPE_THRESHOLD) actions.onThrowAway(batch);
    setDx(0);
  };

  return (
    <div className="sn-pantry-row" data-testid={`pantry_item_${batch.id}`}>
      {dx !== 0 && <SwipeBackground direction={dx > 0 ? 'usedUp' : 'throwAway'} />}
      <div
        className="sn-pantry-row__front"
        style={{ transform: dx ? `translateX(${dx}px)` : undefined, transition: dragging ? 'none' : undefined }}
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={up}
      >
        <button
          type="button"
          className="sn-pantry-row__main m3-state"
          aria-label={description}
          onClick={() => {
            if (!moved.current) onOpen(item);
          }}
        >
          <span className="sn-pantry-row__text">
            <NameLine name={item.name} low={item.isLow} out={false} pending={batch.pendingSync} />
            <span className="sn-pantry-row__details">
              <StatusChip expiry={batch.expiryDate} dateType={batch.dateType} today={today} />
              <LocationLabel location={batch.location} />
              {batch.openedOn && (
                <span className="sn-pantry-row__label">
                  <Icon name="round/lock_open" size={16} />
                  {str('pantry_opened')}
                </span>
              )}
            </span>
          </span>
          {amount && <span className="sn-pantry-row__amount t-title-medium">{amount}</span>}
        </button>
        <PurchaseMenu batch={batch} actions={actions} />
      </div>
    </div>
  );
}

function OutRow({ item, onOpen }: { item: OutItem; onOpen: (item: PantryItem) => void }) {
  const product = item.product;
  return (
    <div className="sn-pantry-row" data-testid={`pantry_item_out_${product.id}`}>
      <div className="sn-pantry-row__front">
        <button
          type="button"
          className="sn-pantry-row__main sn-pantry-row__main--out m3-state"
          aria-label={str('row_description_out', product.name)}
          onClick={() => onOpen(item)}
        >
          <span className="sn-pantry-row__text">
            <NameLine name={product.name} low={false} out pending={false} />
            <LocationLabel location={product.defaultLocation} />
          </span>
        </button>
      </div>
    </div>
  );
}

function NameLine({ name, low, out, pending }: { name: string; low: boolean; out: boolean; pending: boolean }) {
  return (
    <span className="sn-pantry-row__name">
      <span className="t-title-medium sn-pantry-row__name-text">{name}</span>
      {out ? <Badge text={str('badge_out')} /> : low ? <Badge text={str('badge_low')} /> : null}
      {pending && (
        <span className="c-on-surface-variant">
          <Icon name="round/cloud_off" size={16} label={str('pending_sync')} />
        </span>
      )}
    </span>
  );
}

function LocationLabel({ location }: { location: Location }) {
  return (
    <span className="sn-pantry-row__label">
      <Icon name={locationIcon(location)} size={16} />
      {locationLabel(location)}
    </span>
  );
}

/** Small pill such as Out or Low. */
export function Badge({ text }: { text: string }) {
  return <span className="sn-badge t-label-medium">{text}</span>;
}

/** Status chip: icon, text and the fixed status color, never color alone (DESIGN 6.4); VoiceOver gets the full wording. */
export function StatusChip({
  expiry,
  dateType,
  today,
}: {
  expiry: LocalDate | null;
  dateType: DateType;
  today: LocalDate;
}) {
  const status = Expiry.of(expiry, today);
  return (
    <span
      className={`sn-status-chip sn-status-chip--${tone(status)} t-label-large`}
      role="img"
      aria-label={statusText(expiry, dateType, today)}
      data-testid="status_chip"
    >
      <Icon name={statusIcon(status)} size={16} />
      {chipText(expiry, dateType, today)}
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
    <span className="sn-anchor">
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

function SwipeBackground({ direction }: { direction: 'usedUp' | 'throwAway' }) {
  const usedUp = direction === 'usedUp';
  return (
    <div className={`sn-swipe sn-swipe--${direction}`} aria-hidden>
      <Icon name={usedUp ? 'round/done' : 'round/delete_outline'} />
      <span className="t-label-large">{str(usedUp ? 'action_used_up' : 'action_throw_away')}</span>
    </div>
  );
}

/** "65 expired items" with Show or Hide, at the top of the list. */
export function ExpiredHeader({ count, shown, onToggle }: { count: number; shown: boolean; onToggle: () => void }) {
  return (
    <div className="sn-expired-header" data-testid="pantry_expired_header">
      <div className="sn-expired-header__title">
        <span className="sn-expired-header__icon">
          <Icon name="round/delete_outline" />
        </span>
        <span className="t-title-small">{plural('pantry_expired_count', count, count)}</span>
      </div>
      <div className="sn-expired-header__actions">
        <Button variant="text" onClick={onToggle} data-testid="pantry_expired_toggle">
          {str(shown ? 'pantry_expired_hide' : 'pantry_expired_show')}
        </Button>
      </div>
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
      onDismiss={onDismiss}
      actions={
        <Button variant="text" onClick={onDismiss}>
          {str('action_cancel')}
        </Button>
      }
    >
      <div role="radiogroup" className="sn-choices">
        {options.map((option) => (
          <label key={option} className="sn-choice t-body-large">
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
