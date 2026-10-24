import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Order } from '../../models/order';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { OrderService } from '../../services/order.service';

@Component({
  selector: 'app-checkout',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './checkout.html',
  styleUrl: './checkout.css',
})
export class Checkout {
  private orderService = inject(OrderService);
  protected cart = inject(CartService);
  protected auth = inject(AuthService);

  submitting = signal(false);
  error = signal('');
  placedOrder = signal<Order | null>(null);

  submit(): void {
    if (this.cart.items().length === 0 || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.error.set('');

    const request = {
      items: this.cart.items().map(i => ({ productId: i.product.id, quantity: i.quantity })),
    };

    this.orderService.placeOrder(request).subscribe({
      next: order => {
        this.placedOrder.set(order);
        this.cart.clear();
        this.submitting.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 0) {
          this.error.set('Cannot reach Order Service. Is it running on port 8082?');
        } else if (err.status === 401) {
          this.error.set('Your session has expired. Sign in again to place your order.');
        } else {
          this.error.set(err.error?.message ?? 'Something went wrong. Please try again.');
        }
        this.submitting.set(false);
      },
    });
  }
}