import { describe, expect, it } from 'vitest';
import { AppPreferences } from '../../core/preferences';
import { TourRunner } from './runner';

describe('TourRunner', () => {
  it('starts on a phone that has not taken the tour, and remembers a skip', () => {
    const prefs = new AppPreferences(null);
    const tour = new TourRunner(prefs);
    const stop = tour.start();
    expect(tour.get()).toBe('welcome');
    tour.on({ type: 'next' });
    expect(tour.get()).toBe('home');
    tour.on({ type: 'skip' });
    expect(tour.get()).toBeNull();
    expect(prefs.get().tourDone).toBe(true);
    stop();
  });

  it('does not start once taken, and starts again when Settings asks', () => {
    const prefs = new AppPreferences(null);
    prefs.set({ tourDone: true });
    const tour = new TourRunner(prefs);
    const stop = tour.start();
    expect(tour.get()).toBeNull();
    tour.on({ type: 'next' });
    expect(tour.get()).toBeNull();
    prefs.set({ tourDone: false });
    expect(tour.get()).toBe('welcome');
    stop();
    prefs.set({ tourDone: true });
    prefs.set({ tourDone: false });
    expect(tour.get()).toBe('welcome');
  });

  it('finishing the last step ends the tour', () => {
    const prefs = new AppPreferences(null);
    const tour = new TourRunner(prefs);
    tour.start();
    let steps = 0;
    while (tour.get() !== null) {
      tour.on({ type: 'next' });
      steps++;
    }
    expect(steps).toBe(11);
    expect(prefs.get().tourDone).toBe(true);
  });
});
