import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { applyTextScale } from '../../theme/textScale';
import {
  Dialog,
  FilterChip,
  IconButton,
  INDEFINITE,
  Segmented,
  SnackbarProvider,
  TextField,
  UNDO_WINDOW_MS,
  useSnackbars,
} from '.';
import { keyboardInset } from './BottomSheet';

describe('kit', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

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

  it('a snackbar without a time limit stays until its action, and lets an Undo show first', () => {
    const update = vi.fn();
    const undo = vi.fn();
    function Trigger() {
      const snackbars = useSnackbars();
      return (
        <>
          <button onClick={() => snackbars.showAction('A new version is ready', 'Update', update, INDEFINITE)}>
            update
          </button>
          <button onClick={() => snackbars.showUndo('Removed Milk', undo)}>remove</button>
        </>
      );
    }
    render(
      <SnackbarProvider>
        <Trigger />
      </SnackbarProvider>,
    );
    fireEvent.click(screen.getByText('update'));
    act(() => vi.advanceTimersByTime(60 * 60 * 1000));
    expect(screen.getByRole('status')).toHaveTextContent('A new version is ready');
    // An Undo takes its place for its 8 seconds, then the prompt comes back.
    fireEvent.click(screen.getByText('remove'));
    expect(screen.getByRole('status')).toHaveTextContent('Removed Milk');
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS));
    expect(screen.getByRole('status')).toHaveTextContent('A new version is ready');
    act(() => vi.advanceTimersByTime(60_000));
    fireEvent.click(screen.getByRole('button', { name: 'Update' }));
    expect(update).toHaveBeenCalledOnce();
    expect(undo).not.toHaveBeenCalled();
    expect(screen.queryByRole('status')).toBeNull();
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

  /** One Undo snackbar, then 'Saved' queued after it. */
  function undoThenSaved() {
    function Trigger() {
      const snackbars = useSnackbars();
      return (
        <button
          onClick={() => {
            snackbars.showUndo('Recipe deleted', () => {});
            snackbars.show('Saved');
          }}
        >
          delete
        </button>
      );
    }
    const { container } = render(
      <SnackbarProvider>
        <Trigger />
      </SnackbarProvider>,
    );
    fireEvent.click(within(container).getByText('delete'));
    return () => within(container).getByRole('status');
  }

  it('the Undo timer waits while the snackbar has focus, then goes on with the time that was left (A11Y-02)', () => {
    const status = undoThenSaved();
    act(() => vi.advanceTimersByTime(3_000));
    act(() => screen.getByRole('button', { name: 'Undo' }).focus());
    act(() => vi.advanceTimersByTime(60_000));
    expect(status()).toHaveTextContent('Recipe deleted');
    expect(status()).toHaveAttribute('data-held', 'true');
    act(() => screen.getByRole('button', { name: 'Undo' }).blur());
    // 5 of the 8 seconds were left.
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS - 3_000 - 1));
    expect(status()).toHaveTextContent('Recipe deleted');
    act(() => vi.advanceTimersByTime(1));
    expect(status()).toHaveTextContent('Saved');
  });

  it('the Undo timer waits while the pointer is over the snackbar', () => {
    const status = undoThenSaved();
    act(() => vi.advanceTimersByTime(6_000));
    fireEvent.pointerEnter(status());
    act(() => vi.advanceTimersByTime(30_000));
    expect(status()).toHaveTextContent('Recipe deleted');
    fireEvent.pointerLeave(status());
    act(() => vi.advanceTimersByTime(UNDO_WINDOW_MS - 6_000 - 1));
    expect(status()).toHaveTextContent('Recipe deleted');
    act(() => vi.advanceTimersByTime(1));
    expect(status()).toHaveTextContent('Saved');
  });

  it('a held snackbar that goes away does not hold the next one', () => {
    const status = undoThenSaved();
    act(() => screen.getByRole('button', { name: 'Undo' }).focus());
    fireEvent.click(screen.getByRole('button', { name: 'Undo' }));
    expect(status()).toHaveTextContent('Saved');
    expect(status()).not.toHaveAttribute('data-held');
    act(() => vi.advanceTimersByTime(4_000));
    expect(screen.queryByRole('status')).toBeNull();
  });
});

describe('keyboardInset (WEB-14)', () => {
  it('is the part of the page under the keyboard', () => {
    // iPhone 15 portrait: 844 px tall, the keyboard leaves 508.
    expect(keyboardInset({ height: 844, offsetTop: 0, scale: 1 }, 844)).toBe(0);
    expect(keyboardInset({ height: 508, offsetTop: 0, scale: 1 }, 844)).toBe(336);
    // Safari scrolled the visual viewport down to the field: the keyboard still covers the bottom 336 px.
    expect(keyboardInset({ height: 508, offsetTop: 100, scale: 1 }, 844)).toBe(236);
  });

  it('is 0 while pinch-zoomed and never negative', () => {
    expect(keyboardInset({ height: 422, offsetTop: 0, scale: 2 }, 844)).toBe(0);
    expect(keyboardInset({ height: 900, offsetTop: 0, scale: 1 }, 844)).toBe(0);
  });
});
