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
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];
