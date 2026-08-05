import { options } from './lib/config.js';
import { customers, products, randomItem, weightedProduct } from './lib/data.js';
import {
  jsonRequest,
  parseJson,
  placeOrder,
  seedInventory,
  thinkTime,
  uuid,
  verify,
  waitForOrder,
} from './lib/helpers.js';

export { options };

export function setup() {
  seedInventory(products);
}

export default function () {
  const customer = randomItem(customers);
  const product = weightedProduct();
  const placed = placeOrder(
    customer,
    [{ sku: product.sku, quantity: 1, unitPrice: product.unitPrice }],
    'cancel_order_prerequisite',
  );
  if (!placed.body || placed.response.status !== 201) {
    thinkTime();
    return;
  }

  const confirmed = waitForOrder(
    placed.body.id,
    ['CONFIRMED', 'REJECTED'],
    'cancel_order_wait_for_confirmation',
  );
  if (!confirmed.body || confirmed.body.status !== 'CONFIRMED') {
    verify(confirmed.response, 'cancel_order_wait_for_confirmation', {
      'cancel order: prerequisite order confirms': () => false,
    });
    thinkTime();
    return;
  }

  thinkTime(0.5, 2);
  const cancellation = jsonRequest(
    'POST',
    `/api/v1/orders/${placed.body.id}/cancellations`,
    null,
    {
      headers: { 'Idempotency-Key': `cancel-${uuid()}` },
      tags: { operation: 'cancel_order' },
    },
  );
  const cancellationBody = parseJson(cancellation, 'cancel_order');
  verify(cancellation, 'cancel_order', {
    'cancel order: status is 202': (response) => response.status === 202,
    'cancel order: response is cancellation pending': () =>
      cancellationBody && cancellationBody.status === 'CANCELLATION_PENDING',
  });
  if (cancellation.status !== 202) {
    thinkTime();
    return;
  }

  const cancelled = waitForOrder(
    placed.body.id,
    ['CANCELLED'],
    'cancel_order_saga',
  );
  verify(cancelled.response, 'cancel_order_saga', {
    'cancel order: inventory release completes': () =>
      cancelled.body && cancelled.body.status === 'CANCELLED',
  });
  thinkTime();
}
