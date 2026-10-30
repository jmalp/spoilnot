import { useState } from 'react';
import { plural, str } from '../../core/i18n';
import type { Recipe } from '../../domain/recipes/models';
import { NameKey } from '../../domain/pantry/rules';
import { SourceUrl } from '../../domain/recipes/text';
import { Checkbox, Icon } from '../kit';
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
 * A recipe's photo; without one (or when it fails) the recipe's first letter on one of the theme's container colors,
 * chosen by the title so each card keeps its color (DESIGN 11.6: remote images, placeholder on failure).
 */
export function RecipeImage({
  url,
  title,
  className,
}: {
  url: string | null;
  title: string | null;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);
  const initial = [...(title?.trim() ?? '')].find((c) => /[\p{L}\p{N}]/u.test(c))?.toUpperCase() ?? null;
  const tone = initial === null ? 'none' : TONES[((hashCode(NameKey.of(title!)) % 3) + 3) % 3];
  return (
    <div className={`sn-recipe-image sn-recipe-image--${tone} ${className ?? ''}`} aria-hidden>
      {url && !failed && SourceUrl.isWebUrl(url) ? (
        <img src={url} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} />
      ) : initial !== null ? (
        <span className="t-display-medium">{initial}</span>
      ) : (
        <Icon name="outlined/menu_book" size={40} />
      )}
    </div>
  );
}

/** A card in the recipe box: image, title, total time and a favorite star (DESIGN 5.5). */
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
    <div className="m3-card m3-card--filled sn-recipe-card" data-testid="recipe_card">
      <button
        type="button"
        className="sn-recipe-card__open m3-state"
        onClick={onOpen}
        aria-label={recipe.content.title}
      >
        <RecipeImage url={recipe.content.imageUrl} title={recipe.content.title} className="sn-recipe-card__image" />
        <span className="sn-recipe-card__text">
          <span className="t-title-medium sn-recipe-card__title">{recipe.content.title}</span>
          {/* The time row's space is kept, so cards side by side match (after v1). */}
          <span
            className="sn-recipe-card__time t-body-medium c-on-surface-variant"
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
      <button
        type="button"
        className={`sn-recipe-card__star ${recipe.favorite ? 'sn-recipe-card__star--on' : ''}`}
        aria-pressed={recipe.favorite}
        aria-label={str(recipe.favorite ? 'recipe_unfavorite' : 'recipe_favorite')}
        onClick={onToggleFavorite}
      >
        <Icon name={recipe.favorite ? 'round/star' : 'round/star_border'} />
      </button>
    </div>
  );
}

/** Ingredient lines with group headings; tapping a line ticks it off. */
export function IngredientChecklist({
  lines,
  checked,
  onToggle,
}: {
  lines: [string | null, string][];
  checked: ReadonlySet<number>;
  onToggle: (index: number) => void;
}) {
  return (
    <div className="sn-checklist">
      {lines.map(([g, line], index) => {
        const heading = groupHeading(g, index > 0 ? lines[index - 1]![0] : null);
        return (
          <div key={index}>
            {heading && <GroupTitle text={heading} />}
            <label className={`sn-checklist__line m3-state ${checked.has(index) ? 'sn-checklist__line--done' : ''}`}>
              <Checkbox checked={checked.has(index)} onCheckedChange={() => onToggle(index)} />
              <span className="t-body-large">{line}</span>
            </label>
          </div>
        );
      })}
    </div>
  );
}

/** A group's heading shows on its first line: when the group differs from the line before's. */
export const groupHeading = (group: string | null, before: string | null): string | null =>
  group !== null && group !== before ? group : null;

export function SectionTitle({ text, children }: { text: string; children?: React.ReactNode }) {
  return (
    <div className="sn-recipe-section">
      <h2 className="t-title-large">{text}</h2>
      {children}
    </div>
  );
}

export function GroupTitle({ text }: { text: string }) {
  return <h3 className="t-title-small c-primary sn-recipe-group">{text}</h3>;
}
