import axios from 'axios'

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

const DEFAULT_LOCAL_USER = 'local-finance'
function normalizeUserId(value?: string | null) {
  const userId = value?.trim()
  return !userId || userId === 'demo-finance' ? DEFAULT_LOCAL_USER : userId
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  config.headers = config.headers || {}
  config.headers['X-User-Id'] = normalizeUserId(localStorage.getItem('amoeba.user'))
  return config
})

export interface ApiEnvelope<T> { code: string; message: string; data: T }
/** 本次修改：保留后端概览响应中的单位与上期值字段，不改变公式和页面展示口径。 */
export interface MetricResult { code: string; name: string; actual: number; budget: number; gap: number; unit?: string | null; previous?: number | null; aiSummary?: string }
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
  metrics?: Array<Record<string, unknown>>
  top?: Record<string, unknown>
  rows?: Array<Record<string, unknown>>
  risks?: Array<Record<string, unknown>>
  factors?: Array<Record<string, unknown>>
  blockers?: string[]
  notes?: string[]
  [key: string]: unknown
}

// 本次修改：管理中心只消费服务端真实数据；列表兼容数组及常见分页容器，不在前端填充演示记录。
export interface AdminDataScope {
  perspective: Perspective | 'ALL'
  scopeCodes: string[]
}

export interface AdminScopeGrant {
  roleCode: string
  perspective: Perspective
  scopeCode: string
  canViewDetail: boolean
  canExport: boolean
  canEditConfig: boolean
}

export interface AdminUser {
  userId: string
  employeeNo?: string
  username?: string
  displayName: string
  email?: string
  roleCodes: string[]
  scopes?: AdminDataScope[]
  scopeGrants?: AdminScopeGrant[]
  enabled: boolean
}

export interface AdminRole {
  roleCode: string
  roleName: string
  description?: string
  menuCodes?: string[]
  enabled: boolean
}

export interface AdminMenu {
  menuCode: string
  menuName: string
  parentCode?: string
  path?: string
  displayOrder: number
  enabled: boolean
}

export interface AuditEvent {
  eventId: string
  traceId?: string
  operator: string
  service: string
  action: string
  objectType: string
  objectId: string
  fromState?: string
  toState?: string
  result: string
  reason?: string
  metadata?: string
  occurredAt: string
}

export interface AuditEventFilters {
  eventId?: string
  traceId?: string
  operator?: string
  service?: string
  action?: string
  objectType?: string
  objectId?: string
  result?: string
  occurredFrom?: string
  occurredTo?: string
}

type AdminListPayload<T> = T[] | { items?: T[]; content?: T[]; records?: T[] }

async function unwrap<T>(request: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  const response = await request
  if (response.data.code !== '0' && response.data.code !== 'OK') throw new Error(response.data.message || '接口调用失败')
  return response.data.data
}

function normalizeAdminList<T>(payload: AdminListPayload<T>): T[] {
  if (Array.isArray(payload)) return payload
  return payload.items || payload.content || payload.records || []
}

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
  // 本次修改：用户、角色和菜单的更新均按合同 PUT 集合地址，实体主键保留在请求体中。
  adminUsers: async () => normalizeAdminList(await unwrap<AdminListPayload<AdminUser>>(http.get('/admin/users'))),
  createAdminUser: (payload: AdminUser) => unwrap<AdminUser>(http.post('/admin/users', payload)),
  updateAdminUser: (payload: AdminUser) => unwrap<AdminUser>(http.put('/admin/users', payload)),
  adminRoles: async () => normalizeAdminList(await unwrap<AdminListPayload<AdminRole>>(http.get('/admin/roles'))),
  createAdminRole: (payload: AdminRole) => unwrap<AdminRole>(http.post('/admin/roles', payload)),
  updateAdminRole: (payload: AdminRole) => unwrap<AdminRole>(http.put('/admin/roles', payload)),
  adminMenus: async () => normalizeAdminList(await unwrap<AdminListPayload<AdminMenu>>(http.get('/admin/menus'))),
  createAdminMenu: (payload: AdminMenu) => unwrap<AdminMenu>(http.post('/admin/menus', payload)),
  updateAdminMenu: (payload: AdminMenu) => unwrap<AdminMenu>(http.put('/admin/menus', payload)),
  updateAdminUserScopes: (userId: string, roleCodes: string[], scopes: AdminScopeGrant[]) => unwrap<AdminUser>(http.put(`/admin/users/${encodeURIComponent(userId)}/scopes`, { roleCodes, scopes })),
  updateAdminRoleMenus: (roleCode: string, menuCodes: string[]) => unwrap<AdminRole>(http.put(`/admin/roles/${encodeURIComponent(roleCode)}/menus`, { menuCodes })),
  // 本次修改：审计页面直接调用只读查询接口，空筛选项由 Axios 忽略或以空字符串传递，不生成前端记录。
  auditLogs: (params: AuditEventFilters = {}) => unwrap<AuditEvent[]>(http.get('/audit-logs', { params })),
}
