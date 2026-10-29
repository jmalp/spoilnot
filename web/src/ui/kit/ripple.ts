import type { PointerEvent } from 'react';

/** Starts the ripple of an `.m3-state` element at the touch point (kit.css). */
export function ripple(event: PointerEvent<HTMLElement>): void {
  const target = event.currentTarget.querySelector<HTMLElement>(':scope > .m3-state') ?? event.currentTarget;
  if (!target.classList.contains('m3-state')) return;
  const rect = target.getBoundingClientRect();
  const size = 2 * Math.hypot(rect.width, rect.height);
  target.style.setProperty('--ripple-x', `${event.clientX - rect.left}px`);
  target.style.setProperty('--ripple-y', `${event.clientY - rect.top}px`);
  target.style.setProperty('--ripple-size', `${size}px`);
  target.classList.remove('m3-rippling');
  void target.offsetWidth; // restart the animation
  target.classList.add('m3-rippling');
}
