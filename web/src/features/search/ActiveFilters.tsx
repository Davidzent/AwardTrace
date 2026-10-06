import { Link } from 'react-router';
import styles from './ActiveFilters.module.css';
import type { Chip } from './filters';

/** Removable chips for every active filter, and "Clear all" (doc 08). */
export function ActiveFilters({ chips, clearHref }: { chips: Chip[]; clearHref: string }) {
  if (chips.length === 0) {
    return null;
  }
  return (
    <div className={styles.active}>
      <span className={styles.label}>Active:</span>
      <ul className={styles.chips}>
        {chips.map((chip) => (
          <li key={chip.key}>
            <Link to={chip.href} className={styles.chip} aria-label={`Remove filter: ${chip.label}`}>
              {chip.label}
              <span className={styles.remove} aria-hidden="true">
                ×
              </span>
            </Link>
          </li>
        ))}
      </ul>
      <Link to={clearHref} className={styles.clear}>
        Clear all
      </Link>
    </div>
  );
}
