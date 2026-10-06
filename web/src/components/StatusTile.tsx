import type { ReactNode } from 'react';
import type { Condition } from '../features/status/conditions';
import { CheckCircleIcon, MinusCircleIcon, QuestionCircleIcon, WarningIcon, XCircleIcon } from './icons';
import styles from './StatusTile.module.css';

const ICONS: Record<Condition, ReactNode> = {
  OK: <CheckCircleIcon />,
  Degraded: <WarningIcon />,
  Down: <XCircleIcon />,
  Off: <MinusCircleIcon />,
  Unknown: <QuestionCircleIcon />,
};

type Props = { title: string; condition: Condition; children: ReactNode };

/**
 * A label, the condition as a word with an icon, and the numbers behind it (doc 16). The icon and the color repeat
 * the word; they never replace it.
 */
export function StatusTile({ title, condition, children }: Props) {
  return (
    <section className={`${styles.tile} ${styles[condition.toLowerCase()]}`} aria-label={`${title}: ${condition}`}>
      <h2 className={styles.title}>{title}</h2>
      <p className={styles.condition}>
        {ICONS[condition]}
        {condition}
      </p>
      {children}
    </section>
  );
}
