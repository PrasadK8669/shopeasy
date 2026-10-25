import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_ORIGINS, AUTH_API, getDeviceId, isProtectedUrl } from '../api-config';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Only attach the token to our own APIs, never to another site
  const isOurApi = API_ORIGINS.some(origin => req.url.startsWith(origin));
  const token = isOurApi ? auth.token() : null;

  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (req.url.startsWith(AUTH_API)) {
    headers['X-Device-Id'] = getDeviceId(); // lets the server recognise this browser
  }
  const outgoing = Object.keys(headers).length > 0 ? req.clone({ setHeaders: headers }) : req;

  return next(outgoing).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && token) {
        // The server rejected our token (expired or tampered with)
        const where = router.url;
        auth.logout();
        if (isProtectedUrl(where)) {
          router.navigate(['/login'], { queryParams: { reason: 'expired', returnUrl: where } });
        }
        // Public pages still work without a token, so try the request once more without it
        return next(req);
      }
      return throwError(() => err);
    })
  );
};