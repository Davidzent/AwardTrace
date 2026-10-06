import styles from './Wordmark.module.css';

/**
 * The name and its mark: a money trail stepping down from an agency to a prime contractor and on to a
 * subcontractor, ending in gold where the money lands. public/favicon.svg draws the same mark.
 */
export function Wordmark() {
  return (
    <span className={styles.wordmark}>
      <svg className={styles.mark} viewBox="0 0 28 28" aria-hidden="true" focusable="false">
        <rect className={styles.ground} width="28" height="28" rx="7" />
        <path className={styles.trail} d="M8 8.5h6v11h5" />
        <circle className={styles.source} cx="8" cy="8.5" r="2.5" />
        <circle className={styles.destination} cx="20" cy="19.5" r="3" />
      </svg>
      AwardTrace
    </span>
  );
}
