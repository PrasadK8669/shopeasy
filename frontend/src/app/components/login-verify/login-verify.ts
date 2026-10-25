import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { safeReturnUrl } from '../../api-config';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login-verify',
  imports: [ReactiveFormsModule],
  templateUrl: './login-verify.html',
  styleUrl: './login-verify.css',
})
export class LoginVerify implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
  readonly returnParams = this.route.snapshot.queryParamMap.has('returnUrl') ? { returnUrl: this.returnUrl } : {};

  maskedEmail = '';

  form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  submitting = signal(false);
  resending = signal(false);
  error = signal('');
  info = signal('');
  cooldown = signal(0);

  ngOnInit(): void {
    const pending = this.auth.pendingMfa();
    if (!pending) {
      // Opened directly, or the page was reloaded. The password step has to be done again.
      this.router.navigate(['/login'], { queryParams: this.returnParams });
      return;
    }
    this.maskedEmail = pending.maskedEmail;
    this.startCooldown(60); // a code was just emailed
  }

  get code() {
    return this.form.controls.code;
  }

  // Keep only digits, at most 6
  onCodeInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.code.setValue(raw.replace(/\D/g, '').slice(0, 6));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set('');
    this.info.set('');

    this.auth.verifyLogin(this.code.value).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl(this.returnUrl);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        if (err.error?.code === 'MFA_SESSION_EXPIRED') {
          this.sessionExpired();
          return;
        }
        this.error.set(
          err.status === 0
            ? 'Cannot reach the login service. Check that auth-service is running on port 8083.'
            : (err.error?.message ?? 'Verification failed. Please try again.')
        );
        this.code.reset('');
      },
    });
  }

  resend(): void {
    if (this.cooldown() > 0 || this.resending()) {
      return;
    }
    this.resending.set(true);
    this.error.set('');
    this.info.set('');

    this.auth.resendLoginCode().subscribe({
      next: res => {
        this.info.set(res.message);
        this.startCooldown(60);
        this.resending.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.resending.set(false);
        if (err.error?.code === 'MFA_SESSION_EXPIRED') {
          this.sessionExpired();
          return;
        }
        this.error.set(
          err.status === 0
            ? 'Cannot reach the login service. Check that auth-service is running on port 8083.'
            : (err.error?.message ?? 'Could not send a new code. Please try again.')
        );
      },
    });
  }

  cancel(): void {
    this.auth.clearPendingMfa();
    this.router.navigate(['/login'], { queryParams: this.returnParams });
  }

  private sessionExpired(): void {
    this.auth.clearPendingMfa();
    this.router.navigate(['/login'], { queryParams: { reason: 'expired', ...this.returnParams } });
  }

  private startCooldown(seconds: number): void {
    this.clearTimer();
    this.cooldown.set(seconds);
    this.timer = setInterval(() => {
      this.cooldown.update(s => s - 1);
      if (this.cooldown() <= 0) {
        this.clearTimer();
      }
    }, 1000);
  }

  private clearTimer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }
}