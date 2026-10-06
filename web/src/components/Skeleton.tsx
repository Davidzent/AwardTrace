import styles from './Skeleton.module.css';

/** A gray block where content will appear, shaped like it (doc 08): a result card, a page title, a panel, or a tile. */
export function Skeleton({ shape }: { shape: 'card' | 'title' | 'panel' | 'tile' }) {
  return <div className={`${styles.skeleton} ${styles[shape]}`} aria-hidden="true" />;
}
