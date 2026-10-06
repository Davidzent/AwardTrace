import type { Schemas } from '../../api/client';

/** A day the award's obligations changed: the day's net change and the running total after it, in cents. */
export type Point = { date: string; change: number; total: number };

/**
 * The running total of an award's obligations by action date, oldest first. Amounts are summed in whole cents, so
 * the total is exact; modifications on the same day become one point, and ones without a date or amount are skipped.
 */
export function cumulative(transactions: Schemas['Modification'][]): Point[] {
  const byDate = new Map<string, number>();
  for (const { action_date: date, federal_action_obligation: amount } of transactions) {
    if (date && amount) {
      byDate.set(date, (byDate.get(date) ?? 0) + Math.round(Number(amount) * 100));
    }
  }
  let total = 0;
  return [...byDate]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, change]) => ({ date, change, total: (total += change) }));
}

/**
 * Round values for an axis that covers zero and the data, about `count` steps apart: 0, 2M, 4M, 6M. The first is at
 * or below the smaller of zero and `min`, and the last at or above the larger of zero and `max`.
 */
export function niceTicks(min: number, max: number, count: number): number[] {
  const low = Math.min(0, min);
  const high = Math.max(0, max);
  if (low === high) {
    return [0];
  }
  const rough = (high - low) / count;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  // d3's thresholds: the nearest of 1, 2, 5, and 10 times a power of ten, on a log scale.
  const ratio = rough / magnitude;
  const step = magnitude * (ratio >= 7.07 ? 10 : ratio >= 3.16 ? 5 : ratio >= 1.41 ? 2 : 1);
  const ticks = [Math.floor(low / step) * step];
  while ((ticks.at(-1) ?? high) < high) {
    ticks.push((ticks.at(-1) ?? 0) + step);
  }
  return ticks;
}

/** The text form of an amount in cents, as the API sends amounts: 2750000 is "27500.00". */
export function centsToAmount(cents: number): string {
  return (cents / 100).toFixed(2);
}
