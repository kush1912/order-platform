const testType = (__ENV.TEST_TYPE || 'smoke').toLowerCase();
const stressMaxVUs = Number(__ENV.STRESS_MAX_VUS || 750);
const soakVUs = Number(__ENV.SOAK_VUS || 50);

const profiles = {
  smoke: {
    executor: 'constant-vus',
    vus: 1,
    duration: '30s',
  },
  load: {
    executor: 'ramping-vus',
    startVUs: 10,
    stages: [
      { duration: '1m', target: 10 },
      { duration: '1m', target: 50 },
      { duration: '2m', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '3m', target: 100 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '30s',
  },
  stress: {
    executor: 'ramping-vus',
    startVUs: 10,
    stages: [
      { duration: '1m', target: Math.max(25, Math.round(stressMaxVUs * 0.1)) },
      { duration: '1m', target: Math.round(stressMaxVUs * 0.25) },
      { duration: '1m', target: Math.round(stressMaxVUs * 0.5) },
      { duration: '1m', target: Math.round(stressMaxVUs * 0.75) },
      { duration: '2m', target: stressMaxVUs },
      { duration: '1m', target: 0 },
    ],
    gracefulRampDown: '30s',
  },
  spike: {
    executor: 'ramping-vus',
    startVUs: 10,
    stages: [
      { duration: '1m', target: 10 },
      { duration: '10s', target: 500 },
      { duration: '2m', target: 500 },
      { duration: '30s', target: 10 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '15s',
  },
  soak: {
    executor: 'constant-vus',
    vus: soakVUs,
    duration: '30m',
  },
};

if (!profiles[testType]) {
  throw new Error(`Unsupported TEST_TYPE '${testType}'. Use smoke, load, stress, spike, or soak.`);
}

const overloadTest = testType === 'stress' || testType === 'spike';

export const options = {
  scenarios: {
    [testType]: profiles[testType],
  },
  thresholds: {
    checks: [`rate>${overloadTest ? 0.95 : 0.99}`],
    http_req_failed: [`rate<${overloadTest ? 0.10 : 0.01}`],
    http_req_duration: [
      `p(95)<${overloadTest ? 3000 : 1000}`,
      `p(99)<${overloadTest ? 5000 : 2000}`,
    ],
    workflow_failures: [`rate<${overloadTest ? 0.10 : 0.01}`],
  },
  noConnectionReuse: false,
  noVUConnectionReuse: false,
  userAgent: `order-platform-k6/${testType}`,
  setupTimeout: '5m',
  teardownTimeout: '1m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
export const SAGA_TIMEOUT_SECONDS = Number(__ENV.SAGA_TIMEOUT_SECONDS || 20);
