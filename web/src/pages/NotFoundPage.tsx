import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section className="page-message">
      <title>Page not found · AwardTrace</title>
      <h1>Page not found</h1>
      <p>
        No page matches this address. Start again from the <Link to="/">search page</Link>.
      </p>
    </section>
  );
}
