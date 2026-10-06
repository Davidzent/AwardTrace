import type { ReactNode } from 'react';
import styles from './AmountList.module.css';
import { Money } from './Money';

type Item = { key: string; label: ReactNode; note?: ReactNode; amount: string | undefined };

type Props = {
  items: Item[];
  label?: string;
  /** Exact amounts, for a detail page; abbreviated otherwise (doc 08). */
  exact?: boolean;
};

/**
 * Names with amounts (doc 16): each row has the name, an optional note under it, the amount, and a bar as long as
 * the amount is against the largest in the list, so sizes compare at a glance. The amount stays in text; the bar
 * repeats it and is hidden from screen readers.
 */
export function AmountList({ items, label, exact = false }: Props) {
  const largest = Math.max(0, ...items.map((item) => Number(item.amount ?? 0)));
  return (
    <ol className={styles.list} aria-label={label}>
      {items.map((item) => (
        <li key={item.key} className={styles.item}>
          <span className={styles.label}>
            {item.label}
            {item.note && <span className={styles.note}>{item.note}</span>}
          </span>
          <Money amount={item.amount} exact={exact} />
          <span
            className={styles.bar}
            style={{ width: `${largest > 0 ? (Math.max(0, Number(item.amount ?? 0)) / largest) * 100 : 0}%` }}
            aria-hidden="true"
          />
        </li>
      ))}
    </ol>
  );
}
