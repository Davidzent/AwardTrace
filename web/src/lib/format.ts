const exactMoney = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

// At most one decimal and three significant digits, whichever is coarser: $4.8M, $612K, $12.5K, $950.
const shortMoney = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 1,
  maximumSignificantDigits: 3,
  roundingPriority: 'lessPrecision',
});

const counts = new Intl.NumberFormat('en-US');

const dates = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });

/**
 * $4,812,000.00. The API sends money as a decimal string, which Intl formats exactly rather than through a float.
 */
export function formatMoney(amount: string): string {
  return exactMoney.format(amount as Intl.StringNumericLiteral);
}

/** $4.8M, for cards and lists (doc 08). */
export function formatMoneyShort(amount: string): string {
  return shortMoney.format(amount as Intl.StringNumericLiteral);
}

export function formatCount(count: number): string {
  return counts.format(count);
}

/** Sep 28, 2026, from an API date such as 2026-09-28, whatever the reader's time zone. */
export function formatDate(date: string): string {
  return dates.format(new Date(`${date}T00:00:00Z`));
}
