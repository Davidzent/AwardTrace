import { useId } from 'react';
import { Link } from 'react-router';
import styles from './ExampleSearches.module.css';
import { searchHref } from './searchUrl';

/** Each one returns hundreds of awards in the Agriculture data. */
const EXAMPLES = ['wildland fire', 'helicopter', 'beef', 'janitorial', 'road maintenance'];

/** Searches to start from, as links to their results (doc 16). */
export function ExampleSearches() {
  const labelId = useId();
  return (
    <div className={styles.examples}>
      <span className="muted" id={labelId}>
        Try
      </span>
      <ul aria-labelledby={labelId}>
        {EXAMPLES.map((q) => (
          <li key={q}>
            <Link to={`/${searchHref({}, { q })}`}>{q}</Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
