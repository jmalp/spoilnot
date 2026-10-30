import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { applyTextScale } from '../../theme/textScale';
import {
  Dialog,
  FilterChip,
  IconButton,
  Segmented,
  SnackbarProvider,
  TextField,
  UNDO_WINDOW_MS,
  useSnackbars,
} from '.';

describe('kit', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('a selected filter chip says so and shows a check, never color alone', () => {
    const { container } = render(<FilterChip label="Expiring soon" selected onClick={() => {}} />);
    expect(screen.getByRole('button', { name: 'Expiring soon' })).toHaveAttribute('aria-pressed', 'true');
    expect(container.querySelector('svg')).not.toBeNull();
  });

  it('icon buttons are announced by their label', () => {
    render(<IconButton icon="round/arrow_back" label="Back" onClick={() => {}} />);
    expect(screen.getByRole('button', { name: 'Back' })).toBeInTheDocument();
  });

  it('a text field ties its label and error to the input', () => {
    render(<TextField label="Email" value="x" onValueChange={() => {}} error="Enter a valid email" />);
    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Enter a valid email');
  });

  it('segmented buttons are radio buttons with a check on the choice, and a radio list at large text', () => {
    const onPick = vi.fn();
    const props = {
      label: 'Theme',
      options: ['system', 'light', 'dark'] as const,
      selected: 'light' as const,
      optionLabel: (o: string) => o.toUpperCase(),
      onPick,
    };
    const { unmount } = render(<Segmented {...props} />);
    expect(screen.getByRole('radiogroup', { name: 'Theme' })).toHaveAttribute('data-testid', 'segmented_row');
    expect(screen.getByRole('radio', { name: 'LIGHT' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: 'LIGHT' }).querySelector('svg')).not.toBeNull();
    fireEvent.click(screen.getByRole('radio', { name: 'DARK' }));
    expect(onPick).toHaveBeenCalledWith('dark');
    unmount();
    applyTextScale(2);
    render(<Segmented {...props} />);
    expect(screen.getByRole('radiogroup', { name: 'Theme' })).toHaveAttribute('data-testid', 'segmented_list');
    expect(screen.getByRole('radio', { name: 'LIGHT' })).toBeChecked();
    fireEvent.click(screen.getByRole('radio', { name: 'SYSTEM' }));
    expect(onPick).toHaveBeenCalledWith('system');
    applyTextScale(1);
  });

  it('a snackbar with an action runs it once and stays 10 seconds', () => {
    const update = vi.fn();
    function Trigger() {
      const snackbars = useSnackbars();
      return <button onClick={() => snackbars.showAction('A new version is ready', 'Update', update)}>update</button>;
    }
    const { unmount } = render(
      <SnackbarProvider>
        <Trigger />
      </SnackbarProvider>,
    );
    fireEvent.click(screen.getByText('update'));
    act(() => vi.advanceTimersByTime(9_000));
    fireEvent.click(screen.getByRole('button', { name: 'Update' }));
    expect(update).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('status')).toBeNull();
    unmount();
  });

  it('a dialog dismisses on Escape', () => {
    const onDismiss = vi.fn();
    render(<Dialog title="Clear this week?" actions={null} onDismiss={onDismiss} />);
    fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }));
    expect(onDismiss).toHaveBeenCalled();
  });

  it('snackbars show one at a time; Undo stays 8 seconds and runs once', () => {
    const undo = vi.fn();
    function Trigger() {
      const snackbars = useSnackbars();
      return (
        <button
          onClick={() => {
            snackbars.showUndo('Removed Tacos from the plan', undo);
            snackbars.show('Saved');
          }}
        >
          go
        </button>
      );
    }
    render(
      <SnackbarProvider>
        <Trigger />
      </SnackbarProvider>,
    );
    fireEvent.click(screen.getByText('go'));
    expect(screen.getByRole('status')).toHaveTextContent('Removed Tacos from the plan');
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS - 1));
    fireEvent.click(screen.getByRole('button', { name: 'Undo' }));
    expect(undo).toHaveBeenCalledOnce();
    expect(screen.getByRole('status')).toHaveTextContent('Saved');
  });
});
