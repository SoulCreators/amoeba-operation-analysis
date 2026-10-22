import type { Perspective, V13AnalysisPayload } from '../../shared/api'

export type Scenario = V13AnalysisPayload['scenario']

export function finiteAmount(value: unknown): number | null {
  if (typeof value !== 'number' && typeof value !== 'string') return null
  if (typeof value === 'string' && !value.trim()) return null
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : null
}

/** Display only a response belonging to the requested batch and comparison. */
export function analysisBlocker(
  payload: V13AnalysisPayload,
  batchNo: string,
  perspective: Perspective,
  scenario: Scenario,
): string {
  if (payload.batchNo !== batchNo || payload.perspective !== perspective || payload.scenario !== scenario) {
    return '返回结果与当前批次、视角或对比场景不一致，已停止展示。'
  }
  if (payload.status !== 'CALCULATED') {
    return payload.blockers?.join('；') || `当前结果尚不可展示（${payload.status || '未知状态'}）。`
  }
  return ''
}

export function forecastBlocker(payload: V13AnalysisPayload): string {
  if (payload.fcVersionStatus !== 'PUBLISHED' || typeof payload.fcVersion !== 'string' || !payload.fcVersion.trim()) {
    return '当前预测版本未正式发布，暂不展示 FC 对比。'
  }
  if (!payload.rows?.length || payload.rows.some((row) =>
    !row.metricCode || ['actual', 'budget', 'fc'].some((key) => finiteAmount(row[key]) === null))) {
    return '预测快照字段不完整，暂不展示 FC 对比。'
  }
  return ''
}

export interface MatrixRow {
  base: string
  product: string
  actual: number
  budget: number
  gap: number
  gapRate: number | null
}

export function matrixRowsFrom(payload: V13AnalysisPayload): MatrixRow[] {
  if (!payload.rows?.length || payload.rows.some((row) =>
    !row.baseCode || !row.productCode || ['actual', 'budget', 'gap'].some((key) => finiteAmount(row[key]) === null))) return []
  return payload.rows.map((row) => ({
    base: String(row.baseCode), product: String(row.productCode),
    actual: finiteAmount(row.actual)!, budget: finiteAmount(row.budget)!, gap: finiteAmount(row.gap)!,
    gapRate: finiteAmount(row.gapRate),
  }))
}

/** Forecast rows must contain the selected server comparison; the UI never derives financial gaps. */
export function forecastRowsFrom(payload: V13AnalysisPayload, scenario: Scenario): Record<string, unknown>[] {
  const rows = payload.rows || []
  if (!rows.length) return []
  const comparison = scenario
  if (rows.some((row) => !row.metricCode || row.comparison !== comparison ||
    ['actual', 'budget', 'fc', 'gap'].some((key) => finiteAmount(row[key]) === null))) return []
  return rows
}
