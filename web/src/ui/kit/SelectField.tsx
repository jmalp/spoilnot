import { useId, useState } from 'react';
import { Icon } from './Icon';
import { Menu, MenuItem } from './Menu';
import { FieldMessage } from './TextField';

/**
 * A field that opens a menu of choices (EnumDropdown in the app), in the 2026-11 look (DESIGN 19.10): its label
 * above, the choice in a box on the field color. Disabled with a helper line when the value cannot change, such as a
 * unit locked by stock.
 */
export function SelectField<T extends string>({
  label,
  value,
  options,
  optionLabel,
  onPick,
  disabled,
  supportingText,
  testId,
}: {
  label: string;
  value: T;
  options: readonly T[];
  optionLabel: (t: T) => string;
  onPick: (t: T) => void;
  disabled?: boolean;
  supportingText?: string;
  testId?: string;
}) {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <div className={`m3-text-field m3-text-field--look ${disabled ? 'm3-text-field--disabled' : ''}`}>
      <label className="m3-text-field__label-above t-field-label" htmlFor={id}>
        {label}
      </label>
      <div className="m3-text-field__box sn-anchor">
        <button
          type="button"
          id={id}
          className="m3-text-field__input m3-select t-field-text"
          disabled={disabled}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-describedby={supportingText ? `${id}-supporting` : undefined}
          data-testid={testId}
          onClick={() => setOpen(true)}
        >
          {optionLabel(value)}
        </button>
        <span className="m3-text-field__trailing m3-select__arrow" aria-hidden>
          <Icon name="round/arrow_drop_down" />
        </span>
        <Menu open={open} onDismiss={() => setOpen(false)}>
          {options.map((option) => (
            <MenuItem
              key={option}
              label={optionLabel(option)}
              onClick={() => {
                onPick(option);
                setOpen(false);
              }}
            />
          ))}
        </Menu>
      </div>
      <FieldMessage id={supportingText ? `${id}-supporting` : undefined} helper={supportingText} />
    </div>
  );
}
