import { beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  interceptors: { request: { use: vi.fn() } },
}))

vi.mock('axios', () => ({
  default: { create: vi.fn(() => httpMock) },
}))

import { api } from '../src/api'

// 本次修改：捕获 api.ts 注册的真实请求拦截器，直接验证身份头归一化契约。
type RequestConfig = { headers?: Record<string, string>; data?: unknown }
const requestInterceptor = httpMock.interceptors.request.use.mock.calls[0]?.[0] as (
  config: RequestConfig,
) => RequestConfig

describe('FE-001 API client contract', () => {
  beforeEach(() => {
    localStorage.clear()
    httpMock.get.mockReset()
    httpMock.post.mockReset()
  })

  it('normalizes demo user and maps overview aliases and numeric values', async () => {
    localStorage.setItem('amoeba.user', 'demo-finance')
    httpMock.get.mockResolvedValueOnce({
      data: {
        code: '0',
        message: 'ok',
        data: [{ metricCode: 'REVENUE', metricName: '收入', actual: '12.5', budget: 10, gap: '2.5' }],
      },
    })

    const rows = await api.overview('2026-08', 'BASE', 'ALL')
    expect(rows).toEqual([{ code: 'REVENUE', name: '收入', actual: 12.5, budget: 10, gap: 2.5 }])
    expect(httpMock.get).toHaveBeenCalledWith('/overview', { params: { period: '2026-08', perspective: 'BASE', scope: 'ALL' } })
  })

  it('accepts OK envelopes and falls back from results to pnls', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: { code: 'OK', message: '', data: { batchNo: 'B-1', perspective: 'BASE', status: 'CALCULATED', results: [{ scopeCode: 'ALL', lines: {} }] } },
    })

    await expect(api.result('B-1')).resolves.toMatchObject({ pnls: [{ scopeCode: 'ALL', lines: {} }] })
    expect(httpMock.get).toHaveBeenCalledWith('/calculations/B-1')
  })

  it('surfaces non-success envelopes as errors', async () => {
    httpMock.get.mockResolvedValueOnce({ data: { code: 'A0002', message: '未获取到认证用户', data: null } })
    await expect(api.readiness('B-1')).rejects.toThrow('未获取到认证用户')
  })

  it('sends calculation commands to the protected API path', async () => {
    httpMock.post.mockResolvedValueOnce({ data: { code: '0', message: '', data: { batchNo: 'B-1', status: 'CALCULATED' } } })
    await expect(api.calculate('B-1')).resolves.toMatchObject({ batchNo: 'B-1', status: 'CALCULATED' })
    expect(httpMock.post).toHaveBeenCalledWith('/calculations', { batchNo: 'B-1' })
  })

  // 本次修改：验证空身份值统一回退到本地联调用户，并保留请求头和业务请求体。
  it.each([
    ['null', null],
    ['undefined', undefined],
    ['empty string', ''],
    ['blank string', '   '],
  ])('falls back to local-finance for %s user values', (_label, userValue) => {
    const getItem = vi.spyOn(localStorage, 'getItem').mockReturnValue(userValue as string | null)
    const config: RequestConfig = {
      headers: { 'X-Trace-Id': 'trace-001' },
      data: { batchNo: 'B-1', perspective: 'BASE' },
    }

    try {
      const transformed = requestInterceptor(config)

      expect(transformed.headers).toEqual({ 'X-Trace-Id': 'trace-001', 'X-User-Id': 'local-finance' })
      expect(transformed.data).toEqual({ batchNo: 'B-1', perspective: 'BASE' })
    } finally {
      getItem.mockRestore()
    }
  })

  // 本次修改：验证合法用户保持原值、带空格用户被 trim，且身份只写入 X-User-Id。
  it.each([
    ['valid user', 'finance-a', 'finance-a'],
    ['spaced user', ' user-a ', 'user-a'],
  ])('normalizes %s without adding identity to the business body', (_label, storedUser, expectedUser) => {
    const getItem = vi.spyOn(localStorage, 'getItem').mockReturnValue(storedUser)
    const config: RequestConfig = {
      headers: { 'X-Trace-Id': 'trace-002' },
      data: { batchNo: 'B-2', perspective: 'PRODUCT_LINE' },
    }

    try {
      const transformed = requestInterceptor(config)

      expect(transformed.headers).toEqual({ 'X-Trace-Id': 'trace-002', 'X-User-Id': expectedUser })
      expect(transformed.data).toEqual({ batchNo: 'B-2', perspective: 'PRODUCT_LINE' })
      expect(transformed.data).not.toHaveProperty('userId')
      expect(transformed.data).not.toHaveProperty('X-User-Id')
    } finally {
      getItem.mockRestore()
    }
  })
})
