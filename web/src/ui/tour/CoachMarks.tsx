// Coach marks for the app tour (DESIGN 5.8), ported from CoachMarks.kt: elements carry data-tour="<key>", and the
// overlay dims the screen around the marked element of the current step.
import { useEffect, useState } from 'react';
import { Button } from '../kit';
import './tour.css';

/** The marked element on screen for [key], or null (the first one with a size: a hidden tab's twin has none). */
export function findMark(key: string): HTMLElement | null {
  for (const element of document.querySelectorAll<HTMLElement>(`[data-tour="${key}"]`)) {
    const r = element.getBoundingClientRect();
    if (r.width > 0 && r.height > 0) return element;
  }
  return null;
}

/** The mark a tap landed on, reported without taking the tap, so the element still does what it always does. */
export function markOf(target: EventTarget | null): string | null {
  return target instanceof Element ? (target.closest('[data-tour]')?.getAttribute('data-tour') ?? null) : null;
}

type Rect = { left: number; top: number; right: number; bottom: number };

/** Space the card needs beside its target before it covers the target instead. */
const MIN_CARD_ROOM = 160;
const PAD = 8;
const GAP = 12;
const CORNER = 12;

const sameRect = (a: Rect | null, b: Rect | null) =>
  a === b || (!!a && !!b && a.left === b.left && a.top === b.top && a.right === b.right && a.bottom === b.bottom);

/** Where [element] is now, following scrolling and layout changes frame by frame while the overlay shows. */
function useBounds(element: () => HTMLElement | null): Rect | null {
  const [rect, setRect] = useState<Rect | null>(null);
  useEffect(() => {
    let frame = 0;
    const measure = () => {
      const r = element()?.getBoundingClientRect();
      const next =
        r && r.width > 0 && r.height > 0 ? { left: r.left, top: r.top, right: r.right, bottom: r.bottom } : null;
      setRect((current) => (sameRect(current, next) ? current : next));
      frame = requestAnimationFrame(measure);
    };
    measure();
    return () => cancelAnimationFrame(frame);
  }, [element]);
  return rect;
}

/**
 * A dimmed screen with a hole around the target (null for a centered card) and a card with [text], [progress]
 * ("3 of 10"), Skip and Next. Taps in the hole reach the element below, so the user can follow along; taps on the
 * dimmed part do nothing.
 */
export function CoachMarkOverlay({
  target,
  text,
  nextLabel,
  onNext,
  title,
  progress,
  skipLabel,
  onSkip,
}: {
  target: () => HTMLElement | null;
  text: string;
  nextLabel: string;
  onNext: () => void;
  title?: string;
  progress?: string;
  skipLabel?: string;
  onSkip?: () => void;
}) {
  const bounds = useBounds(target);
  const width = window.innerWidth;
  const height = window.innerHeight;
  const hole = bounds && {
    left: Math.max(0, bounds.left - PAD),
    top: Math.max(0, bounds.top - PAD),
    right: Math.min(width, bounds.right + PAD),
    bottom: Math.min(height, bounds.bottom + PAD),
  };
  const shown = hole && hole.right > hole.left && hole.bottom > hole.top ? hole : null;
  const below = shown ? height - shown.bottom - 2 * GAP : 0;
  const above = shown ? shown.top - 2 * GAP : 0;
  const center = shown ? (shown.top + shown.bottom) / 2 : 0;
  // Below a target in the top half, above one in the bottom half, wherever there is room.
  const placement = !shown
    ? { className: 'sn-coach__card--center', style: {} }
    : below >= MIN_CARD_ROOM && (center < height / 2 || above < MIN_CARD_ROOM)
      ? { className: '', style: { top: shown.bottom + GAP, maxHeight: below } }
      : above >= MIN_CARD_ROOM
        ? { className: '', style: { bottom: height - shown.top + GAP, maxHeight: above } }
        : // A target as tall as the screen: the card covers its lower part.
          { className: '', style: { bottom: 16 } };
  return (
    <div className="sn-coach" data-testid="coach_mark_overlay">
      {shown ? (
        <>
          {/* Four invisible blocks take the touches around the hole; the dimming is drawn with rounded corners. */}
          <div className="sn-coach__block" style={{ left: 0, top: 0, width, height: shown.top }} />
          <div
            className="sn-coach__block"
            style={{ left: 0, top: shown.bottom, width, height: height - shown.bottom }}
          />
          <div
            className="sn-coach__block"
            style={{ left: 0, top: shown.top, width: shown.left, height: shown.bottom - shown.top }}
          />
          <div
            className="sn-coach__block"
            style={{
              left: shown.right,
              top: shown.top,
              width: width - shown.right,
              height: shown.bottom - shown.top,
            }}
          />
          <svg className="sn-coach__scrim" width={width} height={height} aria-hidden="true">
            <path
              fillRule="evenodd"
              d={`M0 0H${width}V${height}H0Z M${shown.left} ${shown.top} m0 ${CORNER} a${CORNER} ${CORNER} 0 0 1 ${CORNER} -${CORNER} H${shown.right - CORNER} a${CORNER} ${CORNER} 0 0 1 ${CORNER} ${CORNER} V${shown.bottom - CORNER} a${CORNER} ${CORNER} 0 0 1 -${CORNER} ${CORNER} H${shown.left + CORNER} a${CORNER} ${CORNER} 0 0 1 -${CORNER} -${CORNER} Z`}
            />
            <rect
              className="sn-coach__ring"
              x={shown.left}
              y={shown.top}
              width={shown.right - shown.left}
              height={shown.bottom - shown.top}
              rx={CORNER}
            />
          </svg>
        </>
      ) : (
        <div className="sn-coach__block sn-coach__block--dim" style={{ inset: 0 }} />
      )}
      <section
        className={`sn-coach__card m3-card m3-card--elevated ${placement.className}`}
        style={placement.style}
        role="dialog"
        aria-label={title ?? text}
        aria-live="polite"
        data-testid="coach_mark_card"
      >
        <div className="sn-coach__content">
          {progress && <p className="sn-coach__progress t-label-medium">{progress}</p>}
          {title && <h2 className="t-title-large">{title}</h2>}
          <p className="t-body-large">{text}</p>
          <div className="sn-coach__actions">
            {skipLabel && (
              <Button variant="text" onClick={onSkip} data-testid="coach_mark_skip">
                {skipLabel}
              </Button>
            )}
            <Button variant="filled" onClick={onNext} data-testid="coach_mark_next">
              {nextLabel}
            </Button>
          </div>
        </div>
      </section>
    </div>
  );
}
