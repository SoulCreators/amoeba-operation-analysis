import { beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  interceptors: { request: { use: vi.fn() } },
}))

vi.mock('axios', () => ({
  default: { create: vi.fn(() => httpMock) },
}))

import { api } from '../../src/shared/api'

type ApiEnvelope<T> = { code: string; message: string; data: T }

function successEnvelope<T>(data: T): { data: ApiEnvelope<T> } {
  return { data: { code: '0', message: 'ok', data } }
}

function errorEnvelope(message: string): { data: ApiEnvelope<null> } {
  return { data: { code: 'CONFIG_IMPORT_INVALID', message, data: null } }
}

function createImportFile() {
  return new File(['配置导入内容'], 'metrics.xlsx', {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
}

// 本次修改：为配置导入 API 增加基于当前 api.ts 实现的请求路径、字段、响应 envelope 和错误 envelope 契约测试。
describe('FE-CONFIG-IMPORT-API-001 配置导入 API 契约', () => {
  beforeEach(() => {
    httpMock.get.mockReset()
    httpMock.post.mockReset()
  })

  // 本次修改：验证创建配置导入任务的请求体和成功 envelope 解包结果。
  it('创建任务时发送 type、fileName、checksum 并解包成功响应', async () => {
    const request = { type: 'METRIC', fileName: 'metrics.xlsx', checksum: 'sha256-001' }
    const task = { taskNo: 'IMPORT-001', type: 'METRIC', fileName: 'metrics.xlsx', status: 'CREATED' }
    httpMock.post.mockResolvedValueOnce(successEnvelope(task))

    await expect(api.createConfigImport(request)).resolves.toEqual(task)
    expect(httpMock.post).toHaveBeenCalledWith('/config/imports', request)
  })

  // 本次修改：验证任务详情接口同时承担配置导入状态查询，路径使用 taskNo 且响应 envelope 被正确解包。
  it('查询任务状态时请求 taskNo 路径并返回状态数据', async () => {
    const task = { taskNo: 'IMPORT-001', type: 'METRIC', fileName: 'metrics.xlsx', status: 'VALID' }
    httpMock.get.mockResolvedValueOnce(successEnvelope(task))

    await expect(api.configImport('IMPORT-001')).resolves.toEqual(task)
    expect(httpMock.get).toHaveBeenCalledWith('/config/imports/IMPORT-001')
  })

  // 本次修改：验证问题明细接口的 taskNo 路径和数组 data envelope。
  it('查询问题明细时请求 taskNo 路径并返回问题数组', async () => {
    const issues = [{ rowNo: 2, fieldName: 'metricCode', issueCode: 'REQUIRED', issueMessage: '指标编码不能为空' }]
    httpMock.get.mockResolvedValueOnce(successEnvelope(issues))

    await expect(api.configImportIssues('IMPORT-001')).resolves.toEqual(issues)
    expect(httpMock.get).toHaveBeenCalledWith('/config/imports/IMPORT-001/issues')
  })

  // 本次修改：验证上传接口使用 FormData 的 file 字段，并保留当前实现声明的 multipart 类型请求配置。
  it('上传文件时提交包含 file 字段的 FormData', async () => {
    const file = createImportFile()
    const task = { taskNo: 'IMPORT-001', type: 'METRIC', fileName: 'metrics.xlsx', status: 'UPLOADED' }
    httpMock.post.mockResolvedValueOnce(successEnvelope(task))

    await expect(api.uploadConfigImport('IMPORT-001', file)).resolves.toEqual(task)

    const [path, body, config] = httpMock.post.mock.calls[0]
    expect(path).toBe('/config/imports/IMPORT-001/upload')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('file')).toBe(file)
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
  })

  // 本次修改：验证校验接口与上传接口使用独立的 validate 路径，并提交同名 file 字段。
  it('校验文件时提交 file 字段并返回校验结果', async () => {
    const file = createImportFile()
    const validation = {
      taskNo: 'IMPORT-001',
      status: 'INVALID',
      rowCount: 1,
      headers: ['metricCode'],
      issues: [{ rowNo: 2, fieldName: 'metricCode', issueCode: 'REQUIRED', issueMessage: '指标编码不能为空' }],
    }
    httpMock.post.mockResolvedValueOnce(successEnvelope(validation))

    await expect(api.validateConfigImport('IMPORT-001', file)).resolves.toEqual(validation)

    const [path, body, config] = httpMock.post.mock.calls[0]
    expect(path).toBe('/config/imports/IMPORT-001/validate')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('file')).toBe(file)
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
  })

  // 本次修改：验证配置导入相关接口统一将非 0/OK envelope 转换为可识别的业务错误。
  it('遇到错误 envelope 时抛出后端 message', async () => {
    httpMock.get.mockResolvedValueOnce(errorEnvelope('配置导入任务不存在'))

    await expect(api.configImport('IMPORT-MISSING')).rejects.toThrow('配置导入任务不存在')
    expect(httpMock.get).toHaveBeenCalledWith('/config/imports/IMPORT-MISSING')
  })
})
