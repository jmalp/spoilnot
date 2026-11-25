// The Add to pantry sheet's draft (AddToPantryDrafts in the app; after v1, REL-08 on the web). What the person set
// on each line is kept by item id, apart from the list of checked items: when someone else checks off or unchecks an
// item, the edited lines keep what was typed, new lines start from their defaults, and lines that left the cart are
// neither shown nor added. Lines nobody touched follow their item and product. The draft lives in sessionStorage
// while the sheet is open (core/drafts.ts), so it also outlasts iOS reloading the page.
import type { GroceryItem } from '../../domain/grocery/models';
import { AddToPantryRules } from '../../domain/grocery/service';
import { LOCATIONS, UNITS, type Location, type PantryData, type QuantityUnit } from '../../domain/pantry/models';
import { isLocalDate, type LocalDate } from '../../domain/time';
import { formatQuantity } from '../pantry/labels';

/** What the person set on one line. */
export type EntryEdit = {
  quantity: string;
  unit: QuantityUnit;
  expiry: LocalDate | null;
  location: Location;
  skip: boolean;
};

/** The open sheet: the list it is for, the edited lines and the lines showing their editor. */
export type AddToPantryDraft = {
  listId: string;
  edits: Record<string, EntryEdit>;
  expanded: string[];
};

/** One line as shown: the item, its edit or its defaults, and whether its unit is the product's. */
export type EntryLine = {
  item: GroceryItem;
  value: EntryEdit;
  expanded: boolean;
  unitLocked: boolean;
};

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);

function editOf(raw: unknown): EntryEdit | null {
  if (!isRecord(raw)) return null;
  const { quantity, unit, expiry, location, skip } = raw;
  if (typeof quantity !== 'string' || typeof skip !== 'boolean') return null;
  if (!UNITS.includes(unit as QuantityUnit) || !LOCATIONS.includes(location as Location)) return null;
  const date = expiry === null ? null : isLocalDate(expiry) ? expiry : undefined;
  if (date === undefined) return null;
  return { quantity, unit: unit as QuantityUnit, expiry: date, location: location as Location, skip };
}

export const AddToPantryDrafts = {
  open: (listId: string): AddToPantryDraft => ({ listId, edits: {}, expanded: [] }),

  /** A saved draft, or null when it is not one (an older build's, or damaged); bad lines are dropped. */
  parse(raw: unknown): AddToPantryDraft | null {
    if (!isRecord(raw) || typeof raw.listId !== 'string' || !isRecord(raw.edits) || !Array.isArray(raw.expanded))
      return null;
    const edits: Record<string, EntryEdit> = {};
    for (const [id, e] of Object.entries(raw.edits)) {
      const edit = editOf(e);
      if (edit) edits[id] = edit;
    }
    return { listId: raw.listId, edits, expanded: raw.expanded.filter((id): id is string => typeof id === 'string') };
  },

  setEdit: (draft: AddToPantryDraft, itemId: string, edit: EntryEdit): AddToPantryDraft => ({
    ...draft,
    edits: { ...draft.edits, [itemId]: edit },
  }),

  setExpanded: (draft: AddToPantryDraft, itemIds: string[], open: boolean): AddToPantryDraft => ({
    ...draft,
    expanded: open
      ? [...draft.expanded, ...itemIds.filter((id) => !draft.expanded.includes(id))]
      : draft.expanded.filter((id) => !itemIds.includes(id)),
  }),

  /** The current [items] with the edits kept for them. */
  lines(items: GroceryItem[], pantry: PantryData, today: LocalDate, draft: AddToPantryDraft): EntryLine[] {
    return items.map((item) => {
      // The linked product, its unit while it has stock, and the item's amount only in that unit (LOGIC-03, 04).
      const d = AddToPantryRules.defaults(item, pantry, today);
      const value = draft.edits[item.id] ?? {
        quantity: d.quantity === null ? '' : formatQuantity(d.quantity),
        unit: d.unit,
        expiry: d.expiry,
        location: d.location,
        skip: false,
      };
      return {
        item,
        // A product with stock keeps its unit (DESIGN 2; LOGIC-04), also when it got stock meanwhile.
        value: d.unitLocked ? { ...value, unit: d.unit } : value,
        expanded: draft.expanded.includes(item.id),
        unitLocked: d.unitLocked,
      };
    });
  },
};
