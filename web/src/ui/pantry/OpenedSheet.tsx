import { useId, useState } from 'react';
import { plural, str } from '../../core/i18n';
import type { StockBatch } from '../../domain/pantry/models';
import { addDays, type LocalDate } from '../../domain/time';
import { BottomSheet, Button, CountStepper, SpoilNotFilterChip } from '../kit';
import { formatDate, formatQuantity } from './labels';
import './form.css';

/** "Use within" choices for Mark opened, in days; a remembered value outside them is offered too. */
const OPENED_DAY_CHOICES = [1, 3, 5, 7, 14, 30];

const daysLabel = (days: number) =>
  days === 7
    ? str('opened_one_week')
    : days === 14
      ? str('opened_two_weeks')
      : days === 30
        ? str('opened_one_month')
        : plural('opened_days', days, days);

/**
 * Mark opened (DESIGN 5.2): how many were opened (for a batch kept in pieces) and how long they keep once opened,
 * starting from what was chosen for this product last time. Shows the date it will get, never later than its date.
 * In the 2026-11 look (step 3): the sheet title, − and + joined to the count, the choices as filter chips.
 */
export function OpenedSheet({
  batch,
  rememberedDays,
  today,
  onSave,
  onDismiss,
}: {
  batch: StockBatch;
  rememberedDays: number | null;
  today: LocalDate;
  onSave: (count: number, days: number) => void;
  onDismiss: () => void;
}) {
  const pieces = batch.unit === 'pcs' && batch.quantity >= 2;
  const [count, setCount] = useState(1);
  const [days, setDays] = useState(rememberedDays ?? 7);
  const choices = [...new Set([...OPENED_DAY_CHOICES, ...(rememberedDays ? [rememberedDays] : [])])].sort(
    (a, b) => a - b,
  );
  const byDays = addDays(today, days);
  const keepsDate = batch.expiryDate !== null && batch.expiryDate < byDays;
  const max = Math.floor(batch.quantity);
  const withinId = useId();
  return (
    <BottomSheet label={str('opened_title', batch.productName)} onDismiss={onDismiss} testId="sheet_opened">
      <div className="sn-opened">
        <h2 className="t-sheet-title">{str('opened_title', batch.productName)}</h2>
        {pieces && (
          <div className="sn-stepper-field">
            <p className="t-field-label sn-stepper-field__label">{str('opened_how_many')}</p>
            <CountStepper
              value={str('opened_count', count, formatQuantity(batch.quantity))}
              minusLabel={str('opened_fewer')}
              plusLabel={str('opened_more')}
              onMinus={() => setCount(Math.max(1, count - 1))}
              onPlus={() => setCount(Math.min(max, count + 1))}
              minusDisabled={count <= 1}
              plusDisabled={count >= max}
              valueTestId="opened_count"
            />
          </div>
        )}
        <div className="sn-field-group">
          <p className="t-field-label c-on-surface-variant" id={withinId}>
            {str('opened_use_within')}
          </p>
          <div className="sn-opened__chips" role="group" aria-labelledby={withinId}>
            {choices.map((choice) => (
              <SpoilNotFilterChip
                key={choice}
                label={daysLabel(choice)}
                selected={days === choice}
                onClick={() => setDays(choice)}
                data-testid={`opened_days_${choice}`}
              />
            ))}
          </div>
        </div>
        <p className="t-body-large">
          {keepsDate
            ? str('opened_keeps_date', formatDate(batch.expiryDate!, today))
            : str('opened_new_date', formatDate(byDays, today))}
        </p>
        <p className="t-body-small c-on-surface-variant">{str('opened_remembered', batch.productName)}</p>
        <Button primary onClick={() => onSave(pieces ? count : batch.quantity, days)} data-testid="button_mark_opened">
          {str('action_mark_opened')}
        </Button>
      </div>
    </BottomSheet>
  );
}
