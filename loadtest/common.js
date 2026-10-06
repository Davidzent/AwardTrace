// What the three scenarios share (doc 12): where they send requests, the load's shape, and real IDs to ask for.
import http from 'k6/http';

/** The app's origin. Production by default; set BASE_URL to aim elsewhere, such as http://localhost:8080. */
export const BASE_URL = (__ENV.BASE_URL || 'https://app.awardtrace.zntsns.com').replace(/\/$/, '');

/** Ramp to 20 requests a second over a minute, then hold it for five (doc 12). */
export const shape = {
  executor: 'ramping-arrival-rate',
  startRate: 1,
  timeUnit: '1s',
  preAllocatedVUs: 20,
  maxVUs: 60,
  stages: [
    { target: 20, duration: '1m' },
    { target: 20, duration: '5m' },
  ],
};

/** Any failed request counts, a 429 from the rate limit included; that's why the README exempts the load generator. */
export const failures = { http_req_failed: ['rate<0.001'] };

/**
 * Award IDs and recipient UEIs to request, from the newest awards. Setup's requests carry their own name, so they
 * don't count toward a scenario's latency threshold.
 */
export function collectIds() {
  const awards = [];
  const recipients = new Set();
  // 500 awards, at the API's largest page size of 50.
  for (let page = 1; page <= 10; page += 1) {
    const response = http.get(`${BASE_URL}/api/v1/awards/search?sort=newest&size=50&page=${page}`, {
      tags: { name: 'setup' },
    });
    if (response.status !== 200) {
      throw new Error(`Setup's search returned ${response.status}; is the app up, and is this address exempt?`);
    }
    for (const result of response.json('results')) {
      awards.push(result.award_id);
      if (result.recipient && result.recipient.uei) {
        recipients.add(result.recipient.uei);
      }
    }
  }
  return { awards, recipients: [...recipients] };
}

export function pick(items) {
  return items[Math.floor(Math.random() * items.length)];
}
