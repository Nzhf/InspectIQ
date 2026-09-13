import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  BatchDetail,
  InspectionResponse,
  PageResponse,
} from '../../core/models/inspection';
import { IngestionService } from '../../core/services/ingestion.service';
import { mapHttpError } from '../../core/services/error-handler.service';
import { ErrorAlertComponent } from '../../shared/components/error-alert.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner.component';

@Component({
  selector: 'app-batch-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    RouterLink,
    LoadingSpinnerComponent,
    ErrorAlertComponent,
  ],
  template: `
    <a mat-button routerLink="/batches" class="back-link">&larr; Back to Batches</a>

    <app-loading-spinner *ngIf="loading()" />
    <app-error-alert *ngIf="error()" [message]="error()!" />

    <ng-container *ngIf="!loading() && !error() && batch() as b">
      <mat-card class="section-card">
        <mat-card-header>
          <mat-card-title>{{ b.batchCode }}</mat-card-title>
          <mat-card-subtitle>{{ b.productName }} — {{ b.status }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <p><strong>Pass Rate:</strong> {{ b.passRatePercent != null ? (b.passRatePercent | number: '1.1-1') + '%' : '—' }}</p>
          <p><strong>Total Inspections:</strong> {{ b.totalInspections | number }}</p>
          <p><strong>Passes:</strong> {{ b.passCount | number }} | <strong>Fails:</strong> {{ b.failCount | number }}</p>
        </mat-card-content>
      </mat-card>

      <mat-card class="section-card">
        <mat-card-header>
          <mat-card-title>Inspection Results</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="inspections()" class="inspections-table">
            <ng-container matColumnDef="inspectedAt">
              <th mat-header-cell *matHeaderCellDef>Inspected At</th>
              <td mat-cell *matCellDef="let insp">{{ insp.inspectedAt | date: 'medium' }}</td>
            </ng-container>

            <ng-container matColumnDef="result">
              <th mat-header-cell *matHeaderCellDef>Result</th>
              <td mat-cell *matCellDef="let insp">{{ insp.result }}</td>
            </ng-container>

            <ng-container matColumnDef="defectTypeCode">
              <th mat-header-cell *matHeaderCellDef>Defect Type</th>
              <td mat-cell *matCellDef="let insp">{{ insp.defectTypeCode ?? '—' }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
          </table>

          <mat-paginator
            [length]="totalElements()"
            [pageSize]="pageSize()"
            [pageSizeOptions]="[10, 20, 50]"
            (page)="onPageChange($event)"
            showFirstLastButtons
          />
        </mat-card-content>
      </mat-card>
    </ng-container>
  `,
  styles: [
    `
      .back-link {
        display: inline-block;
        margin-bottom: 1rem;
      }
      .inspections-table {
        width: 100%;
      }
    `,
  ],
})
export class BatchDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ingestionService = inject(IngestionService);

  loading = signal(true);
  error = signal<string | null>(null);
  batch = signal<BatchDetail | null>(null);
  inspections = signal<InspectionResponse[]>([]);
  totalElements = signal(0);
  pageSize = signal(20);
  currentPage = signal(0);

  private batchId = '';

  readonly displayedColumns = ['inspectedAt', 'result', 'defectTypeCode'];

  ngOnInit(): void {
    this.batchId = this.route.snapshot.paramMap.get('id') ?? '';
    this.loadBatch();
    this.loadInspections();
  }

  onPageChange(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.currentPage.set(event.pageIndex);
    this.loadInspections();
  }

  private loadBatch(): void {
    this.ingestionService.getBatch(this.batchId).subscribe({
      next: (b) => {
        this.batch.set(b);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(mapHttpError(err));
        this.loading.set(false);
      },
    });
  }

  private loadInspections(): void {
    this.ingestionService
      .listInspections(this.batchId, this.currentPage(), this.pageSize())
      .subscribe({
        next: (page: PageResponse<InspectionResponse>) => {
          this.inspections.set(page.content);
          this.totalElements.set(page.totalElements);
        },
        error: (err) => {
          this.error.set(mapHttpError(err));
        },
      });
  }
}
