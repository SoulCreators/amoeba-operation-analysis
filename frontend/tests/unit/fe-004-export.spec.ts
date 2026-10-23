import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  interceptors: { request: { use: vi.fn() } },
}))

const appApiMock = vi.hoisted(() => ({
  batch: vi.fn(),
  overview: vi.fn(),
  readiness: vi.fn(),
  quality: vi.fn(),
  datasets: vi.fn(),
  result: vi.fn(),
  createExport: vi.fn(),
  exportStatus: vi.fn(),
  downloadExport: vi.fn(),
  v13Overview: vi.fn(),
}))

vi.mock('axios', () => ({
  default: { create: vi.fn(() => httpMock) },
}))

import { api, type ExportTaskStatus } from '../../src/shared/api'

const exportRequest = {
  requestKey: 'REQ-EXPORT-001-001',
  batchNo: 'BATCH-202608-BASE-001',
  resultVersion: 'RV-202608-001',
  perspective: 'BASE' as const,
  format: 'XLSX' as const,
  resultStatus: 'CALCULATED' as const,
  requestedScopes: ['NANCHANG', 'YICHANG'],
}

async function downloadWhenSucceeded(taskId: string) {
  const task = await api.exportStatus(taskId)
  return task.status === 'SUCCEEDED' ? api.downloadExport(taskId) : null
}

describe('FE-EXPORT-001 export client contract', () => {
  beforeEach(() => {
    httpMock.get.mockReset()
    httpMock.post.mockReset()
  })

  it('creates an export task with the complete request contract and no client user id', async () => {
    const task = { taskId: 'TASK-001', status: 'QUEUED' as const }
    httpMock.post.mockResolvedValueOnce({ data: { code: '0', message: '', data: task } })

    await expect(api.createExport(exportRequest)).resolves.toEqual(task)
    expect(httpMock.post).toHaveBeenCalledWith('/exports', exportRequest)
    expect(httpMock.post.mock.calls[0][1]).not.toHaveProperty('userId')
  })

  it('reads the current export task status and unwraps the response envelope', async () => {
    const task = {
      taskId: 'TASK-002',
      status: 'RUNNING' as const,
      fileName: '经营分析.xlsx',
    }
    httpMock.get.mockResolvedValueOnce({ data: { code: 'OK', message: '', data: task } })

    await expect(api.exportStatus('TASK-002')).resolves.toEqual(task)
    expect(httpMock.get).toHaveBeenCalledWith('/exports/TASK-002')
  })

  it('downloads the file after the task reaches SUCCEEDED', async () => {
    const task = { taskId: 'TASK-003', status: 'SUCCEEDED' as const }
    const file = {
      fileName: '经营分析.xlsx',
      contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      contentLength: 4,
      content: 'UEsDBA==',
    }
    httpMock.get
      .mockResolvedValueOnce({ data: { code: '0', message: '', data: task } })
      .mockResolvedValueOnce({ data: { code: '0', message: '', data: file } })

    await expect(downloadWhenSucceeded(task.taskId)).resolves.toEqual(file)
    expect(httpMock.get).toHaveBeenNthCalledWith(1, '/exports/TASK-003')
    expect(httpMock.get).toHaveBeenNthCalledWith(2, '/exports/TASK-003/download')
  })

  it.each<ExportTaskStatus>(['QUEUED', 'RUNNING', 'FAILED', 'EXPIRED'])(
    'does not request a download while task status is %s',
    async (status) => {
      httpMock.get.mockResolvedValueOnce({
        data: { code: '0', message: '', data: { taskId: 'TASK-004', status } },
      })

      await expect(downloadWhenSucceeded('TASK-004')).resolves.toBeNull()
      expect(httpMock.get).toHaveBeenCalledTimes(1)
      expect(httpMock.get).not.toHaveBeenCalledWith('/exports/TASK-004/download')
    },
  )
})

function resetAppApi(finalStatus: 'SUCCEEDED' | 'FAILED' | 'EXPIRED') {
  Object.values(appApiMock).forEach((mock) => mock.mockReset())
  appApiMock.batch.mockResolvedValue({ batchNo: 'BATCH-202608-BASE-001', period: '2026-08', perspective: 'BASE', status: 'CALCULATED' })
  appApiMock.overview.mockResolvedValue([])
  appApiMock.readiness.mockResolvedValue({ batchNo: 'BATCH-202608-BASE-001', perspective: 'BASE', readyForCalculation: true, datasets: [] })
  appApiMock.quality.mockResolvedValue({ batchNo: 'BATCH-202608-BASE-001', perspective: 'BASE', status: 'READY', totalDatasetCount: 0, requiredDatasetCount: 0, presentRequiredDatasetCount: 0, validRequiredDatasetCount: 0, issueCount: 0, missingDatasetCodes: [], invalidDatasetCodes: [], datasets: [] })
  appApiMock.datasets.mockResolvedValue([])
  appApiMock.v13Overview.mockResolvedValue({ batchNo: 'BATCH-202608-BASE-001', perspective: 'BASE', scenario: 'ACT_BGT', status: 'CALCULATED', metrics: [] })
  appApiMock.result.mockResolvedValue({ batchNo: 'BATCH-202608-BASE-001', perspective: 'BASE', status: 'CALCULATED', versionNo: 'RV-202608-001', pnls: [] })
  appApiMock.createExport.mockResolvedValue({ taskId: 'TASK-APP-001', status: 'QUEUED' })
  appApiMock.exportStatus.mockResolvedValue({ taskId: 'TASK-APP-001', status: finalStatus })
  appApiMock.downloadExport.mockResolvedValue({ fileName: '经营分析.csv', contentType: 'text/csv', contentLength: 3, content: 'YWJj' })
}

async function mountExportApp() {
  vi.resetModules()
  vi.doMock('../../src/shared/api', () => ({ api: appApiMock }))
  const { default: WorkbenchPage } = await import('../../src/features/workbench/WorkbenchPage.vue')
  const wrapper = mount(WorkbenchPage)
  await flushPromises()
  return wrapper
}

describe('FE-EXPORT-001 WorkbenchPage export lifecycle', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:export'),
      revokeObjectURL: vi.fn(),
    })
    vi.spyOn(window, 'atob').mockReturnValue('abc')
  })

  it('polls a queued task and downloads only after exportStatus returns SUCCEEDED', async () => {
    resetAppApi('SUCCEEDED')
    const wrapper = await mountExportApp()
    const exportButton = wrapper.findAll('button.btn').find((button) => button.text() === '导出')
    expect(exportButton).toBeDefined()

    await exportButton!.trigger('click')
    await flushPromises()
    expect(appApiMock.createExport).toHaveBeenCalledWith({
      requestKey: 'FE-EXPORT-001:经营总览:BATCH-202608-BASE-001:RV-202608-001:BASE:ALL',
      batchNo: 'BATCH-202608-BASE-001',
      resultVersion: 'RV-202608-001',
      perspective: 'BASE',
      format: 'CSV',
      resultStatus: 'CALCULATED',
      requestedScopes: [],
    })
    expect(appApiMock.exportStatus).not.toHaveBeenCalled()
    expect(appApiMock.downloadExport).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    expect(appApiMock.exportStatus).toHaveBeenCalledWith('TASK-APP-001')
    expect(appApiMock.downloadExport).toHaveBeenCalledWith('TASK-APP-001')
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it.each<['FAILED' | 'EXPIRED']>([['FAILED'], ['EXPIRED']])(
    'does not download when exportStatus returns %s',
    async ([finalStatus]) => {
      resetAppApi(finalStatus)
      const wrapper = await mountExportApp()
      const exportButton = wrapper.findAll('button.btn').find((button) => button.text() === '导出')
      expect(exportButton).toBeDefined()

      await exportButton!.trigger('click')
      await flushPromises()
      await vi.advanceTimersByTimeAsync(500)
      await flushPromises()

      expect(appApiMock.exportStatus).toHaveBeenCalledWith('TASK-APP-001')
      expect(appApiMock.downloadExport).not.toHaveBeenCalled()
      expect(URL.createObjectURL).not.toHaveBeenCalled()
      wrapper.unmount()
    },
  )
})
