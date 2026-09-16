import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { AlertStatus } from '../../../core/models/inspection';

@Component({
  selector: 'app-alert-banner',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `
    <mat-card class="alert-banner" [class.ok]="status()?.state === 'OK'" [class.alert]="status()?.state === 'ALERT'" [class.insufficient]="status()?.state === 'INSUFFICIENT_DATA'">
      <mat-card-content>
        <strong>Alert Status:</strong>
        <ng-container [ngSwitch]="status()?.state">
          <span *ngSwitchCase="'OK'">Yield is within threshold ({{ status()?.yieldThresholdPercent | number: '1.1-1' }}%)</span>
          <span *ngSwitchCase="'ALERT'" class="alert-text">Yield has dropped below threshold! Current: {{ status()?.lastYieldPercent != null ? (status()?.lastYieldPercent | number: '1-1') + '%' : 'N/A' }}</span>
          <span *ngSwitchCase="'INSUFFICIENT_DATA'">Not enough data to evaluate ({{ status()?.lastSampleUnits }} samples)</span>
        </ng-container>
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .alert-banner {
        margin-bottom: 1rem;
      }
      .ok {
        background-color: #d1e7dd;
        border: 1px solid #badbcc;
      }
      .alert {
        background-color: #f8d7da;
        border: 1px solid #f5c2c7;
      }
      .alert-text {
        color: #842029;
        font-weight: 600;
      }
      .insufficient {
        background-color: #fff3cd;
        border: 1px solid #ffecb5;
      }
    `,
  ],
})
export class AlertBannerComponent {
  status = input<AlertStatus | null>(null);
}
