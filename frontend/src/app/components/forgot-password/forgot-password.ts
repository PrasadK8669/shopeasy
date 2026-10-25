import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.css',
})
export class ForgotPassword {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submitting = signal(false);
  error = signal('');

  get email() {
    return this.form.controls.email;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set('');
    const email = this.email.value.trim();

    this.auth.forgotPassword(email).subscribe({
      next: () => {
        this.submitting.set(false);
        // Always continue to the next page, whether or not the account exists
        this.router.navigate(['/reset-password'], { queryParams: { email } });
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 0
            ? 'Cannot reach the login service. Check that auth-service is running on port 8083.'
            : (err.error?.message ?? 'Something went wrong. Please try again.')
        );
        this.submitting.set(false);
      },
    });
  }
}