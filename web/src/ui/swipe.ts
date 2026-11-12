import { useRef, useState, type PointerEvent } from 'react';
import { isStandalone } from '../core/install';

/** How far a row must be swiped, as a share of its width, to act (SwipeToDismissBox's default threshold). */
const SWIPE_THRESHOLD = 0.4;

/** Safari's Back gesture starts this close to the screen's left edge (after v1, WEB-15). */
export const SAFARI_EDGE_PX = 24;

/**
 * A swipe starting at [x] belongs to Safari, not to the row: in a Safari tab a swipe from the left edge goes Back. The
 * Home Screen app has no such gesture, so there every swipe is the row's.
 */
export const isSafariEdge = (x: number, standalone: boolean): boolean => !standalone && x < SAFARI_EDGE_PX;

/**
 * A row that swipes sideways (SwipeToDismissBox): rows scroll vertically while a horizontal drag moves the row, and
 * letting go past 40% of its width calls [onSwipe] with the direction. [wasDragged] tells a click after a drag
 * apart from a tap. In a Safari tab a swipe from the screen's left edge is left to Safari's Back (WEB-15).
 */
export function useSwipe(onSwipe: (direction: 'right' | 'left') => void) {
  const [dx, setDx] = useState(0);
  const [dragging, setDragging] = useState(false);
  const drag = useRef<{ x: number; y: number; horizontal: boolean | null; width: number } | null>(null);
  const moved = useRef(false);

  const handlers = {
    onPointerDown(e: PointerEvent<HTMLElement>) {
      if (e.pointerType === 'mouse' && e.button !== 0) return;
      if (isSafariEdge(e.clientX, isStandalone())) {
        drag.current = null;
        return;
      }
      drag.current = { x: e.clientX, y: e.clientY, horizontal: null, width: e.currentTarget.offsetWidth };
      moved.current = false;
    },
    onPointerMove(e: PointerEvent<HTMLElement>) {
      const d = drag.current;
      if (!d) return;
      const x = e.clientX - d.x;
      const y = e.clientY - d.y;
      if (d.horizontal === null && Math.hypot(x, y) > 10) {
        d.horizontal = Math.abs(x) > Math.abs(y);
        if (d.horizontal) e.currentTarget.setPointerCapture(e.pointerId);
      }
      if (d.horizontal) {
        moved.current = true;
        setDragging(true);
        setDx(x);
      }
    },
    onPointerUp() {
      const d = drag.current;
      drag.current = null;
      setDragging(false);
      if (!d?.horizontal) return;
      if (dx > d.width * SWIPE_THRESHOLD) onSwipe('right');
      else if (dx < -d.width * SWIPE_THRESHOLD) onSwipe('left');
      setDx(0);
    },
    onPointerCancel() {
      handlers.onPointerUp();
    },
  };

  return {
    dx,
    /** The row's transform while dragging; it springs back when let go. */
    style: { transform: dx ? `translateX(${dx}px)` : undefined, transition: dragging ? 'none' : undefined },
    handlers,
    wasDragged: () => moved.current,
  };
}
