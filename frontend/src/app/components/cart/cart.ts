import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Product } from '../../models/product';
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

  showImage(p: Product): boolean {
    return !!p.imageUrl && !this.brokenImages().includes(p.id);
  }

  markBroken(id: number): void {
    this.brokenImages.update(list => (list.includes(id) ? list : [...list, id]));
  }
}