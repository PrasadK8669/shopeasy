import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { safeReturnUrl } from '../../api-config';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-verify',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './verify.html',
  styleUrl: './verify.css',
})
export class Verify implements OnDestroy {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly email = (this.route.snapshot.queryParamMap.get('email') ?? '').trim();
  readonly fromLogin = this.route.snapshot.queryParamMap.get('notice') === 'unverified';
  readonly returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));

  form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  submitting = signal(false);
  resending = signal(false);
  error = signal('');
  info = signal('');
  cooldown = signal(0);

  constructor() {
    if (!this.email) {
      this.router.navigate(['/register']);
      return;
    }
    if (!this.fromLogin) {
      this.startCooldown(60); // a code was just emailed when the account was created
    }
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

    this.auth.verifyEmail(this.email, this.code.value).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl(this.returnUrl);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 0
            ? 'Cannot reach the login service. Check that auth-service is running on port 8083.'
            : (err.error?.message ?? 'Verification failed. Please try again.')
        );
        this.code.reset('');
        this.submitting.set(false);
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

    this.auth.resendCode(this.email).subscribe({
      next: res => {
        this.info.set(res.message);
        this.startCooldown(60);
        this.resending.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 0
            ? 'Cannot reach the login service. Check that auth-service is running on port 8083.'
            : (err.error?.message ?? 'Could not send a new code. Please try again.')
        );
        this.resending.set(false);
      },
    });
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