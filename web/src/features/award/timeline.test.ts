import { describe, expect, it } from 'vitest';
import { centsToAmount, cumulative, niceTicks } from './timeline';

describe('cumulative', () => {
  it('sums by day, oldest first, exactly, and skips modifications without a date or amount', () => {
    const points = cumulative([
      { action_date: '2026-08-24', federal_action_obligation: '-27500.00' },
      { action_date: '2026-07-16', federal_action_obligation: '0.10' },
      { action_date: '2026-07-16', federal_action_obligation: '82499.90' },
      { action_date: '2026-09-01' },
      { federal_action_obligation: '5.00' },
    ]);

    expect(points).toEqual([
      { date: '2026-07-16', change: 8_250_000, total: 8_250_000 },
      { date: '2026-08-24', change: -2_750_000, total: 5_500_000 },
    ]);
    expect(centsToAmount(points[1]?.total ?? 0)).toBe('55000.00');
  });
});

describe('niceTicks', () => {
  it('covers zero and the data in round steps', () => {
    expect(niceTicks(5_500_000, 8_250_000, 4)).toEqual([0, 2_000_000, 4_000_000, 6_000_000, 8_000_000, 10_000_000]);
    expect(niceTicks(-300, 900, 4)).toEqual([-400, -200, 0, 200, 400, 600, 800, 1000]);
    expect(niceTicks(0, 0, 4)).toEqual([0]);
  });
});
