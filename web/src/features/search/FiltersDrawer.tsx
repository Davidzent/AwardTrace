import { type ReactNode, useRef } from 'react';
import { Button } from '../../components/Button';
import styles from './FiltersDrawer.module.css';

/**
 * The filter column on narrow screens: a "Filters" button with the active count that opens a full-height drawer
 * (doc 08). A native modal dialog provides the focus trap, Escape to close, and the inert page behind it.
 */
export function FiltersDrawer({ count, children }: { count: number; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const close = () => dialog.current?.close();
  return (
    <>
      <Button className={styles.open} onClick={() => dialog.current?.showModal()}>
        Filters{count > 0 ? ` (${count})` : ''}
      </Button>
      <dialog ref={dialog} className={styles.drawer} aria-labelledby="filters-title">
        <div className={styles.header}>
          <h2 id="filters-title">Filters</h2>
          <Button variant="quiet" onClick={close}>
            Close
          </Button>
        </div>
        {children}
        <Button variant="primary" className={styles.done} onClick={close}>
          Show results
        </Button>
      </dialog>
    </>
  );
}
