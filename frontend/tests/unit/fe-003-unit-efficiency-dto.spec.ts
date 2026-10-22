import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkbenchPage from '../../src/features/workbench/WorkbenchPage.vue'

const apiMock = vi.hoisted(() => ({
  v13Overview: vi.fn(),
  v13UnitEfficiency: vi.fn(),
}))

vi.mock('../../src/shared/api', () => ({ api: apiMock }))

const basePayload = {
  batchNo: 'BATCH-202608-BASE-001',
  perspective: 'BASE' as const,
  scenario: 'ACT_BGT' as const,
  status: 'PENDING_DEFINITION',
  rows: [],
}

async function openUnitEfficiencyView() {
  const wrapper = mount(WorkbenchPage)
  await flushPromises()

  const unitNav = wrapper.findAll('button.nav').find((button) => button.text().includes('单位效率分析'))
  expect(unitNav).toBeDefined()
  await unitNav!.trigger('click')
  await flushPromises()
  return wrapper
}

describe('FE-UT-008 unit-efficiency DTO mapping', () => {
  beforeEach(() => {
    localStorage.clear()
    apiMock.v13Overview.mockReset()
    apiMock.v13UnitEfficiency.mockReset()
    apiMock.v13Overview.mockResolvedValue(basePayload)
  })

  it('renders the backend formal scope, manufacturing cost, Wh, and unit-cost fields', async () => {
    apiMock.v13UnitEfficiency.mockResolvedValue({
      ...basePayload,
      status: 'CALCULATED_TECHNICAL',
      rows: [{
        scopeCode: 'NANCHANG',
        lineCode: 'L01',
        actualManufacturingCost: 1234.5,
        budgetManufacturingCost: 1200,
        actualOutputWh: 98765.4,
        budgetOutputWh: 98000,
        actualPerWh: 0.1234,
        budgetPerWh: 0.1224,
        gap: 0.001,
      }],
    })

    const wrapper = await openUnitEfficiencyView()
    const cells = wrapper.findAll('section.view.active tbody tr td').map((cell) => cell.text())

    expect(cells).toEqual(['NANCHANG', 'L01', '1,234.5', '98,765.4', '0.12', '—'])
  })

  it('keeps missing numeric fields as placeholders and preserves explicit zero values', async () => {
    apiMock.v13UnitEfficiency.mockResolvedValue({
      ...basePayload,
      status: 'CALCULATED_TECHNICAL',
      rows: [{
        scopeCode: 'YICHANG',
        lineCode: 'L02',
        actualManufacturingCost: null,
        actualOutputWh: 0,
        actualPerWh: 0,
        momGap: 0,
      }],
    })

    const wrapper = await openUnitEfficiencyView()
    const cells = wrapper.findAll('section.view.active tbody tr td').map((cell) => cell.text())

    expect(cells).toEqual(['YICHANG', 'L02', '—', '0', '0', '+0'])
    expect(cells).not.toContain('NaN')
    expect(cells).not.toContain('Infinity')
  })
})
