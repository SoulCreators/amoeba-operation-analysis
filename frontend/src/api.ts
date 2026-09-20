import axios from 'axios'

export type Perspective = 'BASE' | 'PRODUCT_LINE' | 'BUSINESS_UNIT'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  config.headers = config.headers || {}
  config.headers['X-User-Id'] = localStorage.getItem('amoeba.user') || 'demo-finance'
  return config
})

export interface ApiEnvelope<T> { code: string; message: string; data: T }
export interface MetricResult { code: string; name: string; actual: number; budget: number; gap: number; aiSummary?: string }
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

async function unwrap<T>(request: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  const response = await request
  if (response.data.code !== '0' && response.data.code !== 'OK') throw new Error(response.data.message || '接口调用失败')
  return response.data.data
}

export const api = {
  health: () => unwrap(http.get('/health')),
  overview: (period: string, perspective: Perspective, scope = 'ALL') => unwrap<MetricResult[]>(http.get('/overview', { params: { period, perspective, scope } })),
  result: (batchNo: string) => unwrap<{ batchNo: string; perspective: Perspective; status: string; versionNo?: string; pnls: PnlResult[] }>(http.get(`/calculations/${batchNo}`)),
  pvm: (batchNo: string) => unwrap<PvmResult[]>(http.get(`/pvm/${batchNo}`)),
  metricTree: (batchNo: string) => unwrap<MetricResult[]>(http.get(`/metric-tree/${batchNo}`)),
  ai: (batchNo: string) => unwrap<AiDraft>(http.get(`/ai/analysis/${batchNo}`)),
  generateAi: (batchNo: string) => unwrap<AiDraft>(http.post(`/ai/analysis/${batchNo}`)),
  readiness: (batchNo: string) => unwrap<BatchReadiness>(http.get(`/batches/${batchNo}/readiness`)),
  quality: (batchNo: string) => unwrap<DataQualityReport>(http.get(`/batches/${batchNo}/quality`)),
  datasets: (perspective: Perspective) => unwrap<DatasetDefinition[]>(http.get('/datasets', { params: { perspective } })),
  metrics: (perspective?: Perspective, includeDisabled = true) => unwrap<MetricDefinition[]>(http.get('/metrics', { params: { perspective, includeDisabled } })),
  upsertMetric: (payload: Omit<MetricDefinition, 'id' | 'updatedBy' | 'updatedAt'>) => unwrap<MetricDefinition>(http.post('/metrics', payload)),
  mappings: (type: string, includeDisabled = true) => unwrap<MappingEntry[]>(http.get('/mappings', { params: { type, includeDisabled } })),
  upsertMapping: (payload: Omit<MappingEntry, 'id' | 'updatedBy' | 'updatedAt'>) => unwrap<MappingEntry>(http.post('/mappings', payload)),
  disableMapping: (payload: Pick<MappingEntry, 'type' | 'matchKey'>) => unwrap<MappingEntry>(http.post('/mappings/disable', payload)),
  createConfigImport: (payload: { type: string; fileName: string; checksum?: string }) => unwrap<ConfigurationImportTask>(http.post('/config/imports', payload)),
  uploadConfigImport: (taskNo: string, file: File) => { const form = new FormData(); form.append('file', file); return unwrap<ConfigurationImportTask>(http.post(`/config/imports/${taskNo}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' } })) },
  validateConfigImport: (taskNo: string, file: File) => { const form = new FormData(); form.append('file', file); return unwrap<{ taskNo: string; status: string; rowCount: number; headers: string[]; issues: ConfigurationImportIssue[] }>(http.post(`/config/imports/${taskNo}/validate`, form, { headers: { 'Content-Type': 'multipart/form-data' } })) },
  configImportIssues: (taskNo: string) => unwrap<ConfigurationImportIssue[]>(http.get(`/config/imports/${taskNo}/issues`)),
  createBatch: (payload: { batchNo: string; period: string; perspective: Perspective }) => unwrap(http.post('/batches', payload)),
  createImport: (batchNo: string, payload: { datasetCode: string; fileName: string; checksum?: string }) => unwrap<{ taskNo: string }>(http.post(`/batches/${batchNo}/imports`, payload)),
  validateImport: (taskNo: string, file: File) => { const form = new FormData(); form.append('file', file); return unwrap(http.post(`/imports/${taskNo}/validate`, form, { headers: { 'Content-Type': 'multipart/form-data' } })) },
}
