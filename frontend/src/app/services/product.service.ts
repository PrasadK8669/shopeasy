import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CategoryCount, Page, Product, ProductQuery } from '../models/product';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  private baseUrl = 'http://localhost:8081/api/products';

  getProducts(query: ProductQuery = {}): Observable<Page<Product>> {
    let params = new HttpParams()
      .set('page', query.page ?? 0)
      .set('size', query.size ?? 12);

    if (query.search?.trim()) {
      params = params.set('search', query.search.trim());
    }
    if (query.category) {
      params = params.set('category', query.category);
    }
    if (query.minPrice != null) {
      params = params.set('minPrice', query.minPrice);
    }
    if (query.maxPrice != null) {
      params = params.set('maxPrice', query.maxPrice);
    }
    if (query.inStock) {
      params = params.set('inStock', true);
    }
    if (query.sort && query.sort !== 'featured') {
      params = params.set('sort', query.sort);
    }
    return this.http.get<Page<Product>>(this.baseUrl, { params });
  }

  getCategories(): Observable<CategoryCount[]> {
    return this.http.get<CategoryCount[]>(`${this.baseUrl}/categories`);
  }

  getProduct(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.baseUrl}/${id}`);
  }
}