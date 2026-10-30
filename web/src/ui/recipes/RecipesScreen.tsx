import { useEffect, useState } from 'react';
import { messageId } from '../../core/errors';
import { useObservable } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import { NameKey } from '../../domain/pantry/rules';
import { RecipeBox, type RecipeFilter, type RecipeQuery, type RecipeSort } from '../../domain/recipes/box';
import type { Recipe } from '../../domain/recipes/models';
import { FilterChip, Icon, IconButton, Menu, MenuItem, Progress, TextField, useSnackbars } from '../kit';
import { EmptyState, OfflineBanner, TabScaffold, useOnInnerScroll } from '../Scaffold';
import { RecipeCard } from './components';
import '../pantry/pantry.css';
import './recipes.css';

/**
 * The recipe box (DESIGN 5.5): search, All, Favorites and tag chips, sort, and the cards. Recipes are added in the
 * Android app (DESIGN 18.1), so there is no Add recipe button.
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
  const { recipes, recipeService, writer } = useServices();
  const snackbars = useSnackbars();
  const box = useObservable(recipes.box$, null);
  const onScroll = useOnInnerScroll();
  const actor = { uid: me.user.uid, name: me.displayName };

  useEffect(() => {
    const subscription = writer.errors$.subscribe((e) => snackbars.show(str(messageId(e))));
    return () => subscription.unsubscribe();
  }, [writer, snackbars]);

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
      {box?.fromCache && <OfflineBanner />}
      {box === null ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : box.recipes.length === 0 ? (
        <EmptyState icon="outlined/menu_book" message={str('web_recipes_empty')} />
      ) : (
        <>
          <div className="sn-pantry-search">
            <TextField
              label=""
              aria-label={str('recipe_search')}
              placeholder={str('recipe_search')}
              value={query.search}
              onValueChange={(search) => onQueryChange({ ...query, search })}
              type="search"
              enterKeyHint="search"
              data-testid="recipe_search"
              leading={
                <span className="sn-pantry-search__leading">
                  <Icon name="round/search" />
                </span>
              }
              trailing={
                query.search ? (
                  <IconButton
                    icon="round/clear"
                    label={str('action_clear_search')}
                    onClick={() => onQueryChange({ ...query, search: '' })}
                  />
                ) : undefined
              }
            />
          </div>
          <FilterRow
            selected={shownQuery.filter}
            tags={tags}
            onSelect={(f) => onQueryChange({ ...query, filter: f })}
          />
          {list.length === 0 && <p className="t-body-large sn-recipe-no-matches">{str('recipe_no_matches')}</p>}
          <div className="sn-recipe-grid" data-testid="recipe_grid" onScroll={onScroll}>
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

/** All, Favorites, then the user's tags. */
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
    [{ kind: 'all' }, str('recipe_filter_all')],
    [{ kind: 'favorites' }, str('recipe_filter_favorites')],
    ...tags.map((tag): [RecipeFilter, string] => [{ kind: 'tag', tag }, tag]),
  ];
  return (
    <div className="sn-filter-row sn-filter-row--plain">
      {chips.map(([filter, label]) => (
        <FilterChip
          key={label + filter.kind}
          label={label}
          selected={sameFilter(filter, selected)}
          onClick={() => onSelect(filter)}
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
