import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Product } from '../../models/product';
import { CartService } from '../../services/cart.service';
import { ProductService } from '../../services/product.service';

@Component({
  selector: 'app-product-detail',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.css',
})
export class ProductDetail implements OnInit {
  private route = inject(ActivatedRoute);
  private productService = inject(ProductService);
  private cart = inject(CartService);

  product = signal<Product | null>(null);
  loading = signal(false);
  error = signal('');
  notFound = signal(false);
  quantity = signal(1);
  added = signal(false);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
  }

  load(id: number): void {
    this.loading.set(true);
    this.error.set('');
    this.notFound.set(false);
    this.productService.getProduct(id).subscribe({
      next: product => {
        this.product.set(product);
        this.quantity.set(1);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 404) {
          this.notFound.set(true);
        } else {
          this.error.set(
            'We could not load this product. Check that Product Service is running on port 8081, then try again.'
          );
        }
        this.loading.set(false);
      },
    });
  }

  retry(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
  }

  increase(): void {
    const p = this.product();
    if (p && this.quantity() < p.stock) {
      this.quantity.update(q => q + 1);
    }
  }

  decrease(): void {
    if (this.quantity() > 1) {
      this.quantity.update(q => q - 1);
    }
  }

  addToCart(): void {
    const p = this.product();
    if (!p || p.stock === 0) {
      return;
    }
    for (let i = 0; i < this.quantity(); i++) {
      this.cart.add(p);
    }
    this.added.set(true);
    setTimeout(() => this.added.set(false), 1200);
  }
}
