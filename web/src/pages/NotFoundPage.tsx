import { ExampleSearches } from '../features/search/ExampleSearches';

/** An unknown address keeps the layout, so the header's search box is right there; examples give a place to start. */
export function NotFoundPage() {
  return (
    <section className="page-message">
      <title>Page not found · AwardTrace</title>
      <h1>Page not found</h1>
      <p>No page matches this address. Search with the box at the top of the page, or start from one of these.</p>
      <ExampleSearches />
    </section>
  );
}
