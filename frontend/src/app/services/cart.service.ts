import { Injectable, computed, signal } from '@angular/core';
import { CartItem, Product } from '../models/product';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly _items = signal<CartItem[]>([]);

  readonly items = this._items.asReadonly();
  readonly totalItems = computed(() =>
    this._items().reduce((sum, i) => sum + i.quantity, 0));
  readonly totalPrice = computed(() =>
    this._items().reduce((sum, i) => sum + i.product.price * i.quantity, 0));

  add(product: Product): void {
    this._items.update(items => {
      const existing = items.find(i => i.product.id === product.id);
      if (existing) {
        if (existing.quantity >= product.stock) return items; // can't exceed stock
        return items.map(i =>
          i.product.id === product.id ? { ...i, quantity: i.quantity + 1 } : i);
      }
      if (product.stock < 1) return items;
      return [...items, { product, quantity: 1 }];
    });
  }

  setQuantity(productId: number, quantity: number): void {
    this._items.update(items =>
      items
        .map(i => i.product.id === productId
          ? { ...i, quantity: Math.min(quantity, i.product.stock) }
          : i)
        .filter(i => i.quantity > 0));
  }

  remove(productId: number): void {
    this._items.update(items => items.filter(i => i.product.id !== productId));
  }

  clear(): void {
    this._items.set([]);
  }
}
