import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

const passwordsMatch: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirm = group.get('confirm')?.value;
  return password && confirm && password !== confirm ? { mismatch: true } : null;
};

@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.css',
})
export class ResetPassword implements OnDestroy {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly email = (this.route.snapshot.queryParamMap.get('email') ?? '').trim();

  form = this.fb.nonNullable.group(
    {
      code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(72),
          Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).*$/),
        ],
      ],
      confirm: ['', Validators.required],
    },
    { validators: passwordsMatch }
  );

  submitting = signal(false);
  resending = signal(false);
  showPassword = signal(false);
  error = signal('');
  info = signal('');
  cooldown = signal(0);

  constructor() {
    if (!this.email) {
      this.router.navigate(['/forgot-password']);
      return;
    }
    this.startCooldown(60); // a code was just requested on the previous page
  }

  get code() {
    return this.form.controls.code;
  }

  get password() {
    return this.form.controls.password;
  }

  get confirm() {
    return this.form.controls.confirm;
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

    this.auth.resetPassword(this.email, this.code.value, this.password.value).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/login'], { queryParams: { reset: 'done' } });
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 0
            ? 'Cannot reach the login service. Check that auth-service is running on port 8083.'
            : (err.error?.message ?? 'Could not reset the password. Please try again.')
        );
        if (err.error?.code === 'INVALID_OTP') {
          this.code.reset(''); // the code is wrong or dead, so ask for it again
        }
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

    this.auth.forgotPassword(this.email).subscribe({
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