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
}

