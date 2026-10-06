import { ApiError } from '../api/client';
import { Button } from './Button';
import styles from './ProblemMessage.module.css';

/** An API failure, described by its problem details (doc 07), with a way to try again. */
export function ProblemMessage({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const problem = error instanceof ApiError ? error.problem : undefined;
  const title =
    problem?.title ?? (error instanceof ApiError ? `The request failed (${error.status})` : 'The server could not be reached');
  return (
    <div className={styles.problem} role="alert">
      <p className={styles.title}>{title}</p>
      {problem?.detail && <p>{problem.detail}</p>}
      {problem?.errors && problem.errors.length > 0 && (
        <ul>
          {problem.errors.map((fieldError) => (
            <li key={fieldError.field}>
              {fieldError.field}: {fieldError.message}
            </li>
          ))}
        </ul>
      )}
      {onRetry && <Button onClick={onRetry}>Try again</Button>}
    </div>
  );
}
