import { expect, test } from '@playwright/test'
import path from 'node:path'

function freshBatch() {
  return `E2E-FILE-${Date.now()}-${test.info().workerIndex}`
}

test.describe('formal XLSX import feedback', () => {
  test('selects the repository template and exposes the real local API validation result', async ({ page }) => {
    const batchNo = freshBatch()
    const templatePath = path.resolve(
      __dirname,
      '../../templates/import/20260921_最终模板/01-基地实际收入成本明细.xlsx',
    )
    const createImportResponsePromise = page.waitForResponse((response) =>
      response.url().includes(`/api/v1/batches/${batchNo}/imports`) && response.request().method() === 'POST',
    )
    const validateResponsePromise = page.waitForResponse((response) =>
      response.url().includes('/api/v1/imports/') && response.url().endsWith('/validate') && response.request().method() === 'POST',
    )

    await page.goto(`/?batch=${encodeURIComponent(batchNo)}`)
    await page.getByRole('button', { name: '月度数据导入' }).click()
    await expect(page.locator('h2').first()).toHaveText('月度数据导入')

    await page.locator('select').filter({ has: page.locator('option[value="BASE_ACT_INC_COST"]') }).selectOption('BASE_ACT_INC_COST')
    await page.locator('#fileInput').setInputFiles(templatePath)
    await expect(page.locator('.file-list')).toContainText('01-基地实际收入成本明细.xlsx')

    await page.getByRole('button', { name: '提交校验' }).click()
    const createImportResponse = await createImportResponsePromise
    const createEnvelope = await createImportResponse.json()
    const validateResponse = await validateResponsePromise
    const validateEnvelope = await validateResponse.json()

    expect(createImportResponse.status()).toBe(200)
    expect(createEnvelope.code).toBe('OK')
    expect(createEnvelope.message).toBe('成功')
    expect(validateResponse.status()).toBe(200)
    expect(validateEnvelope.code).toBe('OK')
    expect(validateEnvelope.message).toBe('成功')
    expect(validateEnvelope.data.status).toBe('INVALID')
    expect(validateEnvelope.data.rowCount).toBe(0)
    expect(validateEnvelope.data.issues).toEqual([
      expect.objectContaining({
        fieldName: '财经修正项目',
        issueCode: 'FIELD_MISSING',
        issueMessage: '缺少必填字段: 财经修正项目',
      }),
    ])

    await expect(page.locator('.alert')).toContainText('校验完成：01-基地实际收入成本明细.xlsx')
    await expect(page.locator('.dataset-card').filter({ hasText: '基地实际收入成本明细' })).toContainText('INVALID')
  })
})
