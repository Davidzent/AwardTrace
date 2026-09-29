import type { ReactNode } from 'react';
import type { Condition } from '../features/status/conditions';

type Props = { title: string; condition: Condition; children: ReactNode };

/** A label, the condition as a word, and the numbers behind it. Color repeats the word; it never replaces it. */
export function StatusTile({ title, condition, children }: Props) {
  return (
    <section className={`panel status-tile is-${condition.toLowerCase()}`} aria-label={`${title}: ${condition}`}>
      <h2>{title}</h2>
      <p className="condition">{condition}</p>
      {children}
    </section>
  );
}
