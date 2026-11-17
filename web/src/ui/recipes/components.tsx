import { useState, type ReactNode } from 'react';
import { plural, str } from '../../core/i18n';
import type { Recipe } from '../../domain/recipes/models';
import { NameKey } from '../../domain/pantry/rules';
import { SourceUrl } from '../../domain/recipes/text';
import { Checkbox, Icon, ListGroup, RoundIconButton, type IconName } from '../kit';
import './recipes.css';

/** "1 hr 15 min", or null when the time is unknown. */
export function durationText(minutes: number | null): string | null {
  if (minutes === null) return null;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return str('recipe_minutes', m);
  if (m === 0) return str('recipe_hours', h);
  return str('recipe_hours_minutes', h, m);
}

export function servingsLabel(servings: number | null, text: string | null): string | null {
  if (servings !== null) return plural('recipe_servings', servings, servings);
  return text;
}

/** Java's String.hashCode, so a title keeps the same placeholder color as in the app. */
function hashCode(text: string): number {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (Math.imul(31, h) + text.charCodeAt(i)) | 0;
  return h;
}

const TONES = ['primary', 'secondary', 'tertiary'] as const;

/**
 * A recipe's photo (DESIGN 11.6: remote images, placeholder on failure). Without one (DESIGN 19.12) a block in one of
 * the theme's container colors with the recipe's first letter in a leaf of the matching strong color, chosen by the
 * title so each recipe keeps its color, as in the app. [size] 'header' is a recipe's hero: a bigger leaf, low in the
 * block.
 */
export function RecipeImage({
  url,
  title,
  className,
  size = 'card',
}: {
  url: string | null;
  title: string | null;
  className?: string;
  size?: 'card' | 'header';
}) {
  const [failed, setFailed] = useState(false);
  const initial = [...(title?.trim() ?? '')].find((c) => /[\p{L}\p{N}]/u.test(c))?.toUpperCase() ?? null;
  const tone = initial === null ? 'none' : TONES[((hashCode(NameKey.of(title!)) % 3) + 3) % 3];
  return (
    <div className={`sn-recipe-image sn-recipe-image--${tone} sn-recipe-image--${size} ${className ?? ''}`} aria-hidden>
      {url && !failed && SourceUrl.isWebUrl(url) ? (
        <img src={url} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} />
      ) : initial !== null ? (
        <span className="sn-recipe-leaf">{initial}</span>
      ) : (
        <Icon name="outlined/menu_book" size={40} />
      )}
    </div>
  );
}

/**
 * A card in the recipe box (DESIGN 5.5, 19.8): a plain card with the photo or the letter in a leaf, the title and the
 * total time, and the favorite star in a round button over the picture.
 */
export function RecipeCard({
  recipe,
  onOpen,
  onToggleFavorite,
}: {
  recipe: Recipe;
  onOpen: () => void;
  onToggleFavorite: () => void;
}) {
  const time = durationText(recipe.content.totalMinutes);
  return (
    <div className="sn-recipe-card" data-testid="recipe_card">
      <button
        type="button"
        className="sn-recipe-card__open m3-state"
        onClick={onOpen}
        // "Spinach and feta omelette, Total 15 min": the title and the time as one sentence (after v1, review A11Y-07).
        aria-label={time ? `${recipe.content.title}, ${str('recipe_total_time', time)}` : recipe.content.title}
      >
        <RecipeImage url={recipe.content.imageUrl} title={recipe.content.title} className="sn-recipe-card__image" />
        <span className="sn-recipe-card__text">
          <span className="t-row-title sn-recipe-card__title">{recipe.content.title}</span>
          {/* The time row's space is kept, so cards side by side match (after v1). */}
          <span
            className="sn-recipe-card__time t-row-supporting c-on-surface-variant"
            aria-label={time ? str('recipe_total_time', time) : undefined}
          >
            {time && (
              <>
                <Icon name="round/schedule" size={16} />
                {time}
              </>
            )}
          </span>
        </span>
      </button>
      <FavoriteToggle favorite={recipe.favorite} onToggle={onToggleFavorite} className="sn-recipe-card__star" />
    </div>
  );
}

/**
 * The favorite star in a round button (DESIGN 19.7): one name and its state ("Favorite, selected"), as a label that
 * flipped with the state said it twice (review A11Y-08).
 */
export function FavoriteToggle({
  favorite,
  onToggle,
  className,
  testId,
}: {
  favorite: boolean;
  onToggle: () => void;
  className?: string;
  testId?: string;
}) {
  return (
    <RoundIconButton
      icon={favorite ? 'round/star' : 'round/star_border'}
      label={str('recipe_favorite_toggle')}
      aria-pressed={favorite}
      className={className}
      data-testid={testId}
      onClick={onToggle}
    />
  );
}

/**
 * Lines in their groups (DESIGN 19.5): each group under its heading, its lines as rows of a group. [lines] are
 * (group, line); [row] draws a line with its index in [lines]. [field] draws the rows on the field color (a sheet).
 */
export function GroupedRows<T>({
  lines,
  row,
  field = false,
}: {
  lines: [string | null, T][];
  row: (index: number, line: T) => ReactNode;
  field?: boolean;
}) {
  const groups: { group: string | null; items: [number, T][] }[] = [];
  lines.forEach(([group, line], index) => {
    const last = groups[groups.length - 1];
    if (!last || last.group !== group) groups.push({ group, items: [] });
    groups[groups.length - 1]!.items.push([index, line]);
  });
  return (
    <div className="sn-recipe-groups">
      {groups.map(({ group, items }, i) => (
        <div key={i} className="sn-recipe-groups__group">
          {group !== null && <h3 className="t-group-heading c-primary sn-recipe-group">{group}</h3>}
          <ListGroup className={field ? 'sn-group--field' : undefined}>
            {items.map(([index, line]) => row(index, line))}
          </ListGroup>
        </div>
      ))}
    </div>
  );
}

/**
 * Ingredient lines as a checklist (DESIGN 19.5): rows of a group with a checkbox, under each ingredient group's
 * heading; tapping a line ticks it off. [field] on a sheet.
 */
export function IngredientChecklist({
  lines,
  checked,
  onToggle,
  field = false,
}: {
  lines: [string | null, string][];
  checked: ReadonlySet<number>;
  onToggle: (index: number) => void;
  field?: boolean;
}) {
  return (
    <GroupedRows
      lines={lines}
      field={field}
      row={(index, line) => (
        <label
          key={index}
          className={`sn-segment sn-checklist__line m3-state ${checked.has(index) ? 'sn-checklist__line--done' : ''}`}
        >
          <Checkbox checked={checked.has(index)} onCheckedChange={() => onToggle(index)} />
          <span className="t-body-large sn-checklist__text">{line}</span>
        </label>
      )}
    />
  );
}

/**
 * The steps (DESIGN 19.12): rows of a group, each with its number in a leaf, under each step group's heading. [steps]
 * are (group, text); each row is read as one, "1, Whisk the eggs".
 */
export function StepList({ steps }: { steps: [string | null, string][] }) {
  return (
    <GroupedRows
      lines={steps}
      row={(index, text) => (
        <div key={index} className="sn-segment sn-recipe-step">
          <StepLeaf number={index + 1} />
          <span className="t-body-large sn-recipe-step__text">{text}</span>
        </div>
      )}
    />
  );
}

/** A step's number in a leaf (DESIGN 19.3): 34 px in the list, 64 px ([large]) in cook mode. */
export function StepLeaf({ number, large = false }: { number: number; large?: boolean }) {
  return <span className={`sn-step-leaf ${large ? 'sn-step-leaf--large' : ''}`}>{number}</span>;
}

/** A fact about a recipe as a pill on the row color (DESIGN 19.12): the total time, the servings. */
export function InfoPill({ label, icon }: { label: string; icon: IconName }) {
  return (
    <span className="sn-info-pill t-button-medium">
      <Icon name={icon} size={18} />
      {label}
    </span>
  );
}

/** A section's heading on a recipe (Ingredients, Steps, Notes), with what sits beside it ([children]). */
export function SectionTitle({ text, children }: { text: string; children?: React.ReactNode }) {
  return (
    <div className="sn-recipe-section">
      <h2 className="t-section-title">{text}</h2>
      {children}
    </div>
  );
}
