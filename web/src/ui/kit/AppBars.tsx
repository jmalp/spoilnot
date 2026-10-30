import type { ReactNode } from 'react';
import type { IconName } from './Icon';
import { Icon } from './Icon';
import { ripple } from './ripple';

/**
 * A pinned TopAppBar: 64 px, surface, surfaceContainer once content scrolls under it ([scrolled]). Tabs use a
 * headlineSmall title, pushed screens titleLarge with a back button (TabScaffold, DetailScaffold).
 */
export function TopAppBar({
  title,
  titleStyle = 'title',
  navigation,
  actions,
  scrolled,
}: {
  title: ReactNode;
  titleStyle?: 'headline' | 'title';
  navigation?: ReactNode;
  actions?: ReactNode;
  scrolled?: boolean;
}) {
  return (
    <header className={`m3-top-app-bar ${scrolled ? 'm3-top-app-bar--scrolled' : ''}`}>
      {navigation && <div className="m3-top-app-bar__navigation">{navigation}</div>}
      <h1 className={`m3-top-app-bar__title ${titleStyle === 'headline' ? 't-headline-small' : 't-title-large'}`}>
        {title}
      </h1>
      {actions && <div className="m3-top-app-bar__actions">{actions}</div>}
    </header>
  );
}

export type NavigationItem<T extends string> = {
  id: T;
  label: string;
  icon: IconName;
  selectedIcon: IconName;
};

/** The bottom NavigationBar: filled icon and pill when selected, outlined icon otherwise, labels always shown. */
export function NavigationBar<T extends string>({
  items,
  selected,
  onSelect,
}: {
  items: NavigationItem<T>[];
  selected: T;
  onSelect: (id: T) => void;
}) {
  return (
    <nav className="m3-navigation-bar">
      {items.map((item) => {
        const isSelected = item.id === selected;
        return (
          <button
            key={item.id}
            type="button"
            className="m3-navigation-bar__item"
            aria-current={isSelected ? 'page' : undefined}
            data-testid={`tab_${item.id}`}
            onClick={() => onSelect(item.id)}
            onPointerDown={ripple}
          >
            <span className="m3-navigation-bar__indicator m3-state">
              <Icon name={isSelected ? item.selectedIcon : item.icon} />
            </span>
            <span className="m3-navigation-bar__label">{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
