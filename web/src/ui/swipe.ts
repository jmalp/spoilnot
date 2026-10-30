import { useRef, useState, type PointerEvent } from 'react';

/** How far a row must be swiped, as a share of its width, to act (SwipeToDismissBox's default threshold). */
const SWIPE_THRESHOLD = 0.4;

/**
 * A row that swipes sideways (SwipeToDismissBox): rows scroll vertically while a horizontal drag moves the row, and
 * letting go past 40% of its width calls [onSwipe] with the direction. [wasDragged] tells a click after a drag
 * apart from a tap.
 */
export function useSwipe(onSwipe: (direction: 'right' | 'left') => void) {
  const [dx, setDx] = useState(0);
  const [dragging, setDragging] = useState(false);
  const drag = useRef<{ x: number; y: number; horizontal: boolean | null; width: number } | null>(null);
  const moved = useRef(false);

  const handlers = {
    onPointerDown(e: PointerEvent<HTMLElement>) {
      if (e.pointerType === 'mouse' && e.button !== 0) return;
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
