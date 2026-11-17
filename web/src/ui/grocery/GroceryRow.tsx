import { memo, useState } from 'react';
import { str } from '../../core/i18n';
import type { GroceryItem } from '../../domain/grocery/models';
import { Checkbox, Icon, IconButton, LabelChip, Menu, MenuItem, type IconName } from '../kit';
import { amountDescription, amountText } from '../pantry/labels';
import { useSwipe } from '../swipe';
import './grocery.css';

/**
 * One item as a row of its aisle's group (GroceryRow in the app; DESIGN 19.5). A tap checks it off and it moves to In
 * cart, where its row is a step darker and struck through. After v1 (UX-10) a swipe right checks it off too (or puts
 * it back from the cart) and a swipe left deletes it with Undo, each shown under the row in its shape; the tap and the
 * ⋮ menu do the same (DESIGN 6.8). Each source is a label chip with its own icon, so Added automatically and Recipe
 * look different without relying on color.
 *
 * Memoized (review WEB-13): it takes only its own facts and handlers that keep their identity, so another phone's
 * change to some other item doesn't redraw it.
 */
export const GroceryRow = memo(function GroceryRow({
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
  const swipe = useSwipe((direction) => (direction === 'right' ? onToggle(item) : onDelete(item)));
  // What the app did by itself says so (after v1, UX-08); Back in stock replaces it: one line, "Back in stock · Remove".
  const source =
    item.source === 'low_stock' && !backInStock
      ? (['grocery_source_low_stock', 'round/trending_down'] as const)
      : item.source === 'recipe'
        ? (['grocery_source_recipe', 'round/restaurant_menu'] as const)
        : item.source === 'meal_plan'
          ? (['grocery_source_meal_plan', 'round/restaurant_menu'] as const)
          : null;
  // One sentence for VoiceOver, as Pantry's rows (after v1, review A11Y-07): "Bananas, 6 pieces, Added automatically,
  // added by Sam, ripe ones", with the checkbox's state. Remove and ⋮ stay their own stops.
  const description = [
    item.name,
    item.quantity !== null ? amountDescription(item.quantity, item.unit) : null,
    item.source === 'low_stock'
      ? backInStock
        ? null
        : str('grocery_source_low_stock')
      : item.source === 'recipe'
        ? str('grocery_source_recipe_description')
        : item.source === 'meal_plan'
          ? str('grocery_source_meal_plan_description')
          : null,
    backInStock ? str('grocery_back_in_stock') : null,
    addedBy ? str('grocery_added_by', addedBy) : null,
    item.note,
    item.pendingSync ? str('pending_sync') : null,
  ]
    .filter((part) => part)
    .join(', ');
  return (
    <div
      className={`sn-segment sn-grocery-row ${item.checked ? 'sn-grocery-row--cart' : ''} ${highlight ? 'sn-remote-highlight' : ''}`}
      data-testid={`grocery_item_${item.id}`}
      data-remote={highlight || undefined}
    >
      {swipe.dx !== 0 && <GrocerySwipeBackground right={swipe.dx > 0} checked={item.checked} />}
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
          <Checkbox checked={item.checked} aria-label={description} onCheckedChange={() => onToggle(item)} />
          <span className="sn-grocery-row__text">
            <span className="sn-grocery-row__name" aria-hidden>
              <span className={`t-row-title ${item.checked ? 'sn-grocery-row__checked' : ''}`}>{item.name}</span>
              {item.quantity !== null && (
                <span className="sn-grocery-row__amount c-on-surface-variant">
                  × {amountText(item.quantity, item.unit)}
                </span>
              )}
              {item.pendingSync && (
                <span className="c-on-surface-variant">
                  <Icon name="round/cloud_off" size={16} label={str('pending_sync')} />
                </span>
              )}
            </span>
            {(source || backInStock || addedBy || item.note) && (
              <span className="sn-grocery-row__details">
                {source && <Chip text={str(source[0])} icon={source[1]} />}
                {backInStock && (
                  <span className="sn-grocery-row__back">
                    <Chip text={str('grocery_back_in_stock')} icon="round/check_circle" person />
                    <span className="t-label-large c-on-surface-variant" aria-hidden>
                      ·
                    </span>
                    <button
                      type="button"
                      className="sn-grocery-row__remove t-button-medium m3-state"
                      aria-label={str('grocery_back_in_stock_remove', item.name)}
                      onClick={() => onDelete(item)}
                    >
                      {str('grocery_remove')}
                    </button>
                  </span>
                )}
                {addedBy && (
                  <span className="t-row-supporting c-on-surface-variant" aria-hidden>
                    {str('grocery_added_by', addedBy)}
                  </span>
                )}
                {item.note && (
                  <span className="t-row-supporting c-on-surface-variant" aria-hidden>
                    {item.note}
                  </span>
                )}
              </span>
            )}
          </span>
        </label>
        <ItemMenu item={item} onEdit={onEdit} onDelete={onDelete} />
      </div>
    </div>
  );
});

/**
 * What a swipe does, under the row as it moves (after v1, UX-10): right checks off (or puts back), left deletes. Icon
 * and word, never color alone (DESIGN 6.1).
 */
function GrocerySwipeBackground({ right, checked }: { right: boolean; checked: boolean }) {
  const [icon, label]: [IconName, string] = !right
    ? ['round/delete_outline', str('action_delete')]
    : checked
      ? ['round/undo', str('grocery_swipe_uncheck')]
      : ['round/check', str('grocery_swipe_check')];
  return (
    <div
      className={`sn-grocery-swipe ${right ? 'sn-grocery-swipe--check' : 'sn-grocery-swipe--delete'}`}
      data-testid="grocery_swipe"
      aria-hidden
    >
      <Icon name={icon} />
      <span className="t-button-medium">{label}</span>
    </div>
  );
}

/**
 * Where an item came from as a label chip (DESIGN 19.10), or Back in stock in the person chip's colors ([person]).
 * VoiceOver reads it in the row's sentence, on the checkbox, so it is hidden here.
 */
function Chip({ text, icon, person = false }: { text: string; icon: IconName; person?: boolean }) {
  return (
    <span aria-hidden>
      <LabelChip text={text} icon={icon} kind={person ? 'person' : 'source'} />
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
    <span className="sn-anchor sn-grocery-row__menu">
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
