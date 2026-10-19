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
})
