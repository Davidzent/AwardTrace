import type { ReactNode } from 'react';
import type { Condition } from '../features/status/conditions';
import styles from './StatusTile.module.css';

type Props = { title: string; condition: Condition; children: ReactNode };

/** A label, the condition as a word, and the numbers behind it. Color repeats the word; it never replaces it. */
export function StatusTile({ title, condition, children }: Props) {
  return (
    <section className={`${styles.tile} ${styles[condition.toLowerCase()]}`} aria-label={`${title}: ${condition}`}>
      <h2 className={styles.title}>{title}</h2>
      <p className={styles.condition}>{condition}</p>
      {children}
    </section>
  );
}
