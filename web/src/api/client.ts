import type { components, operations } from './schema';

/** Response and model types, generated from backend/openapi.json. */
export type Schemas = components['schemas'];
export type SearchQuery = NonNullable<operations['search']['parameters']['query']>;
export type Problem = Schemas['Problem'];

/** A failed request. The API describes every failure as RFC 9457 problem details (doc 07). */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | undefined;

  constructor(status: number, problem: Problem | undefined) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

type Query = Record<string, string | number | readonly (string | number)[] | undefined>;

/** List values repeat the parameter, as in state=ID&state=VA; undefined and empty values are left out. */
async function get<T>(path: string, query: Query = {}, signal?: AbortSignal): Promise<T> {
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    for (const item of typeof value === 'object' ? value : [value]) {
      if (item !== undefined && item !== '') {
        params.append(name, String(item));
      }
    }
  }
  const url = params.size > 0 ? `${path}?${params}` : path;
  const response = await fetch(url, signal ? { signal } : {});
  if (!response.ok) {
    const isProblem = response.headers.get('Content-Type')?.startsWith('application/problem+json') ?? false;
    const problem = isProblem ? ((await response.json()) as Problem) : undefined;
    throw new ApiError(response.status, problem);
  }
  return (await response.json()) as T;
}

const segment = encodeURIComponent;

export const api = {
  searchAwards: (query: SearchQuery, signal?: AbortSignal) =>
    get<Schemas['SearchResults']>('/api/v1/awards/search', query, signal),
  award: (awardId: string, signal?: AbortSignal) =>
    get<Schemas['AwardDetail']>(`/api/v1/awards/${segment(awardId)}`, {}, signal),
  recipient: (uei: string, signal?: AbortSignal) =>
    get<Schemas['RecipientDetail']>(`/api/v1/recipients/${segment(uei)}`, {}, signal),
  recipientAwards: (uei: string, query: SearchQuery, signal?: AbortSignal) =>
    get<Schemas['SearchResults']>(`/api/v1/recipients/${segment(uei)}/awards`, query, signal),
  suggestRecipients: (q: string, signal?: AbortSignal) =>
    get<Schemas['RecipientSuggestion'][]>('/api/v1/recipients/suggest', { q }, signal),
  status: (signal?: AbortSignal) => get<Schemas['Status']>('/api/v1/status', {}, signal),
};
