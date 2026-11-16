import { useEffect, useRef, useState } from 'react';

/** How long after the last scroll event a scroll counts as over, where WebKit sends no `scrollend`. */
export const SCROLL_IDLE_MS = 150;

/**
 * [value] as a list should show it (DESIGN 6.4; rememberHeldWhileScrolling in the app): while [scroller] scrolls it
 * keeps what was showing when the scroll began, so a change from another phone never moves the list under the finger,
 * and it shows the newest value once the scroll stops (`scrollend`, or [idleMs] without a scroll event). Outside a
 * scroll it is [value] itself, with no extra render. Review findings WEB-11 and CODE-09.
 */
export function useHeldWhileScrolling<T>(value: T, scroller: HTMLElement | null, idleMs = SCROLL_IDLE_MS): T {
  const [scrolling, setScrolling] = useState(false);
  const [held, setHeld] = useState(value);
  // What is showing now, for the moment a scroll begins; kept in an effect, not during render.
  const showing = useRef(value);
  useEffect(() => {
    if (!scrolling) showing.current = value;
  }, [value, scrolling]);
  useEffect(() => {
    if (!scroller) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let active = false;
    const settle = () => {
      clearTimeout(timer);
      if (!active) return;
      active = false;
      setScrolling(false);
    };
    const onScroll = () => {
      if (!active) {
        active = true;
        setHeld(showing.current);
        setScrolling(true);
      }
      clearTimeout(timer);
      timer = setTimeout(settle, idleMs);
    };
    scroller.addEventListener('scroll', onScroll, { passive: true });
    scroller.addEventListener('scrollend', settle);
    return () => {
      clearTimeout(timer);
      scroller.removeEventListener('scroll', onScroll);
      scroller.removeEventListener('scrollend', settle);
    };
  }, [scroller, idleMs]);
  return scrolling ? held : value;
}
