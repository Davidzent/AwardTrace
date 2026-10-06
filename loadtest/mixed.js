// The mixed scenario (doc 12): 70% search, 25% detail, and 5% recipient at 20 requests a second, run while a delta
// file ingests (see README.md). Passes when both the search and the detail targets hold.
import { check } from 'k6';
import http from 'k6/http';
import { BASE_URL, collectIds, failures, pick, shape } from './common.js';
import { detail } from './detail.js';
import { search } from './search.js';

export const options = {
  scenarios: { mixed: shape },
  thresholds: {
    ...failures,
    'http_req_duration{name:search}': ['p(95)<300'],
    'http_req_duration{name:detail}': ['p(95)<150'],
  },
};

export function setup() {
  return collectIds();
}

export default function (ids) {
  const roll = Math.random();
  if (roll < 0.7) {
    search();
  } else if (roll < 0.95) {
    detail(ids);
  } else {
    const response = http.get(`${BASE_URL}/api/v1/recipients/${pick(ids.recipients)}`, { tags: { name: 'recipient' } });
    check(response, { 'recipient answered': (r) => r.status === 200 });
  }
}
