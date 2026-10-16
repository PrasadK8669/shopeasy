import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, Product } from '../models/product';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  private baseUrl = 'http://localhost:8081/api/products';

  getProducts(search = '', page = 0, size = 8): Observable<Page<Product>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http.get<Page<Product>>(this.baseUrl, { params });
  }

  getProduct(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.baseUrl}/${id}`);
  }
}