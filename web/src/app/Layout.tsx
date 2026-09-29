import { Link, NavLink, Outlet, ScrollRestoration } from 'react-router';

export function Layout() {
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <Link to="/" className="brand">
          AwardTrace
        </Link>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Search
          </NavLink>
          <NavLink to="/status">Status</NavLink>
          <NavLink to="/about">About</NavLink>
          <a href="https://github.com/Davidzent/awardtrace">GitHub</a>
        </nav>
      </header>
      <main id="main">
        <Outlet />
      </main>
      {/* A new page, sort, or search starts at the top; Back and Forward return to where the reader was. */}
      <ScrollRestoration />
    </>
  );
}
