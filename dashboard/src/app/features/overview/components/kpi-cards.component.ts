import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { DashboardSummary } from '../../../core/models/inspection';

@Component({
  selector: 'app-kpi-cards',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `
    <div class="kpi-grid" *ngIf="summary() as s">
      <mat-card class="kpi-card">
        <mat-card-title>{{ s.totalUnits | number }}</mat-card-title>
        <mat-card-subtitle>Total Units</mat-card-subtitle>
      </mat-card>

      <mat-card class="kpi-card">
        <mat-card-title>{{ s.yieldPercent != null ? (s.yieldPercent | number: '1.1-1') + '%' : '—' }}</mat-card-title>
        <mat-card-subtitle>Yield Rate</mat-card-subtitle>
      </mat-card>

      <mat-card class="kpi-card">
        <mat-card-title>{{ s.totalPasses | number }}</mat-card-title>
        <mat-card-subtitle>Passes</mat-card-subtitle>
      </mat-card>

      <mat-card class="kpi-card">
        <mat-card-title>{{ s.totalFails | number }}</mat-card-title>
        <mat-card-subtitle>Fails</mat-card-subtitle>
      </mat-card>

      <mat-card class="kpi-card">
        <mat-card-title>{{ s.totalBatches | number }}</mat-card-title>
        <mat-card-subtitle>Batches</mat-card-subtitle>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .kpi-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 1rem;
      }
      .kpi-card {
        text-align: center;
      }
      mat-card-title {
        font-size: 2rem;
        font-weight: 600;
        margin-bottom: 0.5rem;
      }
    `,
  ],
})
export class KpiCardsComponent {
  summary = input<DashboardSummary | null>(null);
}
