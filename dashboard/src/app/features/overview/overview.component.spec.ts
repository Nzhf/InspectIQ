import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { OverviewComponent } from './overview.component';
import { AnalyticsService } from '../../core/services/analytics.service';
import { AlertService } from '../../core/services/alert.service';

type MockFn = ReturnType<typeof vi.fn>;
type MockAnalytics = { getSummary: MockFn; getYieldTrend: MockFn; getDefectDistribution: MockFn };
type MockAlert = { getStatus: MockFn };

describe('OverviewComponent', () => {
  let component: OverviewComponent;
  let fixture: ComponentFixture<OverviewComponent>;
  let analyticsSpy: MockAnalytics;
  let alertSpy: MockAlert;

  beforeEach(async () => {
    analyticsSpy = {
      getSummary: vi.fn().mockReturnValue(of(null)),
      getYieldTrend: vi.fn().mockReturnValue(of([])),
      getDefectDistribution: vi.fn().mockReturnValue(of([])),
    };
    alertSpy = {
      getStatus: vi.fn().mockReturnValue(of(null)),
    };

    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: analyticsSpy as unknown as AnalyticsService },
        { provide: AlertService, useValue: alertSpy as unknown as AlertService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OverviewComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show error banner when API fails', fakeAsync(() => {
    analyticsSpy.getSummary.mockReturnValue(throwError(() => ({ status: 500 })));
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(component.error()).toBeTruthy();
  }));

  it('should populate summary when API succeeds', fakeAsync(() => {
    const mockSummary = {
      totalUnits: 50,
      totalPasses: 48,
      totalFails: 2,
      yieldPercent: 96.0,
      totalBatches: 2,
    };
    (analyticsSpy.getSummary as MockFn).mockReturnValue(of(mockSummary));

    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(component.summary()).toEqual(mockSummary);
    expect(component.loading()).toBeFalsy();
  }));
});
