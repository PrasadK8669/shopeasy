import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Product } from '../../models/product';
import { CartService } from '../../services/cart.service';
import { ProductService } from '../../services/product.service';

@Component({
  selector: 'app-product-list',
  imports: [CurrencyPipe],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList implements OnInit {
  private productService = inject(ProductService);
  protected cart = inject(CartService);

  products = signal<Product[]>([]);
  page = signal(0);
  totalPages = signal(0);
  search = signal('');
  loading = signal(false);
  error = signal('');
  readonly pageSize = 8;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.productService.getProducts(this.search(), this.page(), this.pageSize).subscribe({
      next: res => {
        this.products.set(res.content);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load products. Is Product Service running on port 8081?');
        this.loading.set(false);
      },
    });
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
    this.load();
  }

  clear(input: HTMLInputElement): void {
    input.value = '';
    this.onSearch('');
  }

  prev(): void {
    if (this.page() > 0) {
      this.page.update(p => p - 1);
      this.load();
    }
  }

  next(): void {
    if (this.page() < this.totalPages() - 1) {
      this.page.update(p => p + 1);
      this.load();
    }
  }

  addToCart(product: Product): void {
    this.cart.add(product);
  }
}