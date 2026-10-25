import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { safeReturnUrl } from '../../api-config';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submitting = signal(false);
  error = signal('');
  showPassword = signal(false);

  readonly returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
  readonly hasReturnUrl = this.route.snapshot.queryParamMap.has('returnUrl');
  readonly expired = this.route.snapshot.queryParamMap.get('reason') === 'expired';
  readonly returnParams = this.hasReturnUrl ? { returnUrl: this.returnUrl } : {};

  get email() {
    return this.form.controls.email;
  }

  get password() {
    return this.form.controls.password;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set('');

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl(this.returnUrl);
      },
      error: (err: HttpErrorResponse) => {
                if (err.status === 403 && err.error?.code === 'EMAIL_NOT_VERIFIED') {
          this.submitting.set(false);
          this.router.navigate(['/verify'], {
            queryParams: { email: this.email.value.trim(), notice: 'unverified', ...this.returnParams },
          });
          return;
        }
        this.error.set(
          err.status === 0
            ? 'Cannot reach the login service. Check that auth-service is running on port 8083.'
            : (err.error?.message ?? 'Sign in failed. Please try again.')
        );
        this.password.reset('');
        this.submitting.set(false);
      },
    });
  }
}