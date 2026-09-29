import type { RouteObject } from 'react-router';
import { NotFoundPage } from '../pages/NotFoundPage';
import { SearchPage } from '../pages/SearchPage';
import { Layout } from './Layout';
import { RouteError } from './RouteError';

/** Every page renders inside the layout; an error in one replaces the page but keeps the header. */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      {
        errorElement: <RouteError />,
        children: [
          { index: true, element: <SearchPage /> },
          // Loaded on first visit, so the search page's bundle stays small (doc 08).
          {
            path: 'awards/:awardId',
            lazy: async () => ({ Component: (await import('../pages/AwardPage')).AwardPage }),
          },
          {
            path: 'recipients/:uei',
            lazy: async () => ({ Component: (await import('../pages/RecipientPage')).RecipientPage }),
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
