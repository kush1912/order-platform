import { BASE_URL, options } from './lib/config.js';
import { customers, products, randomItem, weightedProduct } from './lib/data.js';
import {
  placeOrder,
  seedInventory,
  thinkTime,
  verify,
  waitForOrder,
} from './lib/helpers.js';
import http from 'k6/http';

export { options };

export function setup() {
  seedInventory(products);
  const desiredPoolSize = Number(__ENV.ORDER_POOL_SIZE || 50);
  const orderIds = [];

  for (let index = 0; index < desiredPoolSize; index += 1) {
    const product = weightedProduct();
    const placed = placeOrder(
      randomItem(customers),
      [{ sku: product.sku, quantity: 1, unitPrice: product.unitPrice }],
      'get_order_seed',
    );
    if (!placed.body || placed.response.status !== 201) {
      continue;
    }
    const confirmed = waitForOrder(
      placed.body.id,
      ['CONFIRMED', 'REJECTED'],
      'get_order_seed_saga',
    );
    if (confirmed.body && confirmed.body.status === 'CONFIRMED') {
      orderIds.push(placed.body.id);
    }
  }

  if (orderIds.length === 0) {
    throw new Error('Get Order setup could not create a readable order pool.');
  }
  return { orderIds };
}

export default function (data) {
  const orderId = randomItem(data.orderIds);
  const response = http.get(`${BASE_URL}/api/v1/orders/${orderId}`, {
    tags: { operation: 'get_order' },
  });
  let body = null;
  try {
    body = response.json();
  } catch (error) {
    console.error(`get_order invalid JSON: ${error.message}`);
  }

  verify(response, 'get_order', {
    'get order: status is 200': (result) => result.status === 200,
    'get order: requested id is returned': () => body && body.id === orderId,
    'get order: status is a known state': () =>
      body &&
      ['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLATION_PENDING', 'CANCELLED'].includes(
        body.status,
      ),
    'get order: items are present': () =>
      body && Array.isArray(body.items) && body.items.length > 0,
  });
  thinkTime(0.1, 0.8);
}
