import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkbenchPage from '../../src/features/workbench/WorkbenchPage.vue'

// 本次修改：为基础配置导入建立前端状态编排契约测试，锁定创建、上传、校验和失败展示顺序。
const apiMock = vi.hoisted(() => ({
  batch: vi.fn(),
  createBatch: vi.fn(),
  overview: vi.fn(),
  readiness: vi.fn(),
  quality: vi.fn(),
  datasets: vi.fn(),
  createConfigImport: vi.fn(),
  configImport: vi.fn(),
  uploadConfigImport: vi.fn(),
  validateConfigImport: vi.fn(),
  configImportIssues: vi.fn(),
}))

vi.mock('../../src/shared/api', () => ({ api: apiMock }))

const batchNo = 'BATCH-202608-BASE-001'
const taskNo = 'CONFIG-IMPORT-001'

function task(status = 'CREATED') {
  return { taskNo, type: 'SALES_PROJECT_MAPPING', fileName: 'mapping.xlsx', status }
}

function validation(status = 'VALID') {
  return { taskNo, status, rowCount: 1, headers: ['matchKey', 'mappedValue'], issues: [] }
}

async function openConfigImportView() {
  const wrapper = mount(WorkbenchPage)
  await flushPromises()
  const nav = wrapper.findAll('button.nav').find((button) => button.text().includes('基础配置导入'))
  expect(nav).toBeDefined()
  await nav!.trigger('click')
  await flushPromises()
  return wrapper
}

async function chooseFile(wrapper: ReturnType<typeof mount>, file = new File(['mapping'], 'mapping.xlsx')) {
  const input = wrapper.find('section.view.active input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
  return file
}

async function submitConfigImport(wrapper: ReturnType<typeof mount>) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes('创建任务并导入'))
  expect(button).toBeDefined()
  await button!.trigger('click')
  await flushPromises()
}

describe('FE-CONFIG-IMPORT-STATE-001 configuration import state orchestration', () => {
  beforeEach(() => {
    localStorage.clear()
    Object.values(apiMock).forEach((mock) => mock.mockReset())
    apiMock.batch.mockResolvedValue({ batchNo, period: '2026-08', perspective: 'BASE', status: 'DRAFT' })
    apiMock.overview.mockResolvedValue([])
    apiMock.readiness.mockResolvedValue({ batchNo, perspective: 'BASE', readyForCalculation: false, datasets: [] })
    apiMock.quality.mockResolvedValue({
      batchNo,
      perspective: 'BASE',
      status: 'BLOCKED',
      totalDatasetCount: 0,
      requiredDatasetCount: 0,
      presentRequiredDatasetCount: 0,
      validRequiredDatasetCount: 0,
      issueCount: 0,
      missingDatasetCodes: [],
      invalidDatasetCodes: [],
      datasets: [],
    })
    apiMock.datasets.mockResolvedValue([])
    apiMock.configImport.mockResolvedValue(task())
    apiMock.configImportIssues.mockResolvedValue([])
  })

  // 本次修改：验证创建任务失败时流程立即停止，不会上传或校验文件。
  it('does not upload when task creation fails', async () => {
    apiMock.createConfigImport.mockRejectedValueOnce(new Error('创建任务失败'))

    const wrapper = await openConfigImportView()
    await chooseFile(wrapper)
    await submitConfigImport(wrapper)

    expect(apiMock.createConfigImport).toHaveBeenCalledWith({ type: 'SALES_PROJECT_MAPPING', fileName: 'mapping.xlsx' })
    expect(apiMock.uploadConfigImport).not.toHaveBeenCalled()
    expect(apiMock.validateConfigImport).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('配置导入失败：创建任务失败')
  })

  // 本次修改：验证创建接口返回的 taskNo 被原样传递给上传和校验接口。
  it('uses the taskNo returned by creation for upload and validation', async () => {
    const created = { ...task(), taskNo: 'CONFIG-IMPORT-RETURNED' }
    apiMock.createConfigImport.mockResolvedValueOnce(created)
    apiMock.uploadConfigImport.mockResolvedValueOnce({ ...created, status: 'VALIDATING' })
    apiMock.validateConfigImport.mockResolvedValueOnce({ ...validation(), taskNo: created.taskNo })
    apiMock.configImport.mockResolvedValueOnce({ ...created, status: 'VALID' })

    const wrapper = await openConfigImportView()
    await chooseFile(wrapper)
    await submitConfigImport(wrapper)

    expect(apiMock.uploadConfigImport).toHaveBeenCalledWith(created.taskNo, expect.any(File))
    expect(apiMock.validateConfigImport).toHaveBeenCalledWith(created.taskNo, expect.any(File))
    expect(wrapper.text()).toContain(created.taskNo)
  })

  // 本次修改：验证上传完成前不会调用校验，确保两步服务端编排顺序不可逆。
  it('validates only after upload succeeds', async () => {
    const events: string[] = []
    apiMock.createConfigImport.mockImplementationOnce(async () => {
      events.push('create')
      return task()
    })
    apiMock.uploadConfigImport.mockImplementationOnce(async () => {
      events.push('upload')
      return { ...task(), status: 'VALIDATING' }
    })
    apiMock.validateConfigImport.mockImplementationOnce(async () => {
      events.push('validate')
      return validation()
    })
    apiMock.configImport.mockImplementationOnce(async () => {
      events.push('query')
      return { ...task(), status: 'VALID' }
    })

    const wrapper = await openConfigImportView()
    await chooseFile(wrapper)
    await submitConfigImport(wrapper)

    expect(events).toEqual(['create', 'upload', 'validate', 'query'])
    expect(apiMock.validateConfigImport).toHaveBeenCalledTimes(1)
  })

  // 本次修改：验证校验失败仍保留最终任务状态和问题明细，不把失败结果展示成成功。
  it('keeps invalid status and issue details visible after validation failure', async () => {
    const invalidIssue = { rowNo: 2, fieldName: 'mappedValue', issueCode: 'REQUIRED', issueMessage: '映射值不能为空', rawValue: '' }
    apiMock.createConfigImport.mockResolvedValueOnce(task())
    apiMock.uploadConfigImport.mockResolvedValueOnce({ ...task(), status: 'VALIDATING' })
    apiMock.validateConfigImport.mockResolvedValueOnce({ ...validation('INVALID'), issues: [invalidIssue] })
    apiMock.configImport.mockResolvedValueOnce({ ...task('INVALID') })
    apiMock.configImportIssues.mockResolvedValueOnce([invalidIssue])

    const wrapper = await openConfigImportView()
    await chooseFile(wrapper)
    await submitConfigImport(wrapper)

    expect(wrapper.text()).toContain('校验/应用失败')
    expect(wrapper.text()).toContain('映射值不能为空')
    expect(wrapper.text()).toContain('配置未写入')
    expect(wrapper.text()).not.toContain('校验并应用通过：1 行配置已生效')
  })

  // 本次修改：验证创建成功后的上传异常会展示已知任务状态，并明确声明未伪装为成功。
  it('does not present success when a created task later fails', async () => {
    apiMock.createConfigImport.mockResolvedValueOnce(task())
    apiMock.uploadConfigImport.mockRejectedValueOnce(new Error('上传失败'))
    apiMock.configImport.mockResolvedValueOnce({ ...task('CREATED') })

    const wrapper = await openConfigImportView()
    await chooseFile(wrapper)
    await submitConfigImport(wrapper)

    expect(apiMock.validateConfigImport).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain(`任务 ${taskNo} 处理失败：上传失败`)
    expect(wrapper.text()).toContain('未伪装为成功')
    expect(wrapper.text()).not.toContain('校验并应用通过')
  })

  // 本次修改：验证 USER_PERMISSION 只提示管理员授权，不推断权限文件已经生效或已获授权。
  it('keeps USER_PERMISSION pending administrator authorization without inferring approval', async () => {
    const permissionTask = { ...task(), type: 'USER_PERMISSION', status: 'VALID' }
    apiMock.createConfigImport.mockResolvedValueOnce(permissionTask)
    apiMock.uploadConfigImport.mockResolvedValueOnce(permissionTask)
    apiMock.validateConfigImport.mockResolvedValueOnce({ ...validation(), taskNo, status: 'VALID' })
    apiMock.configImport.mockResolvedValueOnce(permissionTask)

    const wrapper = await openConfigImportView()
    const typeSelect = wrapper.find('section.view.active select')
    await typeSelect.setValue('USER_PERMISSION')
    await chooseFile(wrapper, new File(['permission'], 'permissions.xlsx'))
    await submitConfigImport(wrapper)

    const alert = wrapper.find('section.view.active .alert').text()
    expect(alert).toContain('仍需管理员授权')
    expect(alert).not.toContain('已生效')
    expect(alert).not.toContain('授权成功')
  })
})
