export type Perspective = 'BASE' | 'PRODUCT_LINE' | 'BUSINESS_UNIT'
export type ExportFormat = 'CSV' | 'XLSX'
export type ExportResultStatus = 'CALCULATED' | 'CONFIRMED' | 'PUBLISHED'
export type ExportTaskStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED'

export interface CreateExportRequest {
  requestKey: string
  batchNo: string
  resultVersion: string
  perspective: Perspective
  format: ExportFormat
  resultStatus: ExportResultStatus
  requestedScopes?: string[]
}

export interface ExportTaskResponse {
  taskId: string
  status: ExportTaskStatus
  fileName?: string
  contentType?: string
  createdAt?: string
  updatedAt?: string
  finishedAt?: string
  expiresAt?: string
  errorMessage?: string
}

export interface ExportDownloadResponse {
  fileName: string
  contentType: string
  contentLength: number
  content: string
}

export interface ApiEnvelope<T> { code: string; message: string; data: T }
/** 本次修改：保留后端概览响应中的单位与上期值字段，不改变公式和页面展示口径。 */
export interface MetricResult { code: string; name: string; actual: number; budget: number; gap: number; gapRate?: number | null; unit?: string | null; previous?: number | null; aiSummary?: string }
export interface PnlResult { scopeCode: string; lines: Record<string, number> }
export interface PvmResult { scopeCode: string; perspective: string; actualVolume: number; budgetVolume: number; volumeGap: number; actualRevenue: number; budgetRevenue: number; revenueGap: number; actualSalesCost: number; budgetSalesCost: number; costGap: number; actualGrossProfit: number; budgetGrossProfit: number; grossProfitGap: number; [key: string]: unknown }
export interface AiDraft { batchNo: string; resultVersionNo?: string; status: string; sections: Record<string, string>; generatedBy?: string; generatedAt?: string }
export interface BatchReadiness { batchNo: string; perspective: Perspective; readyForCalculation: boolean; datasets: Array<{ datasetCode: string; datasetName: string; required: boolean; present: boolean; status: string; issueCount: number; taskNo?: string }> }
export interface DatasetDefinition { code: string; name: string; required: boolean; fields: Array<{ code: string; name: string; type: string; required: boolean }> }
export interface MetricDefinition { id?: number; metricCode: string; metricName: string; applicablePerspective: string; unit?: string; formulaExpression: string; displayOrder: number; enabled: boolean; aiSummaryTemplate?: string; updatedBy?: string; updatedAt?: string }
export interface MappingEntry { id?: number; type: string; matchKey: string; mappedValue: string; enabled: boolean; updatedBy?: string; updatedAt?: string }
export interface DataQualityReport { batchNo: string; perspective: Perspective; status: string; totalDatasetCount: number; requiredDatasetCount: number; presentRequiredDatasetCount: number; validRequiredDatasetCount: number; issueCount: number; missingDatasetCodes: string[]; invalidDatasetCodes: string[]; datasets: BatchReadiness['datasets'] }
export interface ConfigurationImportTask { taskNo: string; type: string; fileName: string; status: string; createdBy?: string; createdAt?: string; updatedAt?: string }
export interface ConfigurationImportIssue { rowNo?: number; fieldName?: string; issueCode: string; issueMessage: string; rawValue?: string }
export interface ImportIssue { rowNo?: number; fieldName?: string; issueCode: string; issueMessage: string; rawValue?: string }
export interface ConfigurationImportValidationResult { taskNo: string; status: string; rowCount: number; headers: string[]; issues: ConfigurationImportIssue[] }
export interface CalculationResultPayload {
  batchNo: string
  perspective: Perspective
  status: string
  versionNo?: string
  results?: PnlResult[]
  pnls?: PnlResult[]
  confirmedBy?: string
  publishedBy?: string
  calculatedAt?: string
  confirmedAt?: string
  publishedAt?: string
}
export interface V13AnalysisPayload {
  batchNo: string
  perspective: Perspective
  versionNo?: string
  scenario: 'ACT_BGT' | 'ACT_FC' | 'FC_BGT'
  status: string
  metrics?: Array<V13MetricRow>
  top?: Record<string, unknown>
  rows?: Array<V13MetricRow>
  risks?: Array<Record<string, unknown>>
  factors?: Array<Record<string, unknown>>
  blockers?: string[]
  notes?: string[]
  [key: string]: unknown
}
/** Financial comparison rows are server-authored; clients only format these values. */
export interface V13MetricRow extends Record<string, unknown> {
  baseCode?: string | null
  productCode?: string | null
  metricCode?: string
  metricName?: string
  actual?: number | string | null
  budget?: number | string | null
  gap?: number | string | null
  gapRate?: number | string | null
  fc?: number | string | null
  comparison?: 'ACT_BGT' | 'ACT_FC' | 'FC_BGT'
}
