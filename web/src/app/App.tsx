import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { ApiError } from '../api/client';
import { routes } from './routes';
import './styles.css';

const router = createBrowserRouter(routes);

/**
 * Server data lives only in TanStack Query (doc 13). The API caches search for a minute, so the client does too.
 * Client errors, including 429, are never retried: repeating the request can't change the answer.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      retry: (failures, error) => failures < 2 && !(error instanceof ApiError && error.status < 500),
    },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
