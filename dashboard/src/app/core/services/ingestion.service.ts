import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BatchDetail,
  BatchListItem,
  InspectionResponse,
  PageResponse,
} from '../models/inspection';

@Injectable({
  providedIn: 'root',
})
export class IngestionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.ingestionBaseUrl;

  listBatches(page = 0, size = 20): Observable<PageResponse<BatchListItem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<BatchListItem>>(`${this.baseUrl}/api/v1/batches`, { params });
  }

  getBatch(id: string): Observable<BatchDetail> {
    return this.http.get<BatchDetail>(`${this.baseUrl}/api/v1/batches/${id}`);
  }

  listInspections(batchId: string, page = 0, size = 20): Observable<PageResponse<InspectionResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (batchId) {
      params = params.set('batchId', batchId);
    }
    return this.http.get<PageResponse<InspectionResponse>>(`${this.baseUrl}/api/v1/inspections`, { params });
  }
}
