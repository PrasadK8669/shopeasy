import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_ORIGINS, isProtectedUrl } from '../api-config';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Only attach the token to our own APIs, never to another site
  const isOurApi = API_ORIGINS.some(origin => req.url.startsWith(origin));
  const token = isOurApi ? auth.token() : null;
  const outgoing = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

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