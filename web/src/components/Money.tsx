import { formatMoney, formatMoneyShort } from '../lib/format';

/** Abbreviated in cards and lists, exact on detail pages (doc 08). A missing amount renders as a dash. */
export function Money({ amount, exact = false }: { amount: string | undefined; exact?: boolean }) {
  if (amount === undefined) {
    return <span className="money">—</span>;
  }
  return (
    <span className="money" title={exact ? undefined : formatMoney(amount)}>
      {exact ? formatMoney(amount) : formatMoneyShort(amount)}
    </span>
  );
}
