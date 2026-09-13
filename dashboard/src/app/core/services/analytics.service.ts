import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DashboardSummary,
  DefectDistributionItem,
  YieldPoint,
} from '../models/inspection';

@Injectable({
  providedIn: 'root',
})
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.analyticsBaseUrl;

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/api/v1/dashboard/summary`);
  }

  getYieldTrend(groupBy: 'day' | 'batch' = 'day', from?: string, to?: string): Observable<YieldPoint[]> {
    let params = new HttpParams().set('groupBy', groupBy);
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<YieldPoint[]>(`${this.baseUrl}/api/v1/metrics/yield`, { params });
  }

  getDefectDistribution(batchId?: string, from?: string, to?: string): Observable<DefectDistributionItem[]> {
    let params = new HttpParams();
    if (batchId) {
      params = params.set('batchId', batchId);
    }
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<DefectDistributionItem[]>(`${this.baseUrl}/api/v1/metrics/defects`, { params });
  }
}
