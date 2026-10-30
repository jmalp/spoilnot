import { useState } from 'react';
import { str } from '../../core/i18n';
import type { GroceryItem } from '../../domain/grocery/models';
import { Checkbox, Icon, IconButton, Menu, MenuItem, type IconName } from '../kit';
import { amountText } from '../pantry/labels';
import { useSwipe } from '../swipe';
import './grocery.css';

/**
 * One item (GroceryRow in the app). A tap checks it off and it moves to In cart; a swipe either way deletes it, with
 * Undo. Each source has its own icon, so Low stock and Recipe look different without relying on color.
 */
export function GroceryRow({
  item,
  backInStock,
  addedBy,
  highlight,
  onToggle,
  onDelete,
  onEdit,
}: {
  item: GroceryItem;
  backInStock: boolean;
  /** The member who typed it in, when that was someone else. */
  addedBy: string | null;
  highlight: boolean;
  onToggle: (item: GroceryItem) => void;
  onDelete: (item: GroceryItem) => void;
  onEdit: (item: GroceryItem) => void;
}) {
  const swipe = useSwipe(() => onDelete(item));
  const source =
    item.source === 'low_stock'
      ? (['grocery_source_low_stock', 'round/trending_down'] as const)
      : item.source === 'recipe'
        ? (['grocery_source_recipe', 'round/restaurant_menu'] as const)
        : item.source === 'meal_plan'
          ? (['grocery_source_meal_plan', 'round/restaurant_menu'] as const)
          : null;
  return (
    <div
      className={`sn-grocery-row ${highlight ? 'sn-remote-highlight' : ''}`}
      data-testid={`grocery_item_${item.id}`}
      data-remote={highlight || undefined}
    >
      {swipe.dx !== 0 && (
        <div className={`sn-grocery-swipe ${swipe.dx > 0 ? '' : 'sn-grocery-swipe--end'}`} aria-hidden>
          <Icon name="round/delete_outline" />
        </div>
      )}
      <div
        className="sn-grocery-row__front"
        style={swipe.style}
        {...swipe.handlers}
        onClickCapture={(e) => {
          // A drag is not a tap.
          if (swipe.wasDragged()) e.preventDefault();
        }}
      >
        <label className="sn-grocery-row__main m3-state">
          <Checkbox checked={item.checked} onCheckedChange={() => onToggle(item)} />
          <span className="sn-grocery-row__text">
            <span className="sn-grocery-row__name">
              <span className={`t-body-large ${item.checked ? 'sn-grocery-row__checked' : ''}`}>{item.name}</span>
              {item.quantity !== null && (
                <span className="t-body-medium c-on-surface-variant">× {amountText(item.quantity, item.unit)}</span>
              )}
              {item.pendingSync && (
                <span className="c-on-surface-variant">
                  <Icon name="round/cloud_off" size={16} label={str('pending_sync')} />
                </span>
              )}
            </span>
            {(source || backInStock || addedBy || item.note) && (
              <span className="sn-grocery-row__details">
                {source && <SourceBadge text={str(source[0])} icon={source[1]} />}
                {/* On the same line (after v1): it used to add a tall second line under the badge. */}
                {backInStock && (
                  <>
                    <SourceBadge text={str('grocery_back_in_stock')} icon="round/check_circle" good />
                    <button
                      type="button"
                      className="sn-grocery-row__remove t-label-large m3-state"
                      onClick={() => onDelete(item)}
                    >
                      {str('grocery_remove')}
                    </button>
                  </>
                )}
                {addedBy && (
                  <span className="t-body-small c-on-surface-variant">{str('grocery_added_by', addedBy)}</span>
                )}
                {item.note && <span className="t-body-small c-on-surface-variant">{item.note}</span>}
              </span>
            )}
          </span>
        </label>
        <ItemMenu item={item} onEdit={onEdit} onDelete={onDelete} />
      </div>
    </div>
  );
}

/** A small label with an icon: tertiary for where an item came from, secondary for good news such as Back in stock. */
function SourceBadge({ text, icon, good = false }: { text: string; icon: IconName; good?: boolean }) {
  return (
    <span className={`sn-source-badge t-label-medium ${good ? 'sn-source-badge--good' : ''}`}>
      <Icon name={icon} size={14} />
      {text}
    </span>
  );
}

function ItemMenu({
  item,
  onEdit,
  onDelete,
}: {
  item: GroceryItem;
  onEdit: (item: GroceryItem) => void;
  onDelete: (item: GroceryItem) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <span className="sn-anchor">
      <IconButton
        icon="round/more_vert"
        label={str('grocery_item_options', item.name)}
        onClick={() => setOpen(true)}
        data-testid={`menu_${item.id}`}
      />
      <Menu open={open} onDismiss={() => setOpen(false)}>
        <MenuItem
          label={str('action_edit')}
          onClick={() => {
            setOpen(false);
            onEdit(item);
          }}
        />
        <MenuItem
          label={str('action_delete')}
          onClick={() => {
            setOpen(false);
            onDelete(item);
          }}
        />
      </Menu>
    </span>
  );
}
