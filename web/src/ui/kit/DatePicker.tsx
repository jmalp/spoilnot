import { useState } from 'react';
import { str } from '../../core/i18n';
import { addDays, epochDay, fromEpochDay, type LocalDate } from '../../domain/time';
import { Button } from './Button';
import { Dialog } from './Dialog';
import { IconButton } from './IconButton';

const monthTitle = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' });
const headline = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
const weekday = new Intl.DateTimeFormat('en-US', { weekday: 'narrow', timeZone: 'UTC' });
const fullDay = new Intl.DateTimeFormat('en-US', { dateStyle: 'full', timeZone: 'UTC' });
const utc = (date: LocalDate) => new Date(`${date}T00:00:00Z`);

/** The first day of the month after (or before) [date]'s month. */
function shiftMonth(first: LocalDate, by: number): LocalDate {
  const d = utc(first);
  d.setUTCMonth(d.getUTCMonth() + by, 1);
  return d.toISOString().slice(0, 10);
}

/**
 * The Material 3 date picker dialog (DatePickerDialog in the app): "Select date", the chosen date as a headline, a
 * month grid starting on Sunday with today outlined and the choice filled, and Cancel and OK.
 */
export function DatePickerDialog({
  value,
  today,
  onPick,
  onDismiss,
}: {
  value: LocalDate | null;
  today: LocalDate;
  onPick: (date: LocalDate) => void;
  onDismiss: () => void;
}) {
  const [selected, setSelected] = useState<LocalDate | null>(value);
  const [month, setMonth] = useState(`${(value ?? today).slice(0, 7)}-01`);
  const first = epochDay(month);
  const startOffset = utc(month).getUTCDay(); // Sunday first, as in the app
  const daysInMonth = epochDay(shiftMonth(month, 1)) - first;
  const cells = [
    ...Array.from({ length: startOffset }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => fromEpochDay(first + i)),
  ];
  return (
    <Dialog
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button
            variant="text"
            disabled={!selected}
            onClick={() => selected && onPick(selected)}
            data-testid="date_ok"
          >
            {str('action_ok')}
          </Button>
        </>
      }
    >
      <div className="m3-date-picker">
        <p className="t-label-large">{str('web_select_date')}</p>
        <p className="t-headline-large m3-date-picker__headline">
          {selected ? headline.format(utc(selected)) : str('web_select_date')}
        </p>
        <hr className="sn-divider m3-date-picker__divider" />
        <div className="m3-date-picker__month">
          <span className="t-title-small">{monthTitle.format(utc(month))}</span>
          <span>
            <IconButton
              icon="round/chevron_left"
              label={str('web_previous_month')}
              onClick={() => setMonth(shiftMonth(month, -1))}
            />
            <IconButton
              icon="round/chevron_right"
              label={str('web_next_month')}
              onClick={() => setMonth(shiftMonth(month, 1))}
            />
          </span>
        </div>
        <div className="m3-date-picker__grid" role="grid">
          {Array.from({ length: 7 }, (_, i) => (
            <span key={`w${i}`} className="m3-date-picker__weekday t-body-small" aria-hidden>
              {weekday.format(utc(addDays('2026-10-25', i)))}
            </span>
          ))}
          {cells.map((date, i) =>
            date === null ? (
              <span key={`e${i}`} />
            ) : (
              <button
                key={date}
                type="button"
                className={`m3-date-picker__day t-body-large ${date === selected ? 'm3-date-picker__day--selected' : ''} ${date === today ? 'm3-date-picker__day--today' : ''}`}
                aria-label={fullDay.format(utc(date))}
                aria-pressed={date === selected}
                onClick={() => setSelected(date)}
              >
                {Number(date.slice(8, 10))}
              </button>
            ),
          )}
        </div>
      </div>
    </Dialog>
  );
}
