import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { DefectDistributionItem } from '../../../core/models/inspection';

@Component({
  selector: 'app-defect-pie-chart',
  standalone: true,
  imports: [CommonModule, MatCardModule, NgxChartsModule],
  template: `
    <mat-card class="section-card">
      <mat-card-header>
        <mat-card-title>Defect Distribution</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <ngx-charts-pie-chart
          *ngIf="data().length > 0; else emptyDefects"
          [results]="data()"
          [legend]="true"
          [labels]="true"
        />
        <ng-template #emptyDefects>
          <p class="empty-message">No defect data available yet.</p>
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
export class DefectPieChartComponent {
  data = input<DefectDistributionItem[]>([]);
}
