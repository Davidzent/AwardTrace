import { type KeyboardEvent, type PointerEvent, useLayoutEffect, useRef, useState } from 'react';
import type { Schemas } from '../../api/client';
import { Button } from '../../components/Button';
import { Money } from '../../components/Money';
import panel from '../../components/Panel.module.css';
import table from '../../components/Table.module.css';
import { formatDate, formatMoney, formatMoneyShort } from '../../lib/format';
import { centsToAmount, cumulative, niceTicks, type Point } from './timeline';
import styles from './TimelineChart.module.css';

const HEIGHT = 200;
const MARGIN = { top: 12, right: 64, bottom: 28, left: 56 };
/** The tooltip's width in pixels: 11rem, as in the stylesheet. */
const TOOLTIP_WIDTH = 176;

const monthDays = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
const monthYears = new Intl.DateTimeFormat('en-US', { month: 'short', year: 'numeric', timeZone: 'UTC' });

/**
 * The award's obligations over time (doc 08): the running total as a step line, since it changes only on the days a
 * modification is signed, with a table of the same numbers a button away. Hover, touch, or the arrow keys read a day.
 * It needs every modification and at least two days to say anything, so otherwise the table below stands alone.
 */
export function TimelineChart({ transactions }: { transactions: Schemas['Modification'][] }) {
  const points = cumulative(transactions);
  const [asTable, setAsTable] = useState(false);
  if (points.length < 2) {
    return null;
  }
  return (
    <section className={panel.panel} aria-labelledby="award-timeline">
      <div className={styles.header}>
        <h2 id="award-timeline">Obligations over time</h2>
        <Button variant="quiet" onClick={() => setAsTable(!asTable)}>
          {asTable ? 'Show as chart' : 'Show as table'}
        </Button>
      </div>
      {asTable ? <TimelineTable points={points} /> : <Plot points={points} />}
    </section>
  );
}

function Plot({ points }: { points: Point[] }) {
  const [box, width] = useWidth();
  const [active, setActive] = useState<number | null>(null);
  const first = points[0] as Point;
  const last = points.at(-1) as Point;

  const start = Date.parse(first.date);
  const span = Date.parse(last.date) - start;
  const plotWidth = Math.max(width - MARGIN.left - MARGIN.right, 1);
  const x = (date: string) => MARGIN.left + ((Date.parse(date) - start) / span) * plotWidth;
  const yTicks = niceTicks(Math.min(...points.map((point) => point.total)), last.total, 4);
  const low = yTicks[0] ?? 0;
  const high = yTicks.at(-1) ?? 0;
  const y = (cents: number) => MARGIN.top + ((high - cents) / (high - low || 1)) * (HEIGHT - MARGIN.top - MARGIN.bottom);
  const baseline = y(Math.max(low, 0));

  // Up from zero on the first day, then flat until each later day, where it steps to that day's total.
  const line = points.reduce(
    (path, point) => `${path} H ${x(point.date)} V ${y(point.total)}`,
    `M ${x(first.date)} ${y(0)}`,
  );
  const area = `${line} V ${baseline} H ${x(first.date)} Z`;
  const tickCount = width < 480 ? 3 : 5;
  const dateFormat = span > 300 * 86_400_000 ? monthYears : monthDays;
  const xTicks = Array.from({ length: tickCount }, (_, index) => start + (span * index) / (tickCount - 1));

  // The crosshair snaps to the day nearest the pointer, so the reader aims at a date, never at the line.
  const nearest = (event: PointerEvent<SVGSVGElement>) => {
    const pointer = event.clientX - event.currentTarget.getBoundingClientRect().left;
    let best = 0;
    points.forEach((point, index) => {
      if (Math.abs(x(point.date) - pointer) < Math.abs(x(points[best]?.date ?? '') - pointer)) {
        best = index;
      }
    });
    setActive(best);
  };
  const step = (event: KeyboardEvent) => {
    const moves: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1 };
    if (event.key in moves) {
      event.preventDefault();
      setActive(Math.min(Math.max((active ?? points.length - 1) + (moves[event.key] ?? 0), 0), points.length - 1));
    } else if (event.key === 'Escape') {
      setActive(null);
    }
  };
  const shown = active === null ? undefined : points[active];

  return (
    <div
      ref={box}
      className={styles.plot}
      role="img"
      tabIndex={0}
      aria-label={`Running total of obligations from ${formatDate(first.date)} to ${formatDate(last.date)}, ending at ${formatMoney(centsToAmount(last.total))}. Use the arrow keys to read each day, or show it as a table.`}
      onFocus={() => setActive(points.length - 1)}
      onBlur={() => setActive(null)}
      onKeyDown={step}
    >
      {width > 0 && (
        <svg width={width} height={HEIGHT} onPointerMove={nearest} onPointerDown={nearest} onPointerLeave={() => setActive(null)}>
          {yTicks.map((tick) => (
            <g key={tick}>
              <line className={styles.grid} x1={MARGIN.left} x2={width - MARGIN.right} y1={y(tick)} y2={y(tick)} />
              <text className={styles.axis} x={MARGIN.left - 8} y={y(tick)} textAnchor="end" dominantBaseline="middle">
                {formatMoneyShort(centsToAmount(tick))}
              </text>
            </g>
          ))}
          {xTicks.map((time, index) => (
            <text
              key={time}
              className={styles.axis}
              x={MARGIN.left + (plotWidth * index) / (tickCount - 1)}
              y={HEIGHT - 8}
              textAnchor={index === 0 ? 'start' : index === tickCount - 1 ? 'end' : 'middle'}
            >
              {dateFormat.format(time)}
            </text>
          ))}
          <path className={styles.area} d={area} />
          <path className={styles.line} d={line} />
          <text className={styles.end} x={x(last.date) + 8} y={y(last.total)} dominantBaseline="middle">
            {formatMoneyShort(centsToAmount(last.total))}
          </text>
          {shown && (
            <g>
              <line className={styles.crosshair} x1={x(shown.date)} x2={x(shown.date)} y1={MARGIN.top} y2={baseline} />
              <circle className={styles.marker} cx={x(shown.date)} cy={y(shown.total)} r={4} />
            </g>
          )}
        </svg>
      )}
      {shown && (
        <div
          className={styles.tooltip}
          // Beside the crosshair, on whichever side has more room, and never past the chart's edges.
          style={{ left: tooltipLeft(x(shown.date), width) }}
          aria-hidden="true"
        >
          <strong>{formatMoney(centsToAmount(shown.total))}</strong>
          <span>{formatDate(shown.date)}</span>
          <span>{signed(shown.change)}</span>
        </div>
      )}
    </div>
  );
}

function TimelineTable({ points }: { points: Point[] }) {
  return (
    <div className={table.scroll} tabIndex={0} role="region" aria-label="Obligations over time table">
      <table className={table.table}>
        <caption className="visually-hidden">Obligations over time, oldest first</caption>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col" className={table.num}>
              Change
            </th>
            <th scope="col" className={table.num}>
              Running total
            </th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.date}>
              <td className={table.nowrap}>{formatDate(point.date)}</td>
              <td className={table.num}>{signed(point.change)}</td>
              <td className={table.num}>
                <Money amount={centsToAmount(point.total)} exact />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function tooltipLeft(crosshair: number, width: number): number {
  const left = crosshair > width / 2 ? crosshair - 12 - TOOLTIP_WIDTH : crosshair + 12;
  return Math.min(Math.max(left, 0), width - TOOLTIP_WIDTH);
}

/** +$82,500.00 or -$27,500.00: a change always shows its direction. */
function signed(cents: number): string {
  return `${cents > 0 ? '+' : ''}${formatMoney(centsToAmount(cents))}`;
}

/** The element's width in pixels, kept current, so the chart draws at its real size and its text never scales. */
function useWidth() {
  const ref = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => setWidth(entry?.contentRect.width ?? 0));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return [ref, width] as const;
}
