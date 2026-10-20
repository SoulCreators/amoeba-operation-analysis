import { expect, test } from '@playwright/test'

const taskNo = 'CONFIG-IMPORT-FROM-CREATE'

type JsonRecord = Record<string, unknown>
type MultipartKind = 'upload' | 'validate'

type MultipartRequest = {
  method: string
  kind: MultipartKind
  taskNo: string
  contentType: string
  body: Buffer
}

function envelope(data: unknown): JsonRecord {
  return { code: 'OK', message: '成功', data }
}

function multipartBoundary(contentType: string) {
  const match = contentType.match(/^multipart\/form-data;\s*boundary=(?:"([^"]+)"|([^;]+))$/i)
  expect(match).not.toBeNull()
  return (match?.[1] || match?.[2] || '').trim()
}

test.describe('FE-CONFIG-IMPORT-MULTIPART-001 配置导入浏览器传输契约', () => {
  test('真实浏览器序列化 + stub 后端的 multipart 契约（非完整前后端 E2E）', async ({ page }) => {
    const batchNo = `E2E-CONFIG-${Date.now()}-${test.info().workerIndex}`
    const inputBytes = Buffer.from('not an xlsx or csv payload')
    const multipartRequests: MultipartRequest[] = []

    // 本次新增契约：监听真实浏览器序列化后的请求；后端响应由 stub 提供，不是完整前后端 E2E。
    page.on('request', async (request) => {
      const match = new URL(request.url()).pathname.match(/\/api\/v1\/config\/imports\/([^/]+)\/(upload|validate)$/)
      if (!match) return
      multipartRequests.push({
        method: request.method(),
        kind: match[2] as MultipartKind,
        taskNo: match[1],
        contentType: (await request.headerValue('content-type')) || '',
        body: request.postDataBuffer() || Buffer.alloc(0),
      })
    })

    // 本次新增契约：只 stub 正常 API envelope，不解析文件内容，让任意文本文件也能覆盖浏览器传输层。
    await page.route('**/api/v1/**', async (route) => {
      const request = route.request()
      const pathname = new URL(request.url()).pathname
      const normalizedPath = pathname.replace(/\/$/, '')

      if (request.method() === 'POST' && normalizedPath === '/api/v1/config/imports') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({
          taskNo,
          type: 'SALES_PROJECT_MAPPING',
          fileName: 'config-contract.txt',
          status: 'CREATED',
        })) })
        return
      }

      const taskPath = `/api/v1/config/imports/${taskNo}`
      if (request.method() === 'POST' && normalizedPath === `${taskPath}/upload`) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({
          taskNo,
          type: 'SALES_PROJECT_MAPPING',
          fileName: 'config-contract.txt',
          status: 'VALIDATING',
        })) })
        return
      }

      if (request.method() === 'POST' && normalizedPath === `${taskPath}/validate`) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({
          taskNo,
          status: 'VALID',
          rowCount: 0,
          headers: [],
          issues: [],
        })) })
        return
      }

      if (request.method() === 'GET' && normalizedPath === taskPath) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({
          taskNo,
          type: 'SALES_PROJECT_MAPPING',
          fileName: 'config-contract.txt',
          status: 'VALID',
        })) })
        return
      }

      if (request.method() === 'GET' && normalizedPath === `${taskPath}/issues`) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope([])) })
        return
      }

      if (request.method() === 'GET' && /\/api\/v1\/batches\/[^/]+$/.test(normalizedPath)) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({
          batchNo,
          period: '2026-08',
          perspective: 'BASE',
          status: 'DRAFT',
        })) })
        return
      }

      if (request.method() === 'GET' && normalizedPath.endsWith('/readiness')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({
          batchNo,
          perspective: 'BASE',
          readyForCalculation: false,
          datasets: [],
        })) })
        return
      }

      if (request.method() === 'GET' && normalizedPath.endsWith('/quality')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope({
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
        })) })
        return
      }

      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope([])) })
    })

    const createResponsePromise = page.waitForResponse((response) =>
      response.url().endsWith('/api/v1/config/imports') && response.request().method() === 'POST',
    )
    const uploadResponsePromise = page.waitForResponse((response) =>
      /\/api\/v1\/config\/imports\/[^/]+\/upload$/.test(new URL(response.url()).pathname) && response.request().method() === 'POST',
    )
    const validateResponsePromise = page.waitForResponse((response) =>
      /\/api\/v1\/config\/imports\/[^/]+\/validate$/.test(new URL(response.url()).pathname) && response.request().method() === 'POST',
    )

    await page.goto(`/?batch=${encodeURIComponent(batchNo)}`)
    await page.getByRole('button', { name: '基础配置导入' }).click()
    await expect(page.locator('h2').first()).toHaveText('基础配置导入')

    // 本次新增契约：通过真实文件选择器注入无业务意义的文本，不让测试依赖有效 Excel/CSV 内容。
    await page.locator('input[type="file"]').setInputFiles({
      name: 'config-contract.txt',
      mimeType: 'text/plain',
      buffer: inputBytes,
    })
    await page.getByRole('button', { name: '创建任务并导入' }).click()

    const createResponse = await createResponsePromise
    const createEnvelope = await createResponse.json()
    const createdTaskNo = createEnvelope.data.taskNo
    const [uploadResponse, validateResponse] = await Promise.all([uploadResponsePromise, validateResponsePromise])
    const uploadEnvelope = await uploadResponse.json()
    const validateEnvelope = await validateResponse.json()

    expect(createResponse.status()).toBe(200)
    expect(createEnvelope).toEqual(expect.objectContaining({ code: 'OK', message: '成功', data: expect.any(Object) }))
    expect(createdTaskNo).toBe(taskNo)
    expect(uploadResponse.status()).toBe(200)
    expect(validateResponse.status()).toBe(200)
    expect(uploadEnvelope).toEqual(expect.objectContaining({ code: 'OK', message: '成功', data: expect.any(Object) }))
    expect(validateEnvelope).toEqual(expect.objectContaining({ code: 'OK', message: '成功', data: expect.any(Object) }))
    expect(uploadResponse.url()).toContain(`/config/imports/${createdTaskNo}/upload`)
    expect(validateResponse.url()).toContain(`/config/imports/${createdTaskNo}/validate`)

    // 本次新增契约：页面必须只按 upload → validate 发出两个 multipart POST，且复用创建接口返回的同一任务号。
    expect(multipartRequests).toHaveLength(2)
    expect(multipartRequests.map((request) => request.method)).toEqual(['POST', 'POST'])
    expect(multipartRequests.map((request) => request.kind)).toEqual(['upload', 'validate'])
    expect(multipartRequests.map((request) => request.taskNo)).toEqual([createdTaskNo, createdTaskNo])

    for (const request of multipartRequests) {
      const boundary = multipartBoundary(request.contentType)
      const bodyText = request.body.toString('utf8')

      // 本次新增契约：动态解析 boundary，并锁定起始分隔线与结尾 --boundary--。
      expect(bodyText.startsWith(`--${boundary}\r\n`)).toBe(true)
      const bodyWithoutFinalLineBreak = bodyText.endsWith('\r\n') ? bodyText.slice(0, -2) : bodyText
      expect(bodyWithoutFinalLineBreak.endsWith(`--${boundary}--`)).toBe(true)

      // 本次新增契约：文件 part 只能出现一次，必须保留文件名并包含 setInputFiles 注入的原始字节。
      expect(bodyText.match(/name="file"/g) || []).toHaveLength(1)
      expect(bodyText).toMatch(/Content-Disposition: form-data;\s*name="file";\s*filename="config-contract\.txt"/)
      expect(request.body.includes(inputBytes)).toBe(true)
    }
  })
})
