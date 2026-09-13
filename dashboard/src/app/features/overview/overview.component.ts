import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { catchError, finalize, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AlertStatus,
  DashboardSummary,
  DefectDistributionItem,
  YieldPoint,
} from '../../core/models/inspection';
import { AnalyticsService } from '../../core/services/analytics.service';
import { AlertService } from '../../core/services/alert.service';
import { mapHttpError } from '../../core/services/error-handler.service';
import { ErrorAlertComponent } from '../../shared/components/error-alert.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner.component';
import { AlertBannerComponent } from './components/alert-banner.component';
import { DefectPieChartComponent } from './components/defect-pie-chart.component';
import { KpiCardsComponent } from './components/kpi-cards.component';
import { YieldTrendChartComponent } from './components/yield-trend-chart.component';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    KpiCardsComponent,
    YieldTrendChartComponent,
    DefectPieChartComponent,
    AlertBannerComponent,
    LoadingSpinnerComponent,
    ErrorAlertComponent,
  ],
  template: `
    <app-loading-spinner *ngIf="loading()" />
    <app-error-alert *ngIf="error()" [message]="error()!" />
    <ng-container *ngIf="!loading() && !error()">
      <app-alert-banner *ngIf="enableAlerts && alertStatus()" [status]="alertStatus()!" />

      <app-kpi-cards [summary]="summary()" />

      <div class="charts-grid">
        <app-yield-trend-chart [data]="yieldTrend()" />
        <app-defect-pie-chart [data]="defects()" />
      </div>
    </ng-container>
  `,
  styles: [
    `
      .charts-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1.5rem;
      }
      @media (max-width: 768px) {
        .charts-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class OverviewComponent implements OnInit {
  private readonly analyticsService = inject(AnalyticsService);
  private readonly alertService = inject(AlertService);

  loading = signal(true);
  error = signal<string | null>(null);
  summary = signal<DashboardSummary | null>(null);
  yieldTrend = signal<YieldPoint[]>([]);
  defects = signal<DefectDistributionItem[]>([]);
  alertStatus = signal<AlertStatus | null>(null);

  protected readonly enableAlerts = environment.enableAlerts;

  ngOnInit(): void {
    this.loadData();
  }

  private loadData(): void {
    this.loading.set(true);
    this.error.set(null);

    this.analyticsService
      .getSummary()
      .pipe(
        catchError((err) => {
          this.error.set(mapHttpError(err));
          return of(null);
        }),
      )
      .subscribe((s) => this.summary.set(s));

    this.analyticsService
      .getYieldTrend('day')
      .pipe(
        catchError((err) => {
          this.error.set(mapHttpError(err));
          return of([]);
        }),
      )
      .subscribe((t) => this.yieldTrend.set(t));

    this.analyticsService
      .getDefectDistribution()
      .pipe(
        catchError((err) => {
          this.error.set(mapHttpError(err));
          return of([]);
        }),
      )
      .subscribe((d) => this.defects.set(d));

    if (this.enableAlerts) {
      this.alertService
        .getStatus()
        .pipe(
          catchError(() => of(null)),
          finalize(() => this.loading.set(false)),
        )
        .subscribe((a) => this.alertStatus.set(a));
    } else {
      this.loading.set(false);
    }
  }
}
