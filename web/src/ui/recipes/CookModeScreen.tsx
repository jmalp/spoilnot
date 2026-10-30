import { useEffect, useRef, useState, type PointerEvent } from 'react';
import { useObservable } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useServices } from '../../core/services';
import type { Recipe } from '../../domain/recipes/models';
import { Scaling } from '../../domain/recipes/parser';
import { BottomSheet, Button, Progress } from '../kit';
import { DetailScaffold } from '../Scaffold';
import { IngredientChecklist } from './components';
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

/** Cook mode (DESIGN 5.5): the screen stays awake, large type, one step at a time, ingredients in a sheet. */
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
    <DetailScaffold title={recipe?.content.title ?? ''} onBack={onBack} testId="screen_cook_mode">
      {recipe ? (
        <CookSteps recipe={recipe} servings={servings} />
      ) : (
        <div className="sn-centered">
          {box === null ? <Progress /> : <p className="t-body-large">{str('recipe_gone')}</p>}
        </div>
      )}
    </DetailScaffold>
  );
}

function CookSteps({ recipe, servings }: { recipe: Recipe; servings: number | null }) {
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
  return (
    <div className="sn-cook">
      <div
        className="sn-cook__progress"
        role="progressbar"
        aria-valuemin={1}
        aria-valuemax={steps.length}
        aria-valuenow={current + 1}
      >
        <span style={{ width: `${((current + 1) / Math.max(1, steps.length)) * 100}%` }} />
      </div>
      <div className="sn-cook__step" data-testid="cook_pager" onPointerDown={down} onPointerUp={up}>
        <p className="t-title-medium c-primary" aria-live="polite">
          {str('recipe_step_of', current + 1, steps.length)}
        </p>
        {step.group && <h2 className="t-title-small">{step.group}</h2>}
        <p className="t-headline-small" data-testid="cook_step">
          {step.text}
        </p>
      </div>
      <div className="sn-cook__bar">
        <Button variant="outlined" icon="round/arrow_back" disabled={current === 0} onClick={() => go(current - 1)}>
          {str('recipe_step_previous')}
        </Button>
        <Button
          variant="text"
          icon="round/list"
          className="sn-cook__ingredients"
          onClick={() => setShowIngredients(true)}
        >
          {str('recipe_ingredients')}
        </Button>
        <Button
          variant="tonal"
          trailingIcon="round/arrow_forward"
          disabled={current >= steps.length - 1}
          onClick={() => go(current + 1)}
          data-testid="cook_next"
        >
          {str('recipe_step_next')}
        </Button>
      </div>
      {showIngredients && (
        <BottomSheet label={str('recipe_ingredients')} onDismiss={() => setShowIngredients(false)}>
          <div className="sn-recipe-detail__body">
            <h2 className="t-title-large">{str('recipe_ingredients')}</h2>
            <IngredientChecklist
              lines={recipe.content.ingredients.map((i) => [i.group, Scaling.line(i.raw, factor)])}
              checked={checked}
              onToggle={(index) => {
                const next = new Set(checked);
                if (next.has(index)) next.delete(index);
                else next.add(index);
                setChecked(next);
              }}
            />
          </div>
        </BottomSheet>
      )}
    </div>
  );
}
