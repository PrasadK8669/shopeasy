import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Order } from '../../models/order';
import { CartService } from '../../services/cart.service';
import { OrderService } from '../../services/order.service';

@Component({
  selector: 'app-checkout',
  imports: [ReactiveFormsModule, CurrencyPipe, RouterLink],
  templateUrl: './checkout.html',
  styleUrl: './checkout.css',
})
export class Checkout {
  private fb = inject(FormBuilder);
  private orderService = inject(OrderService);
  protected cart = inject(CartService);

  form = this.fb.nonNullable.group({
    customerName: ['', [Validators.required, Validators.minLength(2)]],
    customerEmail: ['', [Validators.required, Validators.email]],
  });

  submitting = signal(false);
  error = signal('');
  placedOrder = signal<Order | null>(null);

  get name() {
    return this.form.controls.customerName;
  }

  get email() {
    return this.form.controls.customerEmail;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.cart.items().length === 0) {
      return;
    }

    this.submitting.set(true);
    this.error.set('');

    const request = {
      ...this.form.getRawValue(),
      items: this.cart.items().map(i => ({
        productId: i.product.id,
        quantity: i.quantity,
      })),
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
        } else {
          this.error.set(err.error?.message ?? 'Something went wrong. Please try again.');
        }
        this.submitting.set(false);
      },
    });
  }
}