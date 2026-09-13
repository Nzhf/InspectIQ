import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AlertStatus } from '../models/inspection';

@Injectable({
  providedIn: 'root',
})
export class AlertService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.alertBaseUrl;

  getStatus(): Observable<AlertStatus> {
    return this.http.get<AlertStatus>(`${this.baseUrl}/api/v1/alerts/status`);
  }
}
