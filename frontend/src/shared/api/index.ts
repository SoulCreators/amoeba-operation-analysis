import { http, unwrap } from './http'
import type { Perspective, ExportFormat, ExportResultStatus, ExportTaskStatus, CreateExportRequest, ExportTaskResponse, ExportDownloadResponse, MetricResult, PnlResult, PvmResult, AiDraft, BatchReadiness, DatasetDefinition, MetricDefinition, MappingEntry, DataQualityReport, ConfigurationImportTask, ConfigurationImportIssue, ImportIssue, ConfigurationImportValidationResult, CalculationResultPayload, V13AnalysisPayload } from './contracts'
export type * from './contracts'

export const api = {
  health: () => unwrap(http.get('/health')),
  batch: (batchNo: string) => unwrap<{ batchNo: string; period: string; perspective: Perspective; status: string }>(http.get(`/batches/${batchNo}`)),
  overview: async (period: string, perspective: Perspective, scope = 'ALL') => {
    const rows = await unwrap<Array<{ metricCode?: string; metricName?: string; code?: string; name?: string; actual: number; budget: number; gap: number; unit?: string | null; previous?: number | string | null; aiSummary?: string }>>(http.get('/overview', { params: { period, perspective, scope } }))
    return rows.map((row): MetricResult => {
      const metric: MetricResult = {
        code: row.metricCode || row.code || '',
        name: row.metricName || row.name || row.metricCode || row.code || '',
        actual: Number(row.actual || 0),
        budget: Number(row.budget || 0),
        gap: Number(row.gap || 0),
        aiSummary: row.aiSummary,
      }
      // 本次修改：后端返回时透传 unit、previous；缺省时不增加 undefined 属性，保持兼容旧响应。
      if (row.unit !== undefined) metric.unit = row.unit
      if (row.previous !== undefined) metric.previous = row.previous === null ? null : Number(row.previous)
      return metric
    })
  },
  result: async (batchNo: string) => {
    const payload = await unwrap<CalculationResultPayload>(http.get(`/calculations/${batchNo}`))
    return { ...payload, pnls: payload.pnls || payload.results || [] }
  },
  calculate: (batchNo: string) => unwrap<CalculationResultPayload>(http.post('/calculations', { batchNo })),
  confirmCalculation: (batchNo: string) => unwrap<CalculationResultPayload>(http.post('/calculations/confirm', { batchNo })),
  publishCalculation: (batchNo: string) => unwrap<CalculationResultPayload>(http.post('/calculations/publish', { batchNo })),
  pvm: (batchNo: string) => unwrap<PvmResult[]>(http.get(`/pvm/${batchNo}`)),
  metricTree: (batchNo: string) => unwrap<MetricResult[]>(http.get(`/metric-tree/${batchNo}`)),
  ai: (batchNo: string) => unwrap<AiDraft>(http.get(`/ai/analysis/${batchNo}`)),
  generateAi: (batchNo: string) => unwrap<AiDraft>(http.post(`/ai/analysis/${batchNo}`)),
  publishAi: (batchNo: string) => unwrap<AiDraft>(http.post(`/ai/analysis/${batchNo}/publish`)),
  readiness: (batchNo: string) => unwrap<BatchReadiness>(http.get(`/batches/${batchNo}/readiness`)),
  quality: (batchNo: string) => unwrap<DataQualityReport>(http.get(`/batches/${batchNo}/quality`)),
  datasets: (perspective: Perspective) => unwrap<DatasetDefinition[]>(http.get('/datasets', { params: { perspective } })),
  metrics: (perspective?: Perspective, includeDisabled = true) => unwrap<MetricDefinition[]>(http.get('/metrics', { params: { perspective, includeDisabled } })),
  upsertMetric: (payload: Omit<MetricDefinition, 'id' | 'updatedBy' | 'updatedAt'>) => unwrap<MetricDefinition>(http.post('/metrics', payload)),
  mappings: (type: string, includeDisabled = true) => unwrap<MappingEntry[]>(http.get('/mappings', { params: { type, includeDisabled } })),
  upsertMapping: (payload: Omit<MappingEntry, 'id' | 'updatedBy' | 'updatedAt'>) => unwrap<MappingEntry>(http.post('/mappings', payload)),
  disableMapping: (payload: Pick<MappingEntry, 'type' | 'matchKey'>) => unwrap<MappingEntry>(http.post('/mappings/disable', payload)),
  createConfigImport: (payload: { type: string; fileName: string; checksum?: string }) => unwrap<ConfigurationImportTask>(http.post('/config/imports', payload)),
  configImport: (taskNo: string) => unwrap<ConfigurationImportTask>(http.get(`/config/imports/${taskNo}`)),
  uploadConfigImport: (taskNo: string, file: File) => { const form = new FormData(); form.append('file', file); return unwrap<ConfigurationImportTask>(http.post(`/config/imports/${taskNo}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' } })) },
  validateConfigImport: (taskNo: string, file: File) => { const form = new FormData(); form.append('file', file); return unwrap<ConfigurationImportValidationResult>(http.post(`/config/imports/${taskNo}/validate`, form, { headers: { 'Content-Type': 'multipart/form-data' } })) },
  configImportIssues: (taskNo: string) => unwrap<ConfigurationImportIssue[]>(http.get(`/config/imports/${taskNo}/issues`)),
  createBatch: (payload: { batchNo: string; period: string; perspective: Perspective }) => unwrap(http.post('/batches', payload)),
  moveBatch: (batchNo: string, status: string) => unwrap<{ batchNo: string; status: string }>(http.post(`/batches/${batchNo}/status`, { status })),
  createImport: (batchNo: string, payload: { datasetCode: string; fileName: string; checksum?: string }) => unwrap<{ taskNo: string }>(http.post(`/batches/${batchNo}/imports`, payload)),
  validateImport: (taskNo: string, file: File) => { const form = new FormData(); form.append('file', file); return unwrap(http.post(`/imports/${taskNo}/validate`, form, { headers: { 'Content-Type': 'multipart/form-data' } })) },
  importIssues: (taskNo: string) => unwrap<ImportIssue[]>(http.get(`/imports/${taskNo}/issues`)),
  v13Overview: (batchNo: string, scenario: V13AnalysisPayload['scenario'] = 'ACT_BGT') => unwrap<V13AnalysisPayload>(http.get(`/v13/overview/${batchNo}`, { params: { scenario } })),
  v13Matrix: (batchNo: string, metric = 'GROSS_PROFIT') => unwrap<V13AnalysisPayload>(http.get(`/v13/matrix/${batchNo}`, { params: { metric } })),
  v13UnitEfficiency: (batchNo: string) => unwrap<V13AnalysisPayload>(http.get(`/v13/unit-efficiency/${batchNo}`)),
  v13Forecast: (batchNo: string, scenario: V13AnalysisPayload['scenario'] = 'ACT_BGT', fcVersion?: string) => unwrap<V13AnalysisPayload>(http.get(`/v13/forecast/${batchNo}`, { params: { scenario, fcVersion } })),
  v13Risk: (batchNo: string) => unwrap<V13AnalysisPayload>(http.get(`/v13/risk/${batchNo}`)),
  v13Waterfall: (batchNo: string) => unwrap<V13AnalysisPayload>(http.get(`/v13/waterfall/${batchNo}`)),
  v13Topic: (batchNo: string, topicCode: string) => unwrap<V13AnalysisPayload>(http.get(`/v13/topics/${batchNo}/${topicCode}`)),
  createExport: (payload: CreateExportRequest) => unwrap<ExportTaskResponse>(http.post('/exports', payload)),
  exportStatus: (taskId: string) => unwrap<ExportTaskResponse>(http.get(`/exports/${taskId}`)),
  downloadExport: (taskId: string) => unwrap<ExportDownloadResponse>(http.get(`/exports/${taskId}/download`)),
}
