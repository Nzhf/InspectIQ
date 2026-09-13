import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-error-alert',
  standalone: true,
  imports: [MatIconModule],
  template: `<div class="error-alert">
  <mat-icon color="warn">error_outline</mat-icon>
  <span>{{ message() }}</span>
</div>`,
  styles: [
    `
      .error-alert {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 1rem;
        margin: 1rem 0;
        background-color: #fdeded;
        border: 1px solid #f5c2c7;
        border-radius: 4px;
        color: #842029;
      }
    `,
  ],
})
export class ErrorAlertComponent {
  message = input.required<string>();
}
