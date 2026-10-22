import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkbenchPage from '../../src/features/workbench/WorkbenchPage.vue'

const apiMock = vi.hoisted(() => ({
  batch: vi.fn(),
  createBatch: vi.fn(),
  overview: vi.fn(),
  v13Overview: vi.fn(),
  readiness: vi.fn(),
  quality: vi.fn(),
  datasets: vi.fn(),
}))

vi.mock('../../src/shared/api', () => ({ api: apiMock }))

function readyResponse(readyForCalculation = false) {
  return { batchNo: 'BATCH-202608-BASE-001', perspective: 'BASE', readyForCalculation, datasets: [] }
}

describe('FE-002 workbench state isolation', () => {
  beforeEach(() => {
    localStorage.clear()
    apiMock.batch.mockReset()
    apiMock.createBatch.mockReset()
    apiMock.overview.mockReset()
    apiMock.v13Overview.mockReset()
    apiMock.readiness.mockReset()
    apiMock.quality.mockReset()
    apiMock.datasets.mockReset()
    apiMock.batch.mockResolvedValue({ batchNo: 'BATCH-202608-BASE-001', period: '2026-08', perspective: 'BASE', status: 'DRAFT' })
    apiMock.overview.mockResolvedValue([])
    apiMock.v13Overview.mockResolvedValue({ batchNo: 'BATCH-202608-BASE-001', perspective: 'BASE', scenario: 'ACT_BGT', status: 'CALCULATED', metrics: [] })
    apiMock.readiness.mockResolvedValue(readyResponse(false))
    apiMock.quality.mockResolvedValue({ batchNo: 'BATCH-202608-BASE-001', perspective: 'BASE', status: 'BLOCKED', totalDatasetCount: 0, requiredDatasetCount: 0, presentRequiredDatasetCount: 0, validRequiredDatasetCount: 0, issueCount: 0, missingDatasetCodes: [], invalidDatasetCodes: [], datasets: [] })
    apiMock.datasets.mockResolvedValue([])
  })

  it('keeps calculation disabled while readiness is not satisfied', async () => {
    const wrapper = mount(WorkbenchPage)
    await flushPromises()
    const importNav = wrapper.findAll('button').find((button) => button.text().includes('月度数据导入'))
    expect(importNav).toBeDefined()
    await importNav!.trigger('click')
    await flushPromises()
    const calculateButton = wrapper.findAll('button').find((button) => button.text().includes('开始计算'))
    expect(calculateButton).toBeDefined()
    expect(calculateButton.text()).toContain('开始计算')
    expect((calculateButton.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('clears the workbench and shows a real-interface error after a failed refresh', async () => {
    apiMock.v13Overview.mockResolvedValueOnce({ batchNo: 'BATCH-202608-BASE-001', perspective: 'BASE', scenario: 'ACT_BGT', status: 'CALCULATED', metrics: [{ metricCode: 'REVENUE', metricName: '收入', actual: 12, budget: 10, gap: 2 }] })
    const wrapper = mount(WorkbenchPage)
    await flushPromises()
    expect(wrapper.text()).toContain('收入')

    apiMock.batch.mockRejectedValueOnce(new Error('network down'))
    await wrapper.get('button.btn.primary').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('真实数据接口暂不可用：network down')
    expect(wrapper.text()).not.toContain('12')
    expect(wrapper.text()).toContain('暂无真实经营指标')
  })
})
