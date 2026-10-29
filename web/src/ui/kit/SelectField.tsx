import { useId, useState } from 'react';
import { Icon } from './Icon';
import { Menu, MenuItem } from './Menu';

/**
 * A read-only outlined field that opens a menu of choices (EnumDropdown, an ExposedDropdownMenuBox in the app).
 * Disabled with a helper line when the value cannot change, such as a unit locked by stock.
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
    <div className={`m3-text-field m3-text-field--floating ${disabled ? 'm3-text-field--disabled' : ''}`}>
      <div className="m3-text-field__box sn-anchor">
        <button
          type="button"
          id={id}
          className="m3-text-field__input m3-select"
          disabled={disabled}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-describedby={supportingText ? `${id}-supporting` : undefined}
          data-testid={testId}
          onClick={() => setOpen(true)}
        >
          {optionLabel(value)}
        </button>
        <label className="m3-text-field__label" htmlFor={id}>
          {label}
        </label>
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
      {supportingText && (
        <div id={`${id}-supporting`} className="m3-text-field__supporting">
          {supportingText}
        </div>
      )}
    </div>
  );
}
