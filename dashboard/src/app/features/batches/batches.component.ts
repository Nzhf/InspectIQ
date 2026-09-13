import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { BatchListItem, PageResponse } from '../../core/models/inspection';
import { IngestionService } from '../../core/services/ingestion.service';
import { mapHttpError } from '../../core/services/error-handler.service';
import { ErrorAlertComponent } from '../../shared/components/error-alert.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner.component';

@Component({
  selector: 'app-batches',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    RouterLink,
    LoadingSpinnerComponent,
    ErrorAlertComponent,
  ],
  template: `
    <mat-card class="section-card">
      <mat-card-header>
        <mat-card-title>Production Batches</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <app-loading-spinner *ngIf="loading()" />
        <app-error-alert *ngIf="error()" [message]="error()!" />
        <ng-container *ngIf="!loading() && !error()">
          <table mat-table [dataSource]="data()" class="batches-table">
            <ng-container matColumnDef="batchCode">
              <th mat-header-cell *matHeaderCellDef>Batch Code</th>
              <td mat-cell *matCellDef="let batch">
                <a [routerLink]="['/batches', batch.id]">{{ batch.batchCode }}</a>
              </td>
            </ng-container>

            <ng-container matColumnDef="productName">
              <th mat-header-cell *matHeaderCellDef>Product</th>
              <td mat-cell *matCellDef="let batch">{{ batch.productName }}</td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let batch">{{ batch.status }}</td>
            </ng-container>

            <ng-container matColumnDef="passRate">
              <th mat-header-cell *matHeaderCellDef>Pass Rate</th>
              <td mat-cell *matCellDef="let batch">
                {{ batch.passRatePercent != null ? (batch.passRatePercent | number: '1.1-1') + '%' : '—' }}
              </td>
            </ng-container>

            <ng-container matColumnDef="totalInspections">
              <th mat-header-cell *matHeaderCellDef>Inspections</th>
              <td mat-cell *matCellDef="let batch">{{ batch.totalInspections | number }}</td>
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
        </ng-container>
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .batches-table {
        width: 100%;
      }
    `,
  ],
})
export class BatchesComponent implements OnInit {
  private readonly ingestionService = inject(IngestionService);

  loading = signal(true);
  error = signal<string | null>(null);
  data = signal<BatchListItem[]>([]);
  totalElements = signal(0);
  pageSize = signal(20);
  currentPage = signal(0);

  readonly displayedColumns = ['batchCode', 'productName', 'status', 'passRate', 'totalInspections'];

  ngOnInit(): void {
    this.loadBatches();
  }

  onPageChange(event: PageEvent): void {
    this.pageSize.set(event.pageSize);
    this.currentPage.set(event.pageIndex);
    this.loadBatches();
  }

  private loadBatches(): void {
    this.loading.set(true);
    this.ingestionService
      .listBatches(this.currentPage(), this.pageSize())
      .subscribe({
        next: (page: PageResponse<BatchListItem>) => {
          this.data.set(page.content);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(mapHttpError(err));
          this.loading.set(false);
        },
      });
  }
}
