import { useQuery } from '@tanstack/react-query';
import { Link, NavLink, Outlet, ScrollRestoration, useLocation, useNavigate } from 'react-router';
import { api } from '../api/client';
import { Wordmark } from '../components/Wordmark';
import { SearchBox } from '../features/search/SearchBox';
import { searchHref } from '../features/search/searchUrl';
import { formatDate } from '../lib/format';
import styles from './Layout.module.css';

const REPOSITORY = 'https://github.com/Davidzent/AwardTrace';

/** The shell around every page (doc 16): header, content, and footer. */
export function Layout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#main">
        Skip to content
      </a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.brand}>
            <Wordmark />
          </Link>
          <nav aria-label="Main" className={styles.nav}>
            <NavLink to="/" end>
              Search
            </NavLink>
            <NavLink to="/status">Status</NavLink>
            <NavLink to="/about">About</NavLink>
            <a href={REPOSITORY}>GitHub</a>
          </nav>
          {/* The search page has its own, larger box. */}
          {pathname !== '/' && (
            <div className={styles.headerSearch}>
              <SearchBox compact initial="" onSearch={(q) => navigate(`/${searchHref({}, { q })}`)} />
            </div>
          )}
        </div>
      </header>
      <main id="main" className={styles.main}>
        <Outlet />
      </main>
      <Footer />
      {/* A new page, sort, or search starts at the top; Back and Forward return to where the reader was. */}
      <ScrollRestoration />
    </div>
  );
}

/** Says who runs the site and where its data comes from, with the source's last change when the API answers. */
function Footer() {
  const status = useQuery({ queryKey: ['status'], queryFn: ({ signal }) => api.status(signal) });
  const modified = status.data?.freshness?.latest_source_modified_at;
  return (
    <footer className={styles.footer}>
      <div className={styles.footerInner}>
        <p>AwardTrace is an independent project, not affiliated with USAspending.gov or any government agency.</p>
        <p className={styles.provenance}>
          Data from <a href="https://www.usaspending.gov">USAspending.gov</a>
          {modified && (
            <>
              {', last changed '}
              <time dateTime={modified}>{formatDate(modified.slice(0, 10))}</time>
            </>
          )}
        </p>
        <p>
          <a href="/api/v1/openapi.json">API</a>
          {' · '}
          <a href={REPOSITORY}>Source code</a>
        </p>
      </div>
    </footer>
  );
}
