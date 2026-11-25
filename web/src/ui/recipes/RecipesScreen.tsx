import { useState } from 'react';
import { messageId } from '../../core/errors';
import { useObservable } from '../../core/hooks';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import { NameKey } from '../../domain/pantry/rules';
import { RecipeBox, type RecipeFilter, type RecipeQuery, type RecipeSort } from '../../domain/recipes/box';
import type { Recipe } from '../../domain/recipes/models';
import { Icon, IconButton, Menu, MenuItem, Progress, SearchField, SpoilNotFilterChip, useSnackbars } from '../kit';
import { EmptyState, LoadError, OfflineBanner, TabScaffold } from '../Scaffold';
import { RecipeCard } from './components';
import '../pantry/pantry.css';
import './recipes.css';

/**
 * The recipe box (DESIGN 5.5) in the 2026-11 look (19.7, 19.12): how many recipes above the title, the round search
 * field, Favorites and tag chips, sort, and the cards with the letter in a leaf. Recipes are added in the Android app
 * (DESIGN 18.1), so there is no Add recipe button.
 */
export function RecipesScreen({
  onOpenSettings,
  joinRequests,
  query,
  onQueryChange,
  onOpenRecipe,
}: {
  onOpenSettings: () => void;
  joinRequests: number;
  /** Kept by the shell, so search and chips survive leaving the tab, as the app's view model keeps them. */
  query: RecipeQuery;
  onQueryChange: (query: RecipeQuery) => void;
  onOpenRecipe: (id: string) => void;
}) {
  const me = useHouseholdSession();
  const { recipes, recipeService } = useServices();
  const snackbars = useSnackbars();
  const box = useObservable(recipes.box$, null);
  const loadFailed = useObservable(recipes.load.failed$, false);
  const actor = { uid: me.user.uid, name: me.displayName };

  const tags = box ? RecipeBox.tags(box.recipes) : [];
  // A tag chip whose last recipe was deleted falls back to All.
  const filter = query.filter;
  const stale = filter.kind === 'tag' && !tags.some((t) => NameKey.of(t) === NameKey.of(filter.tag));
  const shownQuery: RecipeQuery = stale ? { ...query, filter: { kind: 'all' } } : query;
  const list = box ? RecipeBox.list(box.recipes, shownQuery) : [];
  const toggleFavorite = (recipe: Recipe) =>
    void recipeService
      .setFavorite(me.householdId, actor, recipe, !recipe.favorite)
      .catch((e: unknown) => snackbars.show(str(messageId(e))));

  return (
    <TabScaffold
      title={str('tab_recipes')}
      // How many recipes the box holds (DESIGN 19.7).
      eyebrow={
        box && box.recipes.length > 0
          ? plural('recipe_eyebrow_count', box.recipes.length, box.recipes.length)
          : undefined
      }
      onOpenSettings={onOpenSettings}
      joinRequests={joinRequests}
      innerScroll
      testId="screen_recipes"
      actions={
        box && box.recipes.length > 0 ? (
          <SortMenu sort={query.sort} onSort={(sort) => onQueryChange({ ...query, sort })} />
        ) : undefined
      }
    >
      {loadFailed ? <LoadError onRetry={() => recipes.load.retry()} /> : <OfflineBanner offline={!!box?.fromCache} />}
      {box === null ? (
        <div className="sn-centered">{!loadFailed && <Progress />}</div>
      ) : box.recipes.length === 0 ? (
        <EmptyState icon="outlined/menu_book" message={str('web_recipes_empty')} />
      ) : (
        <>
          {/* The page's round search field and the chips on the page (DESIGN 19.10), as on Pantry. */}
          <div className="sn-pantry-search sn-pantry-search--look">
            <SearchField
              value={query.search}
              onValueChange={(search) => onQueryChange({ ...query, search })}
              placeholder={str('recipe_search')}
              clearLabel={str('action_clear_search')}
              testId="recipe_search"
            />
          </div>
          <FilterRow
            selected={shownQuery.filter}
            tags={tags}
            onSelect={(f) => onQueryChange({ ...query, filter: f })}
          />
          {list.length === 0 && <p className="t-body-large sn-recipe-no-matches">{str('recipe_no_matches')}</p>}
          <div className="sn-recipe-grid" data-testid="recipe_grid">
            {list.map((recipe) => (
              <RecipeCard
                key={recipe.id}
                recipe={recipe}
                onOpen={() => onOpenRecipe(recipe.id)}
                onToggleFavorite={() => toggleFavorite(recipe)}
              />
            ))}
          </div>
        </>
      )}
    </TabScaffold>
  );
}

const sameFilter = (a: RecipeFilter, b: RecipeFilter) =>
  a.kind === b.kind && (a.kind !== 'tag' || (b.kind === 'tag' && a.tag === b.tag));

/**
 * Favorites, then the user's tags (DESIGN 5.5). No All chip (after v1, R16), as on Pantry: everything is the default,
 * and tapping the chosen chip again goes back to it.
 */
function FilterRow({
  selected,
  tags,
  onSelect,
}: {
  selected: RecipeFilter;
  tags: string[];
  onSelect: (filter: RecipeFilter) => void;
}) {
  const chips: [RecipeFilter, string][] = [
    [{ kind: 'favorites' }, str('recipe_filter_favorites')],
    ...tags.map((tag): [RecipeFilter, string] => [{ kind: 'tag', tag }, tag]),
  ];
  return (
    <div className="sn-filter-row sn-filter-row--look">
      {chips.map(([filter, label]) => (
        <SpoilNotFilterChip
          key={label + filter.kind}
          label={label}
          selected={sameFilter(filter, selected)}
          // Favorites keeps its star, as the cards show it (DESIGN 19.10).
          icon={filter.kind === 'favorites' ? 'round/star_border' : undefined}
          onClick={() => onSelect(sameFilter(filter, selected) ? { kind: 'all' } : filter)}
        />
      ))}
    </div>
  );
}

function SortMenu({ sort, onSort }: { sort: RecipeSort; onSort: (sort: RecipeSort) => void }) {
  const [open, setOpen] = useState(false);
  const options: [RecipeSort, string][] = [
    ['recent', str('recipe_sort_recent')],
    ['aToZ', str('recipe_sort_az')],
  ];
  return (
    <span className="sn-anchor">
      <IconButton icon="round/sort" label={str('recipe_sort')} onClick={() => setOpen(true)} />
      <Menu open={open} onDismiss={() => setOpen(false)}>
        {options.map(([option, label]) => (
          <MenuItem
            key={option}
            label={label}
            leading={
              option === sort ? (
                <Icon name="round/check" label={str('recipe_sort_selected')} />
              ) : (
                <span className="sn-menu-spacer" />
              )
            }
            onClick={() => {
              onSort(option);
              setOpen(false);
            }}
          />
        ))}
      </Menu>
    </span>
  );
}
