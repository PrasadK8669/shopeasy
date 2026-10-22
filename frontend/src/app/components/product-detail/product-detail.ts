import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
  related = signal<Product[]>([]);
  loading = signal(true);
  error = signal<'' | 'notfound' | 'failed'>('');
  quantity = signal(1);
  added = signal(false);
  brokenImages = signal<number[]>([]);

  inCartCount = computed(() => {
    const p = this.product();
    return p ? (this.cart.items().find(i => i.product.id === p.id)?.quantity ?? 0) : 0;
  });

  // How many more of this product can still be added
  available = computed(() => {
    const p = this.product();
    return p ? Math.max(p.stock - this.inCartCount(), 0) : 0;
  });

  ngOnInit(): void {
    // paramMap emits again if the id in the URL changes (for example from a related product)
    this.route.paramMap.subscribe(params => this.load(Number(params.get('id'))));
  }

  load(id: number): void {
    this.loading.set(true);
    this.error.set('');
    this.quantity.set(1);
    this.added.set(false);

    if (!Number.isFinite(id) || id <= 0) {
      this.product.set(null);
      this.error.set('notfound');
      this.loading.set(false);
      return;
    }

    this.productService.getProduct(id).subscribe({
      next: p => {
        this.product.set(p);
        this.loading.set(false);
        this.loadRelated(p);
        window.scrollTo({ top: 0 });
      },
      error: (err: HttpErrorResponse) => {
        this.product.set(null);
        this.error.set(err.status === 404 ? 'notfound' : 'failed');
        this.loading.set(false);
      },
    });
  }

  retry(): void {
    this.load(Number(this.route.snapshot.paramMap.get('id')));
  }

  private loadRelated(p: Product): void {
    if (!p.category) {
      this.related.set([]);
      return;
    }
    this.productService.getProducts({ category: p.category, size: 5 }).subscribe({
      next: res => this.related.set(res.content.filter(r => r.id !== p.id).slice(0, 4)),
      error: () => this.related.set([]),
    });
  }

  increase(): void {
    if (this.quantity() < this.available()) {
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
    if (!p || this.available() < 1) {
      return;
    }
    const qty = Math.min(this.quantity(), this.available());
    for (let i = 0; i < qty; i++) {
      this.cart.add(p);
    }
    this.quantity.set(1);
    this.added.set(true);
    setTimeout(() => this.added.set(false), 1200);
  }

  showImage(p: Product): boolean {
    return !!p.imageUrl && !this.brokenImages().includes(p.id);
  }

  markBroken(id: number): void {
    this.brokenImages.update(list => (list.includes(id) ? list : [...list, id]));
  }
}