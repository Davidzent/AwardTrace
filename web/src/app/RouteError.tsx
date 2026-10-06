import { isRouteErrorResponse, useRouteError } from 'react-router';
import { Button } from '../components/Button';

/** A page that failed to render. Failed API requests are shown inside the page instead, with a retry. */
export function RouteError() {
  const error = useRouteError();
  const detail = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : error instanceof Error
      ? error.message
      : 'An unexpected error occurred.';
  return (
    <section className="page-message" role="alert">
      <title>Something went wrong · AwardTrace</title>
      <h1>Something went wrong</h1>
      <p>{detail}</p>
      <Button onClick={() => window.location.reload()}>Reload the page</Button>
    </section>
  );
}
