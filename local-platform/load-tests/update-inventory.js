import http from 'k6/http';
import { options, BASE_URL } from './lib/config.js';
import {
  headerValue,
  jsonRequest,
  parseJson,
  thinkTime,
  verify,
} from './lib/helpers.js';

export { options };

function inventorySku() {
  return `K6-UPDATE-${String(__VU).padStart(6, '0')}`;
}

function readOrCreateInventory(sku) {
  let response = http.get(`${BASE_URL}/api/v1/inventory/${sku}`, {
    tags: { operation: 'update_inventory_get' },
    responseCallback: http.expectedStatuses(200, 404),
  });
  if (response.status !== 404) {
    return response;
  }

  const created = jsonRequest(
    'PUT',
    `/api/v1/inventory/${sku}`,
    {
      onHandQuantity: 1000,
      reason: 'K6_VU_INITIALIZATION',
      sourceReference: `k6-vu-${__VU}`,
    },
    {
      headers: { 'If-None-Match': '*' },
      tags: { operation: 'update_inventory_create' },
      responseCallback: http.expectedStatuses(201, 412),
    },
  );
  if (created.status === 201) {
    return created;
  }
  return http.get(`${BASE_URL}/api/v1/inventory/${sku}`, {
    tags: { operation: 'update_inventory_get_after_race' },
  });
}

export default function () {
  const sku = inventorySku();
  const current = readOrCreateInventory(sku);
  const currentBody = parseJson(current, 'update_inventory_get');
  const etag = headerValue(current, 'ETag');
  verify(current, 'update_inventory_get', {
    'update inventory: current item is readable': (response) =>
      response.status === 200 || response.status === 201,
    'update inventory: current body has version': () =>
      currentBody && Number.isInteger(currentBody.version),
    'update inventory: ETag is present': () => Boolean(etag),
  });
  if (!currentBody || !etag) {
    thinkTime();
    return;
  }

  const delta = 1 + Math.floor(Math.random() * 25);
  const nextQuantity = currentBody.onHandQuantity + delta;
  const updated = jsonRequest(
    'PUT',
    `/api/v1/inventory/${sku}`,
    {
      onHandQuantity: nextQuantity,
      reason: 'K6_WAREHOUSE_SYNC',
      sourceReference: `k6-vu-${__VU}-iteration-${__ITER}`,
    },
    {
      headers: { 'If-Match': etag },
      tags: { operation: 'update_inventory' },
    },
  );
  const updatedBody = parseJson(updated, 'update_inventory');
  verify(updated, 'update_inventory', {
    'update inventory: status is 200': (response) => response.status === 200,
    'update inventory: quantity is updated': () =>
      updatedBody && updatedBody.onHandQuantity === nextQuantity,
    'update inventory: version increments': () =>
      updatedBody && updatedBody.version === currentBody.version + 1,
    'update inventory: availability is valid': () =>
      updatedBody &&
      updatedBody.availableQuantity ===
        updatedBody.onHandQuantity - updatedBody.reservedQuantity,
  });
  thinkTime(0.2, 1);
}
