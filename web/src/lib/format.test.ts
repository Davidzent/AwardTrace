import { describe, expect, it } from 'vitest';
import { formatCount, formatDate, formatMoney, formatMoneyShort } from './format';

describe('format', () => {
  it('abbreviates money for cards and lists', () => {
    expect(formatMoneyShort('4812000.00')).toBe('$4.8M');
    expect(formatMoneyShort('612345.00')).toBe('$612K');
    expect(formatMoneyShort('-12500.00')).toBe('-$12.5K');
    expect(formatMoneyShort('950.00')).toBe('$950');
  });

  it('keeps every cent of a large amount on detail pages', () => {
    expect(formatMoney('9007199254740993.01')).toBe('$9,007,199,254,740,993.01');
    expect(formatMoney('-27500.00')).toBe('-$27,500.00');
  });

  it('formats counts and dates', () => {
    expect(formatCount(12481)).toBe('12,481');
    expect(formatDate('2026-09-28')).toBe('Sep 28, 2026');
  });
});
