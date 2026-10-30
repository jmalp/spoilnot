import { useCallback } from 'react';
import { str, type StringId } from '../../core/i18n';
import { Tour, type TourEvent, type TourStepId } from '../../domain/tour/tour';
import { CoachMarkOverlay, findMark } from './CoachMarks';

const TEXT: Record<TourStepId, StringId> = {
  welcome: 'tour_welcome',
  home: 'tour_home',
  openPantry: 'tour_open_pantry',
  addItem: 'tour_add_item',
  pantryRows: 'tour_pantry_rows',
  openPlan: 'tour_open_plan',
  openRecipes: 'tour_open_recipes',
  recipeBox: 'web_tour_recipes',
  openGroceries: 'tour_open_groceries',
  quickAdd: 'tour_quick_add',
  invite: 'tour_invite',
};

/** One tour step over the app: the hole around its target (if it is on screen), its text and the buttons. */
export function TourOverlay({ step, onEvent }: { step: TourStepId; onEvent: (event: TourEvent) => void }) {
  const { target } = Tour.step(step);
  const last = Tour.isLast(step);
  const position = Tour.position(step);
  const find = useCallback(() => (target ? findMark(target) : null), [target]);
  return (
    <CoachMarkOverlay
      target={find}
      title={step === 'welcome' ? str('tour_welcome_title') : undefined}
      text={str(TEXT[step])}
      progress={position === null ? undefined : str('tour_progress', position, Tour.countedSteps)}
      nextLabel={str(step === 'welcome' ? 'tour_start' : last ? 'tour_done' : 'tour_next')}
      onNext={() => onEvent({ type: 'next' })}
      skipLabel={last ? undefined : str('tour_skip')}
      onSkip={() => onEvent({ type: 'skip' })}
    />
  );
}
