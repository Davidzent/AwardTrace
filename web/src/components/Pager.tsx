import { Link } from 'react-router';
import styles from './Pager.module.css';

/** The pages to list around the current one, with null for a gap: 1 … 4 5 6 … 125. A one-page gap shows the page. */
export function pageItems(current: number, last: number): (number | null)[] {
  const pages = [...new Set([1, current - 1, current, current + 1, last])]
    .filter((page) => page >= 1 && page <= last)
    .sort((a, b) => a - b);
  const items: (number | null)[] = [];
  let previous = 0;
  for (const page of pages) {
    if (page - previous === 2) {
      items.push(previous + 1);
    } else if (page - previous > 2) {
      items.push(null);
    }
    items.push(page);
    previous = page;
  }
  return items;
}

/** Links rather than buttons, so every page has an address. Full on desktop, compact on mobile (doc 08). */
export function Pager({ page, lastPage, href }: { page: number; lastPage: number; href: (page: number) => string }) {
  if (lastPage <= 1) {
    return null;
  }
  return (
    <nav className={styles.pager} aria-label="Pages">
      {page > 1 ? (
        <Link to={href(page - 1)} rel="prev">
          Previous
        </Link>
      ) : (
        <span className={styles.disabled} aria-disabled="true">Previous</span>
      )}
      <ol className={styles.pages}>
        {pageItems(page, lastPage).map((item, index) => (
          <li key={item ?? `gap-${index}`}>
            {item === null ? (
              <span aria-hidden="true">…</span>
            ) : item === page ? (
              <span aria-current="page">{item}</span>
            ) : (
              <Link to={href(item)} aria-label={`Page ${item}`}>
                {item}
              </Link>
            )}
          </li>
        ))}
      </ol>
      <span className={styles.compact}>
        Page {page} of {lastPage}
      </span>
      {page < lastPage ? (
        <Link to={href(page + 1)} rel="next">
          Next
        </Link>
      ) : (
        <span className={styles.disabled} aria-disabled="true">Next</span>
      )}
    </nav>
  );
}
