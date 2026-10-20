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

/** 本次修改：验证概览 DTO 保留 unit/previous，缺失或 null 时不伪造业务值。 */
describe('FE-008 overview unit/previous 字段保留契约', () => {
  beforeEach(() => {
    localStorage.clear()
    httpMock.get.mockReset()
    httpMock.post.mockReset()
  })

  it('保留 overview 响应中明确返回的 unit 和 previous 字段', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: {
        code: '0',
        message: 'ok',
        data: [{
          metricCode: 'REVENUE',
          metricName: '收入',
          actual: 120,
          budget: 100,
          gap: 20,
          unit: '万元',
          previous: 110,
        }],
      },
    })

    const rows = await api.overview('2026-08', 'BASE', 'ALL')

    expect(rows[0]).toMatchObject({
      code: 'REVENUE',
      name: '收入',
      unit: '万元',
      previous: 110,
    })
  })

  it('响应缺失 unit 和 previous 时不伪造字段或默认值', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: {
        code: '0',
        message: 'ok',
        data: [{
          metricCode: 'COST',
          metricName: '成本',
          actual: 80,
          budget: 90,
          gap: -10,
        }],
      },
    })

    const rows = await api.overview('2026-08', 'BASE', 'ALL')

    expect(rows[0]).not.toHaveProperty('unit')
    expect(rows[0]).not.toHaveProperty('previous')
  })

  it('响应 previous 为 null 时保留 null，不参与前端默认值推导', async () => {
    httpMock.get.mockResolvedValueOnce({
      data: {
        code: '0',
        message: 'ok',
        data: [{
          metricCode: 'MARGIN',
          metricName: '毛利',
          actual: 30,
          budget: 28,
          gap: 2,
          unit: null,
          previous: null,
        }],
      },
    })

    const rows = await api.overview('2026-08', 'BASE', 'ALL')

    expect(rows[0]).toMatchObject({ unit: null, previous: null })
  })
})
