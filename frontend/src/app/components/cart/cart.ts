import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartService } from '../../services/cart.service';

@Component({
  selector: 'app-cart',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './cart.html',
  styleUrl: './cart.css',
})
export class Cart {
  protected cart = inject(CartService);

  brokenImages = signal<number[]>([]);

  markBroken(id: number): void {
    if (!this.brokenImages().includes(id)) {
      this.brokenImages.update(ids => [...ids, id]);
    }
  }

  increase(productId: number, current: number): void {
    this.cart.setQuantity(productId, current + 1);
  }

  decrease(productId: number, current: number): void {
    this.cart.setQuantity(productId, current - 1); // 0 removes the item
  }

  remove(productId: number): void {
    this.cart.remove(productId);
  }

  clear(): void {
    this.cart.clear();
  }
}