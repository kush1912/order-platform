import { SharedArray } from 'k6/data';

export const customers = new SharedArray('customers', () =>
  JSON.parse(open('../data/customers.json')),
);

export const products = new SharedArray('products', () =>
  JSON.parse(open('../data/products.json')),
);

export function randomItem(values) {
  return values[Math.floor(Math.random() * values.length)];
}

export function weightedProduct() {
  const totalWeight = products.reduce((sum, product) => sum + product.weight, 0);
  let selection = Math.random() * totalWeight;
  for (const product of products) {
    selection -= product.weight;
    if (selection <= 0) {
      return product;
    }
  }
  return products[products.length - 1];
}

export function randomOrderItems() {
  const desiredCount = 1 + Math.floor(Math.random() * 3);
  const selected = new Map();
  while (selected.size < desiredCount) {
    const product = weightedProduct();
    selected.set(product.sku, {
      sku: product.sku,
      quantity: 1 + Math.floor(Math.random() * 3),
      unitPrice: product.unitPrice,
    });
  }
  return Array.from(selected.values());
}
