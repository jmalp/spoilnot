import { useId } from 'react';
import { appliedTextScale } from '../../theme/textScale';
import { Icon } from './Icon';
import { ripple } from './ripple';
import { Radio } from './Selection';

/** Text scale from which Segmented stacks its options, since side by side they no longer fit. */
export const SEGMENTED_STACK_SCALE = 1.5;

/**
 * A single-choice control (DESIGN 6.5): segmented buttons, or a list of radio buttons at large text sizes so long
 * labels are never cut off. Both are read as a group of radio buttons.
 */
export function Segmented<T extends string>({
  label,
  options,
  selected,
  optionLabel,
  onPick,
  testId,
}: {
  label: string;
  options: readonly T[];
  selected: T;
  optionLabel: (option: T) => string;
  onPick: (option: T) => void;
  testId?: string;
}) {
  const labelId = useId();
  const name = useId();
  const stacked = appliedTextScale() >= SEGMENTED_STACK_SCALE;
  return (
    <div className="m3-segmented-field" data-testid={testId}>
      <span id={labelId} className="t-label-large">
        {label}
      </span>
      {stacked ? (
        <div role="radiogroup" aria-labelledby={labelId} className="m3-segmented-list" data-testid="segmented_list">
          {options.map((option) => (
            <label key={option} className="m3-segmented-list__option t-body-large">
              <Radio name={name} checked={option === selected} onCheckedChange={() => onPick(option)} />
              {optionLabel(option)}
            </label>
          ))}
        </div>
      ) : (
        <div role="radiogroup" aria-labelledby={labelId} className="m3-segmented" data-testid="segmented_row">
          {options.map((option) => {
            const on = option === selected;
            return (
              <button
                key={option}
                type="button"
                role="radio"
                aria-checked={on}
                className={`m3-segmented__button m3-state t-label-large ${on ? 'm3-segmented__button--selected' : ''}`}
                onPointerDown={ripple}
                onClick={() => onPick(option)}
              >
                {on && <Icon name="round/check" size={18} />}
                <span className="m3-segmented__label">{optionLabel(option)}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
