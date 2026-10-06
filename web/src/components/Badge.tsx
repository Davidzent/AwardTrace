import type { ReactNode } from 'react';
import styles from './Badge.module.css';

/** A short label beside a value, such as a category or "Deobligation". */
export function Badge({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={className ? `${styles.badge} ${className}` : styles.badge}>{children}</span>;
}
