import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ContactRequest, ContactResponse } from '../models/contact';

@Injectable({ providedIn: 'root' })
export class ContactService {
  private http = inject(HttpClient);
  private baseUrl = 'http://localhost:8081/api/contact';

  send(request: ContactRequest): Observable<ContactResponse> {
    return this.http.post<ContactResponse>(this.baseUrl, request);
  }
}