import { expect, test } from '@playwright/test'

function freshEmptyBatch() {
  return `E2E-SMOKE-${Date.now()}-${test.info().workerIndex}`
}

test.describe('frontend browser smoke', () => {
  test('loads local real-data mode and keeps an empty batch blocked without fake amounts', async ({ page }) => {
    const batchNo = freshEmptyBatch()

    await page.goto(`/?batch=${encodeURIComponent(batchNo)}`)

    await expect(page.locator('.top-title')).toHaveText(/阿米巴经营分析平台 · 真实数据模式/)
    await expect(page.locator('.side-foot')).toContainText('无真实结果时不展示指标')
    await expect(page.locator('h2').first()).toHaveText('月度工作台')
    await expect(page.locator('.readiness')).toContainText(/批次完整性：/)
    await expect(page.locator('.readiness')).toContainText(/暂不可计算|已满足计算条件/)

    // The unique batch has no imported facts. Every dashboard card must remain
    // the explicit empty marker instead of a demo or fallback amount.
    const cardValues = await page.locator('.cards .metric').allTextContents()
    expect(cardValues).toHaveLength(8)
    expect(cardValues.map((value) => value.trim())).toEqual(Array(8).fill('—'))
    await expect(page.locator('.placeholder-card').filter({ hasText: /暂无真实|没有真实/ }).first()).toBeVisible()
  })

  test('isolates an unavailable API and clears the dashboard without fallback data', async ({ page }) => {
    await page.route('**/api/v1/**', (route) => route.abort('failed'))
    await page.goto('/?batch=E2E-API-DOWN')

    await expect(page.locator('.top-title')).toHaveText(/真实数据模式/)
    await expect(
      page.locator('.alert').filter({ hasText: /真实数据接口暂不可用|V1\.3真实接口暂不可用/ }).first(),
    ).toBeVisible()

    const cardValues = await page.locator('.cards .metric').allTextContents()
    expect(cardValues.map((value) => value.trim())).toEqual(Array(8).fill('—'))
    await expect(page.locator('.cards')).not.toContainText(/演示|示例数据/)
  })
})
