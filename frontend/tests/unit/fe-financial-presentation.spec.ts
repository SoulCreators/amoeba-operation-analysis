import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WorkbenchPage from '../../src/features/workbench/WorkbenchPage.vue'

const api = vi.hoisted(() => ({
  batch: vi.fn(), createBatch: vi.fn(), overview: vi.fn(), readiness: vi.fn(), quality: vi.fn(), datasets: vi.fn(),
  v13Overview: vi.fn(), v13Matrix: vi.fn(), v13Forecast: vi.fn(), v13Waterfall: vi.fn(), v13UnitEfficiency: vi.fn(), v13Risk: vi.fn(), v13Topic: vi.fn(),
}))
vi.mock('../../src/shared/api', () => ({ api }))
const batchNo = 'BATCH-202608-BASE-001'
const base = { batchNo, perspective: 'BASE', scenario: 'ACT_BGT', status: 'CALCULATED' }
const metric = { metricCode: 'REVENUE', metricName: '测试收入', actual: 120, budget: 100, gap: 20, gapRate: 0.2 }
const forecast = (scenario = 'ACT_FC', overrides: Record<string, unknown> = {}) => ({
  ...base, scenario, fcVersion: 'FC-202608-01', fcVersionStatus: 'PUBLISHED',
  rows: [{ ...metric, fc: 110, gap: scenario === 'FC_BGT' ? 10 : 10, gapRate: 0.1, comparison: scenario }], ...overrides,
})
let wrappers: VueWrapper[] = []
async function start() {
  const wrapper = mount(WorkbenchPage)
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
async function navigate(wrapper: VueWrapper, label: string) {
  const button = wrapper.findAll('button.nav').find((button) => button.text().includes(label))!
  await button.trigger('click')
  await flushPromises()
}
function tableCells(wrapper: VueWrapper) {
  return wrapper.findAll('section.view.active tbody td').map((cell) => cell.text())
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  localStorage.clear()
  Object.values(api).forEach((mock) => mock.mockReset())
  api.batch.mockResolvedValue({ batchNo, period: '2026-07', perspective: 'BASE', status: 'CALCULATED' })
  api.overview.mockResolvedValue([{ code: 'REVENUE', name: '旧查询', actual: 999999, budget: 1, gap: 999998 }])
  api.readiness.mockResolvedValue({ batchNo, perspective: 'BASE', readyForCalculation: false, datasets: [] })
  api.quality.mockResolvedValue({ status: 'BLOCKED', issueCount: 0 })
  api.datasets.mockResolvedValue([])
  api.v13Overview.mockResolvedValue({ ...base, metrics: [metric] })
  api.v13Matrix.mockImplementation((_batch, metricCode) => Promise.resolve({
    ...base, metricCode, rows: [{ baseCode: '基地A', productCode: '产品A', actual: 120, budget: 100, gap: 20, gapRate: 0.2 }],
  }))
  api.v13Forecast.mockImplementation((_batch, scenario) => Promise.resolve(forecast(scenario)))
  api.v13Waterfall.mockResolvedValue({ ...base, status: 'PENDING_DEFINITION', factors: [{ code: '候选因子', value: 999 }], blockers: ['分母待确认'] })
  api.v13UnitEfficiency.mockResolvedValue({ ...base, status: 'CALCULATED_TECHNICAL', rows: [{ scopeCode: '基地A', lineCode: '线体A', actualPerWh: 99 }], blockers: ['费用口径待确认'] })
  api.v13Risk.mockResolvedValue({ ...base, status: 'PARTIAL_PENDING_THRESHOLD', risks: [{ level: 'OBSERVE', metricName: '收入', message: '低于预算', thresholdStatus: 'PENDING_DEFINITION' }], blockers: ['阈值待确认'] })
  api.v13Topic.mockResolvedValue({ ...base, status: 'PENDING_SOURCE_DATA', rows: [], blockers: ['专题数据待接入'] })
})
afterEach(() => {
  wrappers.forEach((wrapper) => wrapper.unmount())
  wrappers = []
})

describe('FIN-02/03 financial display boundaries', () => {
  it('uses the loaded batch period and disables unsupported cumulative/custom comparisons', async () => {
    const wrapper = await start()
    expect(wrapper.get('[data-testid="batch-period"]').text()).toBe('2026-07')
    const period = wrapper.get('select[aria-label="期间口径"]')
    expect(period.element.disabled).toBe(true)
    expect(period.element.value).toBe('current')
    expect(period.get('option[value="ytd"]').element.disabled).toBe(true)
    expect(period.get('option[value="custom"]').element.disabled).toBe(true)
    expect(wrapper.get('select[aria-label="对比场景"]').element.disabled).toBe(true)
    expect(wrapper.text()).toContain('累计及自定义月份尚未支持')
  })

  it('shares batch creation when initialization and import navigation overlap', async () => {
    const creation = deferred<unknown>()
    api.batch.mockRejectedValueOnce(new Error('批次不存在'))
    api.createBatch.mockReturnValueOnce(creation.promise)
    const wrapper = await start()
    await navigate(wrapper, '月度数据导入')
    expect(api.createBatch).toHaveBeenCalledTimes(1)
    creation.resolve({ batchNo })
    await flushPromises()
    expect(api.createBatch).toHaveBeenCalledTimes(1)
    expect(api.datasets).toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('批次号已存在')
  })

  it('does not let the legacy overview request overwrite the batch result', async () => {
    const legacy = deferred<unknown[]>()
    api.overview.mockReturnValueOnce(legacy.promise)
    const wrapper = await start()
    expect(tableCells(wrapper)).toContain('120')
    legacy.resolve([{ code: 'REVENUE', name: '旧查询', actual: 999999, budget: 1, gap: 999998 }])
    await flushPromises()
    expect(tableCells(wrapper)).toEqual(['测试收入', '120', '100', '20', '20.0%', '—'])
    expect(wrapper.text()).not.toContain('999,999')
  })

  it.each(['PENDING_FC_VERSION', 'PENDING_SOURCE_DATA'])('hides numeric rows on overview status %s', async (status) => {
    api.v13Overview.mockResolvedValue({ ...base, status, metrics: [metric], blockers: ['数据尚不完整'] })
    const wrapper = await start()
    expect(tableCells(wrapper)).toEqual([])
    expect(wrapper.get('[data-testid="analysis-notice"]').text()).toContain('数据尚不完整')
    expect(wrapper.findAll('.cards .metric').map((card) => card.text().trim())).toEqual(Array(8).fill('—'))
  })

  it('uses the official FC without sending a placeholder version, with no interpolation', async () => {
    const wrapper = await start()
    await navigate(wrapper, 'FC预测与瀑布')
    expect(api.v13Forecast).toHaveBeenLastCalledWith(batchNo, 'ACT_FC', undefined)
    expect(tableCells(wrapper)).toEqual(['测试收入', '100', '120', '110', '10', '10.0%'])
    expect(wrapper.text()).toContain('FC-202608-01')
    expect(wrapper.text()).not.toContain('114.4')
    await wrapper.get('select[aria-label="对比场景"]').setValue('FC_BGT')
    await flushPromises()
    expect(api.v13Forecast).toHaveBeenLastCalledWith(batchNo, 'FC_BGT', undefined)
    expect(wrapper.text()).toContain('FC vs BGT')
    expect(tableCells(wrapper)).toEqual(['测试收入', '100', '120', '110', '10', '10.0%'])
  })

  it.each([
    { status: 'PENDING_FC_VERSION', blockers: ['缺少版本'] },
    { status: 'PENDING_SOURCE_DATA', blockers: ['部分覆盖'] },
    { fcVersionStatus: 'DRAFT' },
    { fcVersion: '' },
    { rows: [{ ...metric, fc: null }] },
    { rows: [{ ...metric, fc: '' }] },
    { rows: [{ ...metric, fc: Infinity }] },
    { batchNo: 'ANOTHER-BATCH' },
    { perspective: 'BUSINESS_UNIT' },
    { scenario: 'ACT_BGT' },
  ])('blocks incomplete, unpublished, or mismatched FC responses: %j', async (overrides) => {
    api.v13Forecast.mockResolvedValue(forecast('ACT_FC', overrides))
    const wrapper = await start()
    await navigate(wrapper, 'FC预测与瀑布')
    expect(tableCells(wrapper)).toEqual([])
    expect(wrapper.get('[data-testid="analysis-notice"]').text().length).toBeGreaterThan(0)
  })

  it('preserves an explicit zero FC and trims the requested version', async () => {
    api.v13Forecast.mockResolvedValue(forecast('ACT_FC', { rows: [{ ...metric, fc: 0, gap: 120, gapRate: 0.1, comparison: 'ACT_FC' }] }))
    const wrapper = await start()
    await navigate(wrapper, 'FC预测与瀑布')
    await wrapper.get('input[aria-label="预测版本"]').setValue(' FC-202608-01 ')
    await flushPromises()
    expect(api.v13Forecast).toHaveBeenLastCalledWith(batchNo, 'ACT_FC', 'FC-202608-01')
    expect(tableCells(wrapper)).toEqual(['测试收入', '100', '120', '0', '120', '—'])
  })

  it('rejects a published response for a different explicitly requested version', async () => {
    const wrapper = await start()
    await navigate(wrapper, 'FC预测与瀑布')
    await wrapper.get('input[aria-label="预测版本"]').setValue('FC-OTHER')
    await flushPromises()
    expect(tableCells(wrapper)).toEqual([])
    expect(wrapper.get('[data-testid="analysis-notice"]').text()).toContain('预测版本与当前选择不一致')
  })

  it('clears old FC numbers immediately and discards a late response after a scenario change', async () => {
    const late = deferred<ReturnType<typeof forecast>>()
    api.v13Forecast.mockReturnValueOnce(late.promise)
    const wrapper = await start()
    await navigate(wrapper, 'FC预测与瀑布')
    await wrapper.get('select[aria-label="对比场景"]').setValue('FC_BGT')
    await flushPromises()
    late.resolve(forecast('ACT_FC', { rows: [{ ...metric, fc: 888888 }] }))
    await flushPromises()
    expect(tableCells(wrapper)).toContain('110')
    expect(wrapper.text()).not.toContain('888,888')
    expect(wrapper.text()).toContain('FC vs BGT')
  })

  it('returns to ACT/BGT on leaving forecast and sends the canonical matrix metric code', async () => {
    const wrapper = await start()
    await navigate(wrapper, 'FC预测与瀑布')
    await wrapper.get('select[aria-label="对比场景"]').setValue('FC_BGT')
    await navigate(wrapper, '基地×产品矩阵')
    expect(wrapper.get('select[aria-label="对比场景"]').element.value).toBe('ACT_BGT')
    expect(tableCells(wrapper)).toEqual(['基地A', '产品A', '120', '100', '20', '20.0%', '贡献提升'])
    await wrapper.get('section.view.active select').setValue('GROSS_PROFIT')
    await flushPromises()
    expect(api.v13Matrix).toHaveBeenLastCalledWith(batchNo, 'GROSS_PROFIT')
  })

  it.each([
    { status: 'PENDING_DATA_MODEL', rows: [{ baseCode: '基地A', actual: 123456, budget: 100 }] },
    { metricCode: 'NET_PROFIT' },
    { rows: [{ baseCode: '基地A', productCode: '产品A', actual: null, budget: 100 }] },
  ])('does not turn incomplete or mismatched data into a matrix: %j', async (overrides) => {
    api.v13Matrix.mockResolvedValue({ ...base, metricCode: 'REVENUE', rows: [{ baseCode: '基地A', productCode: '产品A', actual: 120, budget: 100, gap: 20, gapRate: 0.2 }], ...overrides })
    const wrapper = await start()
    await navigate(wrapper, '基地×产品矩阵')
    expect(tableCells(wrapper)).toEqual([])
    expect(wrapper.find('[data-testid="analysis-notice"]').exists()).toBe(true)
  })
})


describe('真实接口状态与非财经入口', () => {
  it('keeps technical unit efficiency and pending-definition waterfall values hidden', async () => {
    const wrapper = await start()
    await navigate(wrapper, '单位效率分析')
    expect(tableCells(wrapper)).toEqual([])
    expect(wrapper.text()).toContain('费用口径待确认')
    expect(wrapper.text()).not.toContain('99')

    await navigate(wrapper, 'FC预测与瀑布')
    expect(wrapper.text()).toContain('分母待确认')
    expect(wrapper.text()).not.toContain('999')
  })

  it('shows risk observations as pending threshold items and the nonfinancial page as unavailable', async () => {
    const wrapper = await start()
    await navigate(wrapper, '一页风险提示')
    expect(wrapper.text()).toContain('PARTIAL_PENDING_THRESHOLD')
    expect(wrapper.text()).toContain('观察')
    expect(wrapper.text()).toContain('阈值待确认')
    await navigate(wrapper, '非财经指标')
    expect(wrapper.text()).toContain('后端尚未提供非财经指标的独立导入、结果查询和巴长型号授权契约')
    expect(tableCells(wrapper)).toEqual([])
  })

  it('blocks forecast rows missing the server comparison gap', async () => {
    api.v13Forecast.mockResolvedValue(forecast('ACT_FC', { rows: [{ metricCode: 'REVENUE', metricName: '测试收入', actual: 120, budget: 100, fc: 110, comparison: 'ACT_FC' }] }))
    const wrapper = await start()
    await navigate(wrapper, 'FC预测与瀑布')
    expect(tableCells(wrapper)).toEqual([])
    expect(wrapper.text()).toContain('缺少当前场景的服务端差额')
  })
})
