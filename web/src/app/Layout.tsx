import { Link, NavLink, Outlet } from 'react-router';

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
          <a href="https://github.com/Davidzent/awardtrace">GitHub</a>
        </nav>
      </header>
      <main id="main">
        <Outlet />
      </main>
    </>
  );
}
