import { ApiError } from '../api/client';

/** An API failure, described by its problem details (doc 07), with a way to try again. */
export function ProblemMessage({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const problem = error instanceof ApiError ? error.problem : undefined;
  const title =
    problem?.title ?? (error instanceof ApiError ? `The request failed (${error.status})` : 'The server could not be reached');
  return (
    <div className="problem" role="alert">
      <p className="problem-title">{title}</p>
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
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
