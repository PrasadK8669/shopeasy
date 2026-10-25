import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AUTH_API, isProtectedUrl } from '../api-config';
import { AuthResponse, AuthUser, LoginRequest, MessageResponse, RegisterRequest, RegisterResponse, Session } from '../models/auth';

const STORAGE_KEY = 'shopeasy.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private expiryTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly _session = signal<Session | null>(this.readStored());

  readonly user = computed<AuthUser | null>(() => this._session()?.user ?? null);
  readonly isLoggedIn = computed(() => this._session() !== null);
  readonly isAdmin = computed(() => this._session()?.user.role === 'ADMIN');

  constructor() {
    this.scheduleExpiry();
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${AUTH_API}/login`, request).pipe(tap(res => this.start(res)));
  }

       // Registering no longer signs you in. You must verify your email first.
  register(request: RegisterRequest): Observable<RegisterResponse> {
       return this.http.post<RegisterResponse>(`${AUTH_API}/register`, request);
     }

  verifyEmail(email: string, code: string): Observable<AuthResponse> {
       return this.http
         .post<AuthResponse>(`${AUTH_API}/verify-email`, { email, code })
         .pipe(tap(res => this.start(res)));
     }

  resendCode(email: string): Observable<MessageResponse> {
       return this.http.post<MessageResponse>(`${AUTH_API}/resend-otp`, { email });
     }

  // Returns the token, or null if there is none or it has expired
  token(): string | null {
    const session = this._session();
    if (!session) {
      return null;
    }
    if (Date.now() >= session.expiresAt) {
      this.logout();
      return null;
    }
    return session.token;
  }

  // True only if there is a session that has not expired
  check(): boolean {
    return this.token() !== null;
  }

  logout(): void {
    this.clearTimer();
    this._session.set(null);
    this.store(null);
  }

  private start(res: AuthResponse): void {
    const session: Session = {
      token: res.token,
      expiresAt: Date.now() + res.expiresInSeconds * 1000,
      user: res.user,
    };
    this._session.set(session);
    this.store(session);
    this.scheduleExpiry();
  }

  // Signs the user out when the token's own clock runs out (the server checks it too)
  private scheduleExpiry(): void {
    this.clearTimer();
    const session = this._session();
    if (!session) {
      return;
    }
    const remaining = session.expiresAt - Date.now();
    this.expiryTimer = setTimeout(() => this.expire(), Math.max(remaining, 0));
  }

  private expire(): void {
    const where = this.router.url;
    this.logout();
    if (isProtectedUrl(where)) {
      this.router.navigate(['/login'], { queryParams: { reason: 'expired', returnUrl: where } });
    }
  }

  private clearTimer(): void {
    if (this.expiryTimer) {
      clearTimeout(this.expiryTimer);
      this.expiryTimer = null;
    }
  }

  private readStored(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as Session;
      const valid =
        !!session?.token && !!session.user?.id && typeof session.expiresAt === 'number' && Date.now() < session.expiresAt;
      if (!valid) {
        sessionStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      return null;
    }
  }

  private store(session: Session | null): void {
    try {
      if (session) {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } else {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Storage blocked: the login simply lasts until the page is reloaded
    }
  }
}