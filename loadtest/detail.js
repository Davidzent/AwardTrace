// The detail scenario (doc 12): random awards' detail pages at 20 requests a second.
// Passes when p95 is under 150 ms and fewer than 0.1% of requests fail.
import { check } from 'k6';
import http from 'k6/http';
import { BASE_URL, collectIds, failures, pick, shape } from './common.js';

export const options = {
  scenarios: { detail: shape },
  thresholds: { ...failures, 'http_req_duration{name:detail}': ['p(95)<150'] },
};

export function setup() {
  return collectIds();
}

export function detail(ids) {
  const response = http.get(`${BASE_URL}/api/v1/awards/${encodeURIComponent(pick(ids.awards))}`, {
    tags: { name: 'detail' },
  });
  check(response, { 'detail answered': (r) => r.status === 200 });
}

export default detail;
