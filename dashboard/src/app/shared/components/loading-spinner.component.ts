import { Component } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  template: `<div class="centered spinner-container"><mat-spinner diameter="48"></mat-spinner></div>`,
  styles: [
    `
      .spinner-container {
        padding: 2rem;
      }
    `,
  ],
})
export class LoadingSpinnerComponent {}
