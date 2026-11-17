import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';
import { appliedTextScale, LARGE_TEXT_SCALE } from '../../theme/textScale';
import { Icon, type IconName } from './Icon';
import { IconButton } from './IconButton';
import { ripple } from './ripple';
import { Checkbox, Radio, Switch } from './Selection';
import './look.css';

// The shared pieces of the 2026-11 look (DESIGN 19; core/designsystem in the app). The web adds each piece with the
// first screen that uses it: the shell and Home brought the cards, groups and tiles; Pantry (step 3) the search
// field, the filter chip and the connected button group.

/**
 * A search field on a page (DESIGN 19.10; SearchField in the app): 56 px, fully round, on the row color without an
 * outline, a search icon at the start and a clear button ([clearLabel]) once something is typed. [placeholder] names
 * it for VoiceOver too.
 */
export function SearchField({
  value,
  onValueChange,
  placeholder,
  clearLabel,
  testId,
}: {
  value: string;
  onValueChange: (value: string) => void;
  placeholder: string;
  clearLabel: string;
  testId?: string;
}) {
  return (
    <PillField
      value={value}
      onValueChange={onValueChange}
      placeholder={placeholder}
      leadingIcon="round/search"
      type="search"
      enterKeyHint="search"
      data-testid={testId}
      trailing={
        value ? (
          <IconButton icon="round/clear" label={clearLabel} onClick={() => onValueChange('')} />
        ) : (
          // Keeps the height the clear button gives, so the field doesn't move when it appears.
          <span className="sn-search__spacer" />
        )
      }
    />
  );
}

/**
 * The round field of a page (DESIGN 19.10; PillField in the app), under the search field and Groceries' quick-add
 * field: 56 px, fully round, on the row color, a 2 px primary border while it has the keyboard, an optional
 * [leadingIcon], and [trailing] at the end. [placeholder] names it for VoiceOver too.
 */
export function PillField({
  value,
  onValueChange,
  placeholder,
  leadingIcon,
  trailing,
  ...rest
}: Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value' | 'placeholder'> & {
  value: string;
  onValueChange: (value: string) => void;
  placeholder: string;
  leadingIcon?: IconName;
  trailing?: ReactNode;
}) {
  return (
    <div className={`sn-search ${leadingIcon ? '' : 'sn-search--plain'}`}>
      {leadingIcon && <Icon name={leadingIcon} />}
      <input
        className="sn-search__input"
        aria-label={placeholder}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
        {...rest}
      />
      {trailing}
    </div>
  );
}

/**
 * A filter chip of the 2026-11 look (DESIGN 19.10; SpoilNotFilterChip in the app): 44 px, on the row color with 12 px
 * corners and an optional [icon]; chosen, fully round and primary with a check. A toggle button for VoiceOver, or with
 * [radio] one of a group where only one can be chosen.
 */
export function SpoilNotFilterChip({
  label,
  selected,
  icon,
  radio = false,
  className,
  ...rest
}: Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  label: string;
  selected: boolean;
  icon?: IconName;
  radio?: boolean;
}) {
  const leading = selected ? 'round/check' : icon;
  return (
    <button
      type="button"
      className={`sn-filter-chip m3-state t-button-medium ${leading ? 'sn-filter-chip--icon' : ''} ${className ?? ''}`}
      role={radio ? 'radio' : undefined}
      aria-checked={radio ? selected : undefined}
      aria-pressed={radio ? undefined : selected}
      onPointerDown={ripple}
      {...rest}
    >
      {leading && <Icon name={leading} size={18} />}
      {label}
    </button>
  );
}

/**
 * A single choice as joined buttons (DESIGN 19.9; ConnectedButtonGroup in the app): equal widths, 2 px apart, the
 * outside corners fully round and the joined ones 8 px; the chosen button is fully round and primary. From 1.5× text
 * it is a list of radio buttons, so labels are never cut off. Each is a radio button for VoiceOver either way; tapping
 * the chosen one calls [onPick] too, so a caller can clear the choice. [optionTestId] tags each for tests.
 */
export function ConnectedButtonGroup<T>({
  label,
  options,
  selected,
  optionLabel,
  onPick,
  optionKey,
  optionTestId,
}: {
  label: string;
  options: readonly T[];
  selected: T | null;
  optionLabel: (option: T) => string;
  onPick: (option: T) => void;
  optionKey: (option: T) => string;
  optionTestId?: (option: T) => string;
}) {
  const list = appliedTextScale() >= LARGE_TEXT_SCALE;
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={list ? 'sn-connected-list' : 'sn-connected'}
      data-testid={list ? 'connected_list' : 'connected_row'}
    >
      {options.map((option) => {
        const chosen = option === selected;
        return (
          <button
            key={optionKey(option)}
            type="button"
            role="radio"
            aria-checked={chosen}
            className={list ? 'sn-connected-list__option m3-state t-body-large' : 'sn-connected__button m3-state'}
            data-testid={optionTestId?.(option)}
            onPointerDown={ripple}
            onClick={() => onPick(option)}
          >
            {list && <span className="sn-radio-mark" aria-hidden />}
            <span className="sn-connected__label">{optionLabel(option)}</span>
          </button>
        );
      })}
    </div>
  );
}

/**
 * A group of rows (DESIGN 19.5; ListSegment in the app): rows 2 px apart, 20 px outside corners and 6 px where two
 * meet, on the row surface; [nested] for rows inside a highlight card (18 px, the nested row surface). The corners
 * come from each row's place in the group, so give each row the class sn-segment.
 */
export function ListGroup({
  nested = false,
  className,
  children,
  ...rest
}: HTMLAttributes<HTMLDivElement> & { nested?: boolean; children: ReactNode }) {
  return (
    <div className={`sn-group ${nested ? 'sn-group--nested' : ''} ${className ?? ''}`} {...rest}>
      {children}
    </div>
  );
}

/**
 * The leading tile of a row (DESIGN 19.6; StatusTile in the app): the status icon in its fixed color on that color at
 * 12% over the row, a neutral tile for no date. Decoration: the row says the status in words.
 */
export function StatusTile({ tone, icon, nested = false }: { tone: string; icon: IconName; nested?: boolean }) {
  return (
    <span className={`sn-status-tile sn-status-tile--${tone} ${nested ? 'sn-status-tile--nested' : ''}`} aria-hidden>
      <Icon name={icon} size={nested ? 22 : 24} />
    </span>
  );
}

/**
 * The one card a screen wants read first (DESIGN 19.8; HighlightCard in the app): primaryContainer, 28 px corners.
 */
export function HighlightCard({ className, children, ...rest }: HTMLAttributes<HTMLElement> & { children: ReactNode }) {
  return (
    <section className={`sn-highlight-card ${className ?? ''}`} {...rest}>
      {children}
    </section>
  );
}

/** A card that suggests something (DESIGN 19.8; AccentCard in the app): tertiaryContainer, 24 px corners. */
export function AccentCard({ className, children, ...rest }: HTMLAttributes<HTMLElement> & { children: ReactNode }) {
  return (
    <section className={`sn-accent-card ${className ?? ''}`} {...rest}>
      {children}
    </section>
  );
}

/** A plain card (DESIGN 19.8): the row surface with 24 px corners, no outline. */
export function PlainCard({ className, children, ...rest }: HTMLAttributes<HTMLElement> & { children: ReactNode }) {
  return (
    <section className={`sn-plain-card ${className ?? ''}`} {...rest}>
      {children}
    </section>
  );
}

/**
 * The leaf from the app icon holding an icon or a letter (DESIGN 19.3; LeafBadge in the app), 48 px by default. In
 * the accent card's colors, swapped; [highlight] takes the highlight card's (the account's initial in Settings).
 */
export function LeafBadge({
  icon,
  letter,
  size = 48,
  iconSize = 24,
  highlight = false,
}: {
  icon?: IconName;
  letter?: string;
  size?: number;
  iconSize?: number;
  highlight?: boolean;
}) {
  return (
    <span
      className={`sn-leaf-badge ${highlight ? 'sn-leaf-badge--highlight' : ''}`}
      style={size === 48 ? undefined : { width: size, height: size }}
      aria-hidden
    >
      {letter ? <span className="t-card-title">{letter}</span> : icon && <Icon name={icon} size={iconSize} />}
    </span>
  );
}

/** A row's leading icon on a tile (DESIGN 19.5; IconTile in the app): 40 px, secondaryContainer. Decoration. */
export function IconTile({ icon, letter }: { icon?: IconName; letter?: string }) {
  return (
    <span className="sn-icon-tile" aria-hidden>
      {letter ? (
        <span className="t-row-title sn-icon-tile__letter">{letter}</span>
      ) : (
        icon && <Icon name={icon} size={22} />
      )}
    </span>
  );
}

/** A row's name over its second line (RowLines in the app). */
function RowLines({ title, summary, summaryTestId }: { title: string; summary?: ReactNode; summaryTestId?: string }) {
  return (
    <span className="sn-row-lines">
      <span className="t-row-title">{title}</span>
      {summary && (
        <span className="t-row-supporting c-on-surface-variant sn-row-lines__summary" data-testid={summaryTestId}>
          {summary}
        </span>
      )}
    </span>
  );
}

/**
 * A row of a group that opens a page or does one thing (DESIGN 19.5; ActionRow in the app): the [icon] on a tile, the
 * [title] over its [summary], and a chevron when it opens a page ([opensPage]). The whole row is one button, read
 * with both lines. Put it in a [ListGroup].
 */
export function ActionRow({
  icon,
  title,
  summary,
  onClick,
  opensPage = false,
  disabled = false,
  testId,
  summaryTestId,
}: {
  icon: IconName;
  title: string;
  summary?: ReactNode;
  onClick: () => void;
  opensPage?: boolean;
  disabled?: boolean;
  testId?: string;
  summaryTestId?: string;
}) {
  return (
    <button
      type="button"
      className={`sn-segment sn-action-row m3-state ${summary ? '' : 'sn-action-row--one-line'}`}
      disabled={disabled}
      data-testid={testId}
      onPointerDown={ripple}
      onClick={onClick}
    >
      <IconTile icon={icon} />
      <RowLines title={title} summary={summary} summaryTestId={summaryTestId} />
      {opensPage && (
        <span className="c-on-surface-variant sn-action-row__chevron" aria-hidden>
          <Icon name="round/keyboard_arrow_right" />
        </span>
      )}
    </button>
  );
}

/**
 * A row with a switch at its end (DESIGN 19.5; SwitchRow in the app): the [icon] on a tile, the [title] over its
 * [summary]. The whole row is the switch's label, so a tap anywhere turns it, and VoiceOver reads both lines and its
 * state. Put it in a [ListGroup].
 */
export function SwitchRow({
  icon,
  title,
  summary,
  checked,
  onCheckedChange,
  testId,
}: {
  icon: IconName;
  title: string;
  summary?: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  testId?: string;
}) {
  return (
    <label className="sn-segment sn-action-row sn-action-row--switch" data-testid={testId}>
      <IconTile icon={icon} />
      <RowLines title={title} summary={summary} />
      <Switch checked={checked} onCheckedChange={onCheckedChange} />
    </label>
  );
}

/**
 * A one-line row of a group with a checkbox, or a radio button with [radioName], at its start (Notifications' stages
 * and expired food): the whole row is the control's label.
 */
export function ChoiceRow({
  label,
  checked,
  onCheckedChange,
  radioName,
}: {
  label: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  radioName?: string;
}) {
  return (
    <label className="sn-segment sn-choice-row">
      {radioName ? (
        <Radio name={radioName} checked={checked} onCheckedChange={onCheckedChange} />
      ) : (
        <Checkbox checked={checked} onCheckedChange={onCheckedChange} />
      )}
      <span className="t-row-title">{label}</span>
    </label>
  );
}

/** The heading over a group of rows (DESIGN 19.5; GroupHeading in the app): primary, a heading for VoiceOver. */
export function GroupHeading({ text, id }: { text: string; id?: string }) {
  return (
    <h2 className="t-group-heading sn-group-heading" id={id}>
      {text}
    </h2>
  );
}

/**
 * A small label on a row (DESIGN 19.10; LabelChip in the app): where an item came from, or who (a person). A label,
 * not a control.
 */
export function LabelChip({
  text,
  icon,
  kind = 'source',
}: {
  text: string;
  icon?: IconName;
  kind?: 'source' | 'person';
}) {
  return (
    <span className={`sn-label-chip sn-label-chip--${kind} t-chip-label`}>
      {icon && <Icon name={icon} size={14} />}
      {text}
    </span>
  );
}

/** − or + of a stepper: 56 px, secondaryContainer, round outside and 8 px where it joins the value (DESIGN 19.9). */
export function StepButton({
  icon,
  label,
  start = false,
  disabled = false,
  onClick,
}: {
  icon: 'round/remove' | 'round/add';
  label: string;
  start?: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={`sn-step-button m3-state ${start ? 'sn-step-button--start' : 'sn-step-button--end'}`}
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
    >
      <Icon name={icon} />
    </button>
  );
}

/**
 * A stepper for a count that isn't typed (DESIGN 19.9; CountStepper in the app): − and + joined to the [value] shown
 * between them on the field color, such as how many pieces to open or a meal's servings. [valueTestId] tags the value.
 * [announce] has VoiceOver say the value when it changes, where nothing else on screen says it (a recipe's servings).
 */
export function CountStepper({
  value,
  minusLabel,
  plusLabel,
  onMinus,
  onPlus,
  minusDisabled = false,
  plusDisabled = false,
  valueTestId,
  announce = false,
}: {
  value: string;
  minusLabel: string;
  plusLabel: string;
  onMinus: () => void;
  onPlus: () => void;
  minusDisabled?: boolean;
  plusDisabled?: boolean;
  valueTestId?: string;
  announce?: boolean;
}) {
  return (
    <div className="sn-joined-stepper">
      <StepButton icon="round/remove" label={minusLabel} start disabled={minusDisabled} onClick={onMinus} />
      <span
        className="sn-joined-stepper__count t-field-text"
        data-testid={valueTestId}
        aria-live={announce ? 'polite' : undefined}
      >
        {value}
      </span>
      <StepButton icon="round/add" label={plusLabel} disabled={plusDisabled} onClick={onPlus} />
    </div>
  );
}

/**
 * A round icon button that sits over a header or a picture (DESIGN 19.7, 19.9; RoundIconButton in the app): 48 px on
 * the row color, so it stands out from whatever is under it. [label] names it; with aria-pressed it is a toggle, such
 * as a recipe's star, primary while on.
 */
export function RoundIconButton({
  icon,
  label,
  className,
  ...rest
}: Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & { icon: IconName; label: string }) {
  return (
    <button
      type="button"
      className={`sn-round-button m3-state ${className ?? ''}`}
      aria-label={label}
      title={label}
      onPointerDown={ripple}
      {...rest}
    >
      <Icon name={icon} />
    </button>
  );
}
