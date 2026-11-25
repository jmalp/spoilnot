import { useEffect, useRef, useState, type PointerEvent } from 'react';
import { useObservable } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useServices } from '../../core/services';
import type { Recipe } from '../../domain/recipes/models';
import { Scaling } from '../../domain/recipes/parser';
import { BottomSheet, Icon, Progress, RoundIconButton } from '../kit';
import { IngredientChecklist, StepLeaf } from './components';
import '../pantry/form.css';
import './recipes.css';

/** Keeps the screen on while cooking (keepScreenOn in the app); iOS 16.4 and later have the Wake Lock API. */
function useScreenAwake() {
  useEffect(() => {
    let lock: WakeLockSentinel | null = null;
    let active = true;
    const request = () => {
      if (!('wakeLock' in navigator) || document.visibilityState !== 'visible') return;
      navigator.wakeLock.request('screen').then(
        (l) => {
          if (active) lock = l;
          else void l.release();
        },
        () => undefined,
      );
    };
    // The lock ends when the app goes to the background; take it again on return.
    const onVisible = () => {
      if (document.visibilityState === 'visible') request();
    };
    request();
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      active = false;
      document.removeEventListener('visibilitychange', onVisible);
      void lock?.release();
    };
  }, []);
}

/** Cook mode (DESIGN 5.5, 19.12): the screen stays awake, large type, one step at a time, ingredients in a sheet. */
export function CookModeScreen({
  recipeId,
  servings,
  onBack,
}: {
  recipeId: string;
  servings: number | null;
  onBack: () => void;
}) {
  const { recipes } = useServices();
  const box = useObservable(recipes.box$, null);
  const recipe = box?.recipes.find((r) => r.id === recipeId) ?? null;
  useScreenAwake();
  return (
    <div className="sn-screen sn-screen--page sn-screen--inner-scroll" data-testid="screen_cook_mode">
      {recipe ? (
        <CookSteps recipe={recipe} servings={servings} onBack={onBack} />
      ) : (
        <>
          <header className="sn-cook__top">
            <RoundIconButton icon="round/arrow_back" label={str('back')} onClick={onBack} />
          </header>
          <div className="sn-centered">
            {box === null ? <Progress /> : <p className="t-body-large">{str('recipe_gone')}</p>}
          </div>
        </>
      )}
    </div>
  );
}

/** More steps than this draw their bars closer together. */
const MANY_STEPS = 12;

/**
 * Cook mode's page (DESIGN 19.12): Back and the recipe's name with "Step 1 of 2" as a chip, a bar per step, the step
 * on a card with its number in a leaf, and Back, Ingredients and Next joined at the bottom. A sideways swipe turns the
 * page too.
 */
function CookSteps({ recipe, servings, onBack }: { recipe: Recipe; servings: number | null; onBack: () => void }) {
  const steps = recipe.content.steps;
  const [page, setPage] = useState(0);
  const [showIngredients, setShowIngredients] = useState(false);
  const [checked, setChecked] = useState<ReadonlySet<number>>(new Set());
  const start = useRef<{ x: number; y: number } | null>(null);
  const current = Math.min(page, steps.length - 1);
  const step = steps[current];
  const go = (to: number) => setPage(Math.max(0, Math.min(steps.length - 1, to)));
  // A sideways swipe turns the page, like the app's pager.
  const down = (e: PointerEvent) => (start.current = { x: e.clientX, y: e.clientY });
  const up = (e: PointerEvent) => {
    const s = start.current;
    start.current = null;
    if (!s) return;
    const dx = e.clientX - s.x;
    if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(e.clientY - s.y)) go(current + (dx < 0 ? 1 : -1));
  };
  if (!step) return null;
  const factor = Scaling.factor(recipe.content.servings, servings);
  const progress = str('recipe_step_of', current + 1, steps.length);
  return (
    <div className="sn-cook">
      <header className="sn-cook__top">
        <div className="sn-cook__title-row">
          {/* This Back leaves cook mode; the one at the bottom turns the page (after v1, review A11Y-08). */}
          <RoundIconButton icon="round/arrow_back" label={str('back')} onClick={onBack} />
          <h1 className="t-row-title sn-cook__title">{recipe.content.title}</h1>
          {/* Said again as the page turns (it was a line above the step). */}
          <span className="sn-cook__chip t-button-medium" aria-live="polite">
            {progress}
          </span>
        </div>
        {/* A bar per step, named so VoiceOver says "Step 1 of 3" rather than a bare percentage (after v1, A11Y-12). */}
        <div
          className={`sn-cook__bars ${steps.length > MANY_STEPS ? 'sn-cook__bars--many' : ''}`}
          role="progressbar"
          aria-label={progress}
          aria-valuetext={progress}
          aria-valuemin={1}
          aria-valuemax={steps.length}
          aria-valuenow={current + 1}
        >
          {steps.map((_, index) => (
            <span key={index} className={index <= current ? 'sn-cook__bar--done' : undefined} />
          ))}
        </div>
      </header>
      <div className="sn-cook__step" data-testid="cook_pager" onPointerDown={down} onPointerUp={up}>
        {/* The chip says the step; the leaf is a picture of it. */}
        <span aria-hidden>
          <StepLeaf number={current + 1} large />
        </span>
        {step.group && <h2 className="t-group-heading c-primary">{step.group}</h2>}
        <p className="sn-cook__text" data-testid="cook_step">
          {step.text}
        </p>
      </div>
      <div className="sn-cook__controls">
        <button
          type="button"
          className="sn-cook-button sn-cook-button--back m3-state t-button-medium"
          disabled={current === 0}
          aria-label={str('recipe_step_previous_description')}
          onClick={() => go(current - 1)}
        >
          <Icon name="round/arrow_back" size={20} />
          {str('recipe_step_previous')}
        </button>
        <button
          type="button"
          className="sn-cook-button sn-cook-button--ingredients m3-state t-button-medium"
          onClick={() => setShowIngredients(true)}
        >
          <Icon name="round/list" size={20} />
          {str('recipe_ingredients')}
        </button>
        <button
          type="button"
          className="sn-cook-button sn-cook-button--next m3-state t-button-large"
          disabled={current >= steps.length - 1}
          onClick={() => go(current + 1)}
          data-testid="cook_next"
        >
          {str('recipe_step_next')}
          <Icon name="round/arrow_forward" size={22} />
        </button>
      </div>
      {showIngredients && (
        <BottomSheet label={str('recipe_ingredients')} onDismiss={() => setShowIngredients(false)}>
          <div className="sn-form-sheet sn-form-sheet--look">
            <div className="sn-form-sheet__fields sn-cook__sheet">
              <h2 className="t-sheet-title">{str('recipe_ingredients')}</h2>
              <IngredientChecklist
                field
                lines={recipe.content.ingredients.map((i) => [
                  i.group,
                  Scaling.line(i.raw, factor, str('recipe_scaled_pinch')),
                ])}
                checked={checked}
                onToggle={(index) => {
                  const next = new Set(checked);
                  if (next.has(index)) next.delete(index);
                  else next.add(index);
                  setChecked(next);
                }}
              />
            </div>
          </div>
        </BottomSheet>
      )}
    </div>
  );
}
