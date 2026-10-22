import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ContactService } from '../../services/contact.service';

@Component({
  selector: 'app-contact',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './contact.html',
  styleUrl: './contact.css',
})
export class Contact {
  private fb = inject(FormBuilder);
  private contactService = inject(ContactService);

  readonly maxLength = 2000;

  readonly topics = [
    { value: 'general', label: 'General question' },
    { value: 'order', label: 'Help with an order' },
    { value: 'feedback', label: 'Feedback about the shop' },
    { value: 'other', label: 'Something else' },
  ];

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    topic: ['general', Validators.required],
    message: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
  });

  count = signal(0);
  sending = signal(false);
  sent = signal(false);
  sentName = signal('');
  error = signal('');

  get name() {
    return this.form.controls.name;
  }

  get email() {
    return this.form.controls.email;
  }

  get message() {
    return this.form.controls.message;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.sending.set(true);
    this.error.set('');

    this.contactService.send(this.form.getRawValue()).subscribe({
      next: () => {
        this.sentName.set(this.name.value.trim());
        this.sent.set(true);
        this.sending.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 0) {
          this.error.set('Cannot reach Product Service. Check that it is running on port 8081, then try again.');
        } else {
          this.error.set(err.error?.message ?? 'Your message was not sent. Please try again.');
        }
        this.sending.set(false);
      },
    });
  }

  sendAnother(): void {
    this.form.reset({ name: '', email: '', topic: 'general', message: '' });
    this.count.set(0);
    this.sent.set(false);
  }
}