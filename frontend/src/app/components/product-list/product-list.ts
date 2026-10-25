import { CurrencyPipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CategoryCount, Product } from '../../models/product';
import { CartService } from '../../services/cart.service';
import { ProductService } from '../../services/product.service';

interface PriceRange {
  key: string;
  label: string;
  min: number | null;
  max: number | null;
}

@Component({
  selector: 'app-product-list',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
  host: { '(document:keydown.escape)': 'closeFilters()' },
})
export class ProductList implements OnInit, OnDestroy {
  private productService = inject(ProductService);
  private cart = inject(CartService);

  readonly pageSize = 12;

  readonly priceRanges: PriceRange[] = [
    { key: 'any', label: 'Any price', min: null, max: null },
    { key: 'u500', label: 'Under ₹500', min: null, max: 499.99 },
    { key: 'mid', label: '₹500 to ₹1,999', min: 500, max: 1999.99 },
    { key: 'high', label: '₹2,000 to ₹9,999', min: 2000, max: 9999.99 },
    { key: 'o10k', label: '₹10,000 and above', min: 10000, max: null },
  ];

  // Data
  products = signal<Product[]>([]);
  categories = signal<CategoryCount[]>([]);
  total = signal(0);
  totalPages = signal(0);
  page = signal(0);

  // Filters
  search = signal('');
  category = signal('');
  priceKey = signal('any');
  inStock = signal(false);
  sort = signal('featured');

  // UI state
  loading = signal(false);
  error = signal('');
  filtersOpen = signal(false);
  addedId = signal<number | null>(null);
  brokenImages = signal<number[]>([]);

  // Used to move keyboard focus into the drawer and back out again
  menuButton = viewChild<ElementRef<HTMLButtonElement>>('menuBtn');
  closeButton = viewChild<ElementRef<HTMLButtonElement>>('closeBtn');

  totalAll = computed(() => this.categories().reduce((sum, c) => sum + c.count, 0));

  activeFilters = computed(
    () =>
      (this.search() ? 1 : 0) +
      (this.category() ? 1 : 0) +
      (this.priceKey() !== 'any' ? 1 : 0) +
      (this.inStock() ? 1 : 0)
  );

  ngOnInit(): void {
    this.productService.getCategories().subscribe({
      next: list => this.categories.set(list),
      error: () => this.categories.set([]),
    });
    this.load();
  }

  ngOnDestroy(): void {
    document.body.style.overflow = ''; // never leave the page locked
  }

  load(): void {
    const range = this.priceRanges.find(r => r.key === this.priceKey()) ?? this.priceRanges[0];

    this.loading.set(true);
    this.error.set('');
    this.productService
      .getProducts({
        search: this.search(),
        category: this.category(),
        minPrice: range.min,
        maxPrice: range.max,
        inStock: this.inStock(),
        sort: this.sort(),
        page: this.page(),
        size: this.pageSize,
      })
      .subscribe({
        next: res => {
          this.products.set(res.content);
          this.total.set(res.totalElements);
          this.totalPages.set(res.totalPages);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(
            'We could not load the products. Check that Product Service is running on port 8081, then try again.'
          );
          this.loading.set(false);
        },
      });
  }

  // Any filter change goes back to page 1
  private reload(): void {
    this.page.set(0);
    this.load();
  }

  onSearch(value: string): void {
    this.search.set(value.trim());
    this.reload();
  }

  clearSearch(input: HTMLInputElement): void {
    input.value = '';
    this.search.set('');
    this.reload();
  }

  selectCategory(name: string): void {
    this.category.set(name);
    this.reload();
  }

  setPrice(key: string): void {
    this.priceKey.set(key);
    this.reload();
  }

  toggleStock(): void {
    this.inStock.update(v => !v);
    this.reload();
  }

  setSort(value: string): void {
    this.sort.set(value);
    this.reload();
  }

  resetAll(input: HTMLInputElement): void {
    input.value = '';
    this.search.set('');
    this.category.set('');
    this.priceKey.set('any');
    this.inStock.set(false);
    this.reload();
  }

  openFilters(): void {
    this.filtersOpen.set(true);
    document.body.style.overflow = 'hidden'; // stop the page behind from scrolling
    setTimeout(() => this.closeButton()?.nativeElement.focus());
  }

  closeFilters(): void {
    if (!this.filtersOpen()) {
      return;
    }
    this.filtersOpen.set(false);
    document.body.style.overflow = '';
    setTimeout(() => this.menuButton()?.nativeElement.focus());
  }

  // Keeps Tab and Shift+Tab inside the open drawer
  trapFocus(event: KeyboardEvent): void {
    if (event.key !== 'Tab') {
      return;
    }
    const drawer = event.currentTarget as HTMLElement;
    const focusable = drawer.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input, select');
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  prev(): void {
    if (this.page() > 0) {
      this.page.update(p => p - 1);
      this.load();
      window.scrollTo({ top: 0 });
    }
  }

  next(): void {
    if (this.page() < this.totalPages() - 1) {
      this.page.update(p => p + 1);
      this.load();
      window.scrollTo({ top: 0 });
    }
  }

  showImage(p: Product): boolean {
    return !!p.imageUrl && !this.brokenImages().includes(p.id);
  }

  markBroken(id: number): void {
    this.brokenImages.update(list => (list.includes(id) ? list : [...list, id]));
  }

  inCart(productId: number): number {
    return this.cart.items().find(i => i.product.id === productId)?.quantity ?? 0;
  }

  addToCart(product: Product): void {
    this.cart.add(product);
    this.addedId.set(product.id);
    setTimeout(() => {
      if (this.addedId() === product.id) {
        this.addedId.set(null);
      }
    }, 1200);
  }
}