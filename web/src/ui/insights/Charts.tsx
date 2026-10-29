import { useEffect, useId, useState, type CSSProperties } from 'react';
import './insights.css';

/**
 * Charts for Insights (InsightsCharts in the app). Thrown away is never told apart by color alone (DESIGN 6.4): it is
 * dashed in the ring and striped in bars, and every chart has a text legend and a spoken summary. Used is primary,
 * thrown away tertiary, the ring's track surfaceVariant.
 */
const USED = 'var(--md-sys-color-primary)';
const THROWN = 'var(--md-sys-color-tertiary)';
const TRACK = 'var(--md-sys-color-surface-variant)';

/** FastOutSlowInEasing. */
function fastOutSlowIn(t: number): number {
  // cubic-bezier(0.4, 0, 0.2, 1), solved for x by bisection.
  const bezier = (p1: number, p2: number, s: number) => 3 * p1 * s * (1 - s) ** 2 + 3 * p2 * s * s * (1 - s) + s ** 3;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 20; i++) {
    const mid = (lo + hi) / 2;
    if (bezier(0.4, 0.2, mid) < t) lo = mid;
    else hi = mid;
  }
  return bezier(0, 1, (lo + hi) / 2);
}

const reduceMotion = () => {
  try {
    return matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
};

/** 0 to 1 over 700 ms when [key] first shows, or at once with reduce motion on. */
function useGrow(key: string): number {
  const [state, setState] = useState(() => ({ key, value: reduceMotion() ? 1 : 0 }));
  const value = state.key === key ? state.value : reduceMotion() ? 1 : 0;
  useEffect(() => {
    if (reduceMotion()) return;
    let frame = 0;
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / 700);
      setState({ key, value: fastOutSlowIn(t) });
      if (t < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [key]);
  return value;
}

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

/**
 * The share used as a ring: a solid arc for used, a dashed one for thrown away, and [centerText] in the middle. Empty
 * months show only the track.
 */
export function UsageRing({
  used,
  thrown,
  centerText,
  description,
  size,
  small = false,
}: {
  used: number;
  thrown: number;
  centerText: string;
  description: string;
  size: number;
  small?: boolean;
}) {
  const grow = useGrow(`${used}/${thrown}`);
  const stroke = size * 0.12;
  const r = (size - stroke) / 2;
  const c = size / 2;
  const total = used + thrown;
  const usedSweep = total > 0 ? ((360 * used) / total) * grow : 0;
  const thrownSweep = total > 0 ? ((360 * thrown) / total) * grow : 0;
  const dash = stroke * 0.6;
  return (
    <div className="sn-ring" role="img" aria-label={description} style={{ width: size, height: size }}>
      <svg width={size} height={size} aria-hidden>
        <circle cx={c} cy={c} r={r} fill="none" stroke={TRACK} strokeWidth={stroke} />
        {usedSweep > 0 && (
          <path d={arc(c, r, -90, usedSweep)} fill="none" stroke={USED} strokeWidth={stroke} strokeLinecap="butt" />
        )}
        {thrown > 0 && thrownSweep > 0 && (
          <path
            d={arc(c, r, -90 + usedSweep, thrownSweep)}
            fill="none"
            stroke={THROWN}
            strokeWidth={stroke}
            strokeDasharray={`${dash} ${dash / 2}`}
          />
        )}
      </svg>
      <span className={small ? 't-title-medium' : 't-headline-medium'}>{centerText}</span>
    </div>
  );
}

/** A rectangle with diagonal stripes and an outline: the "thrown away" pattern. */
function Striped({ width, height, color }: { width: number; height: number; color: string }) {
  const id = `stripes${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  return (
    <svg width={width} height={height} overflow="visible" aria-hidden className="sn-striped">
      <defs>
        <pattern id={id} width={5} height={5} patternUnits="userSpaceOnUse">
          <path d="M -1 1 L 1 -1 M 0 5 L 5 0 M 4 6 L 6 4" stroke={color} strokeWidth={2} />
        </pattern>
      </defs>
      <rect x={0} y={0} width={width} height={height} fill={`url(#${id})`} />
      <rect x={0} y={0} width={width} height={height} rx={4} fill="none" stroke={color} strokeWidth={2} />
    </svg>
  );
}

function Solid({ width, height, color, radius }: { width: number; height: number; color: string; radius: number }) {
  return (
    <svg width={width} height={height} aria-hidden>
      <rect width={width} height={height} rx={radius} fill={color} />
    </svg>
  );
}

/** One month in [MonthBars]; [description] is what VoiceOver reads for it. */
export type MonthBar = { label: string; used: number; thrown: number; description: string };

/**
 * Used and thrown away per month, side by side: solid bars for used, striped outlined bars for thrown away, each with
 * its number above and the month below. Each month can be tapped to show its details; the selected one has a tinted
 * background and a bold label, and VoiceOver reads each month on its own.
 */
export function MonthBars({
  bars,
  selected,
  onSelect,
  height = 160,
}: {
  bars: MonthBar[];
  selected: number;
  onSelect: (index: number) => void;
  height?: number;
}) {
  const grow = useGrow(JSON.stringify(bars));
  const max = Math.max(1, ...bars.map((b) => Math.max(b.used, b.thrown)));
  const barHeight = (value: number) => Math.max(2, height * (value / max) * grow);
  return (
    <div className="sn-month-bars" role="tablist">
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
            <span className="sn-month-bar__pair">
              <span className="sn-bar">
                <span className="t-label-small c-on-surface-variant">{bar.used}</span>
                <Solid width={14} height={barHeight(bar.used)} color={USED} radius={4} />
              </span>
              <span className="sn-bar">
                <span className="t-label-small c-on-surface-variant">{bar.thrown}</span>
                <Striped width={14} height={barHeight(bar.thrown)} color={THROWN} />
              </span>
            </span>
            <span className="t-label-medium sn-month-bar__label">{bar.label}</span>
          </button>
        );
      })}
    </div>
  );
}

/** A small swatch and its label, for chart legends. */
export function LegendItem({ text, thrown }: { text: string; thrown: boolean }) {
  return (
    <span className="sn-legend">
      {thrown ? (
        <Striped width={14} height={14} color={THROWN} />
      ) : (
        <Solid width={14} height={14} color={USED} radius={3} />
      )}
      <span className="t-body-medium">{text}</span>
    </span>
  );
}

/** Horizontal striped bars for why things were thrown away, longest first, each labeled with its count. */
export function ReasonBars({ reasons }: { reasons: [string, number][] }) {
  const grow = useGrow(JSON.stringify(reasons));
  const max = Math.max(1, ...reasons.map(([, n]) => n));
  return (
    <div className="sn-reason-bars">
      {reasons.map(([label, count]) => (
        <div key={label}>
          <div className="sn-reason-bars__head">
            <span className="t-body-medium">{label}</span>
            <span className="t-label-large">{count}</span>
          </div>
          <div className="sn-reason-bars__track">
            <ReasonBar share={(count / max) * grow} />
          </div>
        </div>
      ))}
    </div>
  );
}

/** The bar's width follows its track, so it is measured once laid out. */
function ReasonBar({ share }: { share: number }) {
  const [width, setWidth] = useState(0);
  const [node, setNode] = useState<HTMLDivElement | null>(null);
  useEffect(() => {
    if (!node) return;
    const observer = new ResizeObserver(() => setWidth(node.clientWidth));
    observer.observe(node);
    return () => observer.disconnect();
  }, [node]);
  const style: CSSProperties = { height: 8 };
  return (
    <div ref={setNode} style={style}>
      {width > 0 && <Striped width={Math.max(2, width * share)} height={8} color={THROWN} />}
    </div>
  );
}
