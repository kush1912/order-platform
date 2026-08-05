import { options } from './lib/config.js';
import { customers, products, randomItem, randomOrderItems } from './lib/data.js';
import { placeOrder, seedInventory, thinkTime, verify, waitForOrder } from './lib/helpers.js';

export { options };

export function setup() {
  seedInventory(products);
}

export default function () {
  const customer = randomItem(customers);
  const items = randomOrderItems();
  const placed = placeOrder(customer, items);
  if (!placed.body || placed.response.status !== 201) {
    thinkTime();
    return;
  }

  const completed = waitForOrder(
    placed.body.id,
    ['CONFIRMED', 'REJECTED'],
    'place_order_saga',
  );
  verify(completed.response, 'place_order_saga', {
    'place order: inventory reservation confirms order': () =>
      completed.body && completed.body.status === 'CONFIRMED',
    'place order: customer remains unchanged': () =>
      completed.body && completed.body.customerId === customer.id,
  });
  thinkTime();
}
