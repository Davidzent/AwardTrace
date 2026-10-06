// The search scenario (doc 12): queries and filters from searches.json at 20 requests a second.
// Passes when p95 is under 300 ms and fewer than 0.1% of requests fail.
import { check } from 'k6';
import http from 'k6/http';
import { BASE_URL, failures, pick, shape } from './common.js';

const searches = JSON.parse(open('./searches.json'));

export const options = {
  scenarios: { search: shape },
  thresholds: { ...failures, 'http_req_duration{name:search}': ['p(95)<300'] },
};

export function search() {
  const response = http.get(`${BASE_URL}/api/v1/awards/search?${pick(searches)}`, { tags: { name: 'search' } });
  check(response, { 'search answered': (r) => r.status === 200 });
}

export default search;
