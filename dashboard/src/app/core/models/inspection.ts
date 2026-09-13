export interface DashboardSummary {
  totalUnits: number;
  totalPasses: number;
  totalFails: number;
  yieldPercent: number | null;
  totalBatches: number;
}

export interface YieldPoint {
  bucket: string;
  totalUnits: number;
  passCount: number;
  failCount: number;
  yieldPercent: number | null;
}

export interface DefectDistributionItem {
  code: string;
  description: string;
  count: number;
  percentOfFails: number | null;
}

export interface BatchListItem {
  id: string;
  batchCode: string;
  productName: string;
  startedAt: string;
  completedAt: string | null;
  status: string;
  totalInspections: number;
  passCount: number;
  failCount: number;
  passRatePercent: number | null;
}

export interface BatchDetail extends BatchListItem {}

export interface InspectionResponse {
  id: string;
  batchId: string;
  result: string;
  inspectedAt: string;
  defectTypeCode: string | null;
  rawData: Record<string, unknown> | null;
}

export interface AlertStatus {
  state: 'OK' | 'ALERT' | 'INSUFFICIENT_DATA';
  yieldPercent: number | null;
  thresholdPercent: number;
  sampleSize: number;
  lastEvaluatedAt: string | null;
  lastAlertSentAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
