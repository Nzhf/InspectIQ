import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { IngestionService } from './ingestion.service';

describe('IngestionService', () => {
  let service: IngestionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(IngestionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch paginated batch list', () => {
    service.listBatches(0, 20).subscribe();

    const req = httpMock.expectOne((r) =>
      r.url === 'http://localhost:8081/api/v1/batches' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it('should fetch a single batch by id', () => {
    const id = '12345678-1234-1234-1234-123456789abc';
    service.getBatch(id).subscribe();

    const req = httpMock.expectOne(`http://localhost:8081/api/v1/batches/${id}`);
    expect(req.request.method).toBe('GET');
    req.flush({ id, batchCode: 'B001' });
  });

  it('should fetch paginated inspections for a batch', () => {
    const batchId = '12345678-1234-1234-1234-123456789abc';
    service.listInspections(batchId, 0, 10).subscribe();

    const req = httpMock.expectOne((r) =>
      r.url === 'http://localhost:8081/api/v1/inspections' &&
        r.params.get('batchId') === batchId &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '10',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });
});
