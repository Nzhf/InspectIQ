import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { YieldPoint } from '../../../core/models/inspection';

@Component({
  selector: 'app-yield-trend-chart',
  standalone: true,
  imports: [MatCardModule, NgxChartsModule],
  template: `
    <mat-card class="section-card">
      <mat-card-header>
        <mat-card-title>Yield Trend</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <ngx-charts-line-chart
          *ngIf="data().length > 0; else emptyTrend"
          [results]="chartData"
          [xAxis]="true"
          [yAxis]="true"
          [showXAxisLabel]="true"
          [showYAxisLabel]="true"
          [autoScale]="true"
          [yAxisLabel]="'Units'"
          [xAxisLabel]="'Period'"
        />
        <ng-template #emptyTrend>
          <p class="empty-message">No yield data available yet.</p>
        </ng-template>
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .empty-message {
        color: #666;
        padding: 1rem;
      }
    `,
  ],
})
export class YieldTrendChartComponent {
  data = input<YieldPoint[]>([]);

  /**
   * ngx-charts expects data as: [{ name: string, series: [{ name, value }] }]
   * We map each YieldPoint into a single series showing pass + fail counts.
   */
  get chartData(): { name: string; series: { name: string; value: number }[] }[] {
    return [
      {
        name: 'Passes',
        series: this.data().map((p) => ({ name: p.bucket, value: p.passCount })),
      },
      {
        name: 'Fails',
        series: this.data().map((p) => ({ name: p.bucket, value: p.failCount })),
      },
    ];
  }
}
