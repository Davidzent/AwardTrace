import { type ReactNode, useRef } from 'react';

/**
 * The filter column on narrow screens: a "Filters" button with the active count that opens a full-height drawer
 * (doc 08). A native modal dialog provides the focus trap, Escape to close, and the inert page behind it.
 */
export function FiltersDrawer({ count, children }: { count: number; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const close = () => dialog.current?.close();
  return (
    <>
      <button type="button" className="filters-button" onClick={() => dialog.current?.showModal()}>
        Filters{count > 0 ? ` (${count})` : ''}
      </button>
      <dialog ref={dialog} className="filters-drawer" aria-labelledby="filters-title">
        <div className="drawer-header">
          <h2 id="filters-title">Filters</h2>
          <button type="button" onClick={close}>
            Close
          </button>
        </div>
        {children}
        <button type="button" className="drawer-done" onClick={close}>
          Show results
        </button>
      </dialog>
    </>
  );
}
