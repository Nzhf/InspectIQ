import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { AnalyticsService } from './analytics.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch summary from the correct URL', () => {
    const mockSummary = {
      totalUnits: 100,
      totalPasses: 95,
      totalFails: 5,
      yieldPercent: 95.0,
      totalBatches: 3,
    };

    service.getSummary().subscribe((summary) => {
      expect(summary).toEqual(mockSummary);
    });

    const req = httpMock.expectOne('http://localhost:8082/api/v1/dashboard/summary');
    expect(req.request.method).toBe('GET');
    req.flush(mockSummary);
  });

  it('should fetch yield trend with default groupBy=day', () => {
    service.getYieldTrend().subscribe();

    const req = httpMock.expectOne((r) =>
      r.url === 'http://localhost:8082/api/v1/metrics/yield' && r.params.get('groupBy') === 'day',
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should fetch defect distribution', () => {
    service.getDefectDistribution().subscribe();

    const req = httpMock.expectOne('http://localhost:8082/api/v1/metrics/defects');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
