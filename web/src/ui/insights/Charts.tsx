import { useId, useState, type CSSProperties } from 'react';
import { str } from '../../core/i18n';
import './insights.css';

/**
 * Charts for Your kitchen (InsightsCharts in the app). Thrown away is never told apart by color alone (DESIGN 6.4): it
 * is dashed in the ring and striped and outlined in bars, and every chart has a text legend and a spoken summary. Used
 * is primary, thrown away tertiary on tertiaryContainer, the ring's track surfaceVariant.
 *
 * The charts grow with CSS animations when they first show (insights.css), not with React state on every frame
 * (after v1, review WEB-13: opening the screen re-rendered every chart about 47 times). Reduce motion turns them off.
 */
const USED = 'var(--md-sys-color-primary)';
const THROWN = 'var(--md-sys-color-tertiary)';
const TRACK = 'var(--md-sys-color-surface-variant)';

const reduceMotion = () => {
  try {
    return matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
};

/** An SVG arc from [start] degrees (0 is three o'clock), [sweep] degrees clockwise. */
function arc(c: number, r: number, start: number, sweep: number): string {
  const point = (deg: number) => {
    const rad = (deg * Math.PI) / 180;
    return `${c + r * Math.cos(rad)} ${c + r * Math.sin(rad)}`;
  };
  if (sweep >= 359.99) {
    return `M ${point(start)} A ${r} ${r} 0 1 1 ${point(start + 180)} A ${r} ${r} 0 1 1 ${point(start + 359.99)}`;
  }
  return `M ${point(start)} A ${r} ${r} 0 ${sweep > 180 ? 1 : 0} 1 ${point(start + sweep)}`;
}

/** The colors of a [UsageRing]: the used arc, the dashed thrown-away arc and the track under them. */
export type RingColors = { used: string; thrown: string; track: string };

/**
 * On the primary month card of Home and Your kitchen (DESIGN 19.8; UsageRingColors.onPrimary in the app): used in
 * primaryContainer, thrown away in onPrimary dashes, the track onPrimary at 24% over primary.
 */
export const ON_PRIMARY_RING: RingColors = {
  used: 'var(--md-sys-color-primary-container)',
  thrown: 'var(--md-sys-color-on-primary)',
  track: 'color-mix(in srgb, var(--md-sys-color-on-primary) 24%, var(--md-sys-color-primary))',
};

/**
 * The share used as a ring: a solid arc for used, a dashed one for thrown away, and [centerText] in the middle. Empty
 * months show only the track. The arcs are drawn once at their final size and revealed by a mask that sweeps round
 * (a CSS animation); once it ends the mask is dropped, so the ring is drawn exactly as without it.
 */
export function UsageRing({
  used,
  thrown,
  centerText,
  description,
  size,
  small = false,
  colors = { used: USED, thrown: THROWN, track: TRACK },
}: {
  used: number;
  thrown: number;
  centerText: string;
  description: string;
  size: number;
  small?: boolean;
  colors?: RingColors;
}) {
  // A new key draws (and grows) the ring again, as the app's growth restarts when the counts change.
  return (
    <RingDrawing
      key={`${used}/${thrown}`}
      used={used}
      thrown={thrown}
      centerText={centerText}
      description={description}
      size={size}
      small={small}
      colors={colors}
    />
  );
}

function RingDrawing({
  used,
  thrown,
  centerText,
  description,
  size,
  small,
  colors,
}: {
  used: number;
  thrown: number;
  centerText: string;
  description: string;
  size: number;
  small: boolean;
  colors: RingColors;
}) {
  const mask = `sweep${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const total = used + thrown;
  const [grown, setGrown] = useState(() => total === 0 || reduceMotion());
  const stroke = size * 0.12;
  const r = (size - stroke) / 2;
  const c = size / 2;
  const usedSweep = total > 0 ? (360 * used) / total : 0;
  const thrownSweep = total > 0 ? (360 * thrown) / total : 0;
  const dash = stroke * 0.6;
  const length = 2 * Math.PI * r;
  return (
    <div className="sn-ring" role="img" aria-label={description} style={{ width: size, height: size }}>
      <svg width={size} height={size} aria-hidden>
        {!grown && (
          <defs>
            <mask id={mask}>
              <circle
                className="sn-ring__sweep"
                cx={c}
                cy={c}
                r={r}
                fill="none"
                stroke="white"
                strokeWidth={stroke + 2}
                strokeDasharray={`${length} ${length}`}
                transform={`rotate(-90 ${c} ${c})`}
                style={{ '--sn-ring-length': `${length}px` } as CSSProperties}
                onAnimationEnd={() => setGrown(true)}
              />
            </mask>
          </defs>
        )}
        <circle cx={c} cy={c} r={r} fill="none" stroke={colors.track} strokeWidth={stroke} />
        <g mask={grown ? undefined : `url(#${mask})`}>
          {usedSweep > 0 && (
            <path
              d={arc(c, r, -90, usedSweep)}
              fill="none"
              stroke={colors.used}
              strokeWidth={stroke}
              strokeLinecap="butt"
            />
          )}
          {thrownSweep > 0 && (
            <path
              d={arc(c, r, -90 + usedSweep, thrownSweep)}
              fill="none"
              stroke={colors.thrown}
              strokeWidth={stroke}
              strokeDasharray={`${dash} ${dash / 2}`}
            />
          )}
        </g>
      </svg>
      {centerText && <span className={small ? 't-title-medium' : 't-headline-medium'}>{centerText}</span>}
    </div>
  );
}

/** One month in [MonthBars]; [description] is what VoiceOver reads for it. */
export type MonthBar = { label: string; used: number; thrown: number; description: string };

/**
 * Used and thrown away per month, side by side (DESIGN 19.8): solid bars for used, striped outlined bars for thrown
 * away, each with its number above and the month below. Each month is a tab that shows its details; the chosen one
 * has a secondaryContainer background and a heavier label, and VoiceOver reads each month on its own. The bars keep
 * their final layout and grow in (a CSS transform), so the numbers sit in their final place from the start.
 */
export function MonthBars({
  bars,
  selected,
  onSelect,
  height = 132,
}: {
  bars: MonthBar[];
  selected: number;
  onSelect: (index: number) => void;
  height?: number;
}) {
  const max = Math.max(1, ...bars.map((b) => Math.max(b.used, b.thrown)));
  const barHeight = (value: number) => Math.max(2, height * (value / max));
  return (
    // Keyed on the counts, so new counts grow in again.
    <div className="sn-month-bars" role="tablist" key={bars.map((b) => `${b.used}/${b.thrown}`).join(',')}>
      {bars.map((bar, index) => {
        const isSelected = index === selected;
        return (
          <button
            key={bar.label + index}
            type="button"
            role="tab"
            aria-selected={isSelected}
            aria-label={bar.description}
            className={`sn-month-bar m3-state ${isSelected ? 'sn-month-bar--selected' : ''}`}
            data-testid={`month_bar_${index}`}
            onClick={() => onSelect(index)}
          >
            <span className="sn-month-bar__pair" aria-hidden>
              <span className="sn-bar">
                <span className="t-chip-label">{bar.used}</span>
                <span className="sn-bar__solid sn-grow-up" style={{ height: barHeight(bar.used) }} />
              </span>
              <span className="sn-bar">
                <span className="t-chip-label">{bar.thrown}</span>
                <span className="sn-bar__striped sn-grow-up" style={{ height: barHeight(bar.thrown) }} />
              </span>
            </span>
            <span className="t-row-supporting sn-month-bar__label">{bar.label}</span>
          </button>
        );
      })}
    </div>
  );
}

/** A small swatch and its label, for chart legends: solid for used, striped and outlined for thrown away. */
export function LegendItem({ text, thrown }: { text: string; thrown: boolean }) {
  return (
    <span className="sn-legend t-row-supporting c-on-surface-variant">
      <span className={`sn-legend__swatch ${thrown ? 'sn-bar__striped' : 'sn-bar__solid'}`} />
      {text}
    </span>
  );
}

/**
 * The month card's legend on primary (Home and Your kitchen, DESIGN 19.8; OnPrimaryLegend in the app): "Used 3" with
 * a solid primaryContainer swatch and "Thrown away 1" with an outlined one, so the two differ by more than color.
 */
export function OnPrimaryLegend({ used, thrown }: { used: number; thrown: number }) {
  return (
    <span className="sn-kitchen-card__legend t-row-supporting">
      <span className="sn-kitchen-card__entry">
        <span className="sn-kitchen-card__swatch" />
        {`${str('insights_legend_used')} ${used}`}
      </span>
      <span className="sn-kitchen-card__entry">
        <span className="sn-kitchen-card__swatch sn-kitchen-card__swatch--outlined" />
        {`${str('insights_legend_thrown')} ${thrown}`}
      </span>
    </span>
  );
}

/**
 * Why things were thrown away (DESIGN 19.8): each reason's name and count over a striped bar as long as its share of
 * the longest, growing in from the start.
 */
export function ReasonBars({ reasons }: { reasons: [string, number][] }) {
  const max = Math.max(1, ...reasons.map(([, n]) => n));
  return (
    <div className="sn-reason-bars" key={reasons.map(([l, n]) => `${l}/${n}`).join(',')}>
      {reasons.map(([label, count]) => (
        <div key={label} className="sn-reason-bars__reason">
          <div className="sn-reason-bars__head t-reason">
            <span>{label}</span>
            <span className="sn-reason-bars__count">{count}</span>
          </div>
          <span
            className="sn-bar__striped sn-reason-bars__bar sn-grow-right"
            style={{ width: `${(count / max) * 100}%` }}
            aria-hidden
          />
        </div>
      ))}
    </div>
  );
}
