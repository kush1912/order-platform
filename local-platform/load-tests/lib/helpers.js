import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, SAGA_TIMEOUT_SECONDS } from './config.js';

export const workflowFailures = new Rate('workflow_failures');
export const sagaCompletion = new Trend('saga_completion_duration', true);

const jsonHeaders = { 'Content-Type': 'application/json' };

export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (value) => {
    const random = Math.floor(Math.random() * 16);
    const digit = value === 'x' ? random : (random & 0x3) | 0x8;
    return digit.toString(16);
  });
}

export function jsonRequest(method, path, body, params = {}) {
  const headers = Object.assign({}, jsonHeaders, params.headers || {});
  return http.request(method, `${BASE_URL}${path}`, body === null ? null : JSON.stringify(body), {
    ...params,
    headers,
  });
}

export function parseJson(response, operation) {
  try {
    return response.json();
  } catch (error) {
    failLog(operation, response, `invalid JSON: ${error.message}`);
    return null;
  }
}

export function verify(response, operation, checks) {
  const passed = check(response, checks);
  workflowFailures.add(!passed, { operation });
  if (!passed) {
    failLog(operation, response);
  }
  return passed;
}

export function failLog(operation, response, detail = '') {
  const body = response && response.body ? String(response.body).slice(0, 800) : '';
  console.error(
    JSON.stringify({
      operation,
      detail,
      status: response ? response.status : null,
      error: response ? response.error : null,
      body,
      vu: typeof __VU === 'undefined' ? null : __VU,
      iteration: typeof __ITER === 'undefined' ? null : __ITER,
    }),
  );
}

export function thinkTime(minSeconds = 0.2, maxSeconds = 1.2) {
  sleep(minSeconds + Math.random() * (maxSeconds - minSeconds));
}

export function headerValue(response, headerName) {
  const matchingName = Object.keys(response.headers).find(
    (name) => name.toLowerCase() === headerName.toLowerCase(),
  );
  return matchingName ? response.headers[matchingName] : null;
}

export function seedInventory(productList, quantity = 100000000) {
  for (const product of productList) {
    const current = http.get(`${BASE_URL}/api/v1/inventory/${product.sku}`, {
      tags: { operation: 'inventory_seed_get' },
      responseCallback: http.expectedStatuses(200, 404),
    });
    if (current.status === 404) {
      const created = jsonRequest(
        'PUT',
        `/api/v1/inventory/${product.sku}`,
        {
          onHandQuantity: quantity,
          reason: 'K6_TEST_SEED',
          sourceReference: 'k6-load-tests',
        },
        {
          headers: { 'If-None-Match': '*' },
          tags: { operation: 'inventory_seed_create' },
          responseCallback: http.expectedStatuses(201, 412),
        },
      );
      if (created.status !== 201 && created.status !== 412) {
        failLog('inventory_seed_create', created);
        throw new Error(`Could not seed ${product.sku}.`);
      }
      continue;
    }

    if (current.status !== 200) {
      failLog('inventory_seed_get', current);
      throw new Error(`Could not read ${product.sku} during setup.`);
    }
    const etag = headerValue(current, 'ETag');
    const updated = jsonRequest(
      'PUT',
      `/api/v1/inventory/${product.sku}`,
      {
        onHandQuantity: quantity,
        reason: 'K6_TEST_RESET',
        sourceReference: 'k6-load-tests',
      },
      {
        headers: { 'If-Match': etag },
        tags: { operation: 'inventory_seed_update' },
        responseCallback: http.expectedStatuses(200, 412),
      },
    );
    if (updated.status !== 200 && updated.status !== 412) {
      failLog('inventory_seed_update', updated);
      throw new Error(`Could not reset ${product.sku}.`);
    }
  }
}

export function placeOrder(customer, items, operation = 'place_order') {
  const response = jsonRequest(
    'POST',
    '/api/v1/orders',
    {
      customerId: customer.id,
      currency: customer.currency,
      items,
    },
    {
      headers: { 'Idempotency-Key': `${operation}-${uuid()}` },
      tags: { operation },
    },
  );
  const body = parseJson(response, operation);
  verify(response, operation, {
    [`${operation}: status is 201`]: (result) => result.status === 201,
    [`${operation}: response has order id`]: () => Boolean(body && body.id),
    [`${operation}: starts pending`]: () => body && body.status === 'PENDING',
    [`${operation}: item count matches`]: () =>
      body && Array.isArray(body.items) && body.items.length === items.length,
    [`${operation}: total is positive`]: () => body && Number(body.totalAmount) > 0,
  });
  return { response, body };
}

export function waitForOrder(orderId, terminalStatuses, operation) {
  const started = Date.now();
  const deadline = started + SAGA_TIMEOUT_SECONDS * 1000;
  let response;
  let body;

  do {
    sleep(0.25);
    response = http.get(`${BASE_URL}/api/v1/orders/${orderId}`, {
      tags: { operation },
    });
    body = parseJson(response, operation);
    if (response.status !== 200 || !body) {
      break;
    }
    if (terminalStatuses.includes(body.status)) {
      sagaCompletion.add(Date.now() - started, { operation });
      return { response, body };
    }
  } while (Date.now() < deadline);

  workflowFailures.add(true, { operation });
  failLog(operation, response, `order did not reach ${terminalStatuses.join(',')}`);
  return { response, body };
}
