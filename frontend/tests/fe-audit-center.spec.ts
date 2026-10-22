import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({ auditLogs: vi.fn() }))
vi.mock('../src/api', () => ({ api: apiMock }))

import AuditCenter from '../src/AuditCenter.vue'

describe('FE-AUDIT-CENTER-001 审计中心真实数据状态', () => {
  beforeEach(() => apiMock.auditLogs.mockReset())

  it('展示服务端返回的真实审计事件', async () => {
    apiMock.auditLogs.mockResolvedValue([{ eventId: 'E-1', operator: 'admin', service: 'iam', action: 'UPDATE_USER', objectType: 'USER', objectId: 'u1', result: 'SUCCESS', occurredAt: '2026-10-21T17:00:00' }])
    const wrapper = mount(AuditCenter)
    await flushPromises()
    expect(apiMock.auditLogs).toHaveBeenCalled()
    expect(wrapper.text()).toContain('UPDATE_USER')
    expect(wrapper.text()).toContain('u1')
    expect(wrapper.text()).not.toContain('演示日志')
  })

  it('空结果显示真实空态', async () => {
    apiMock.auditLogs.mockResolvedValue([])
    const wrapper = mount(AuditCenter)
    await flushPromises()
    expect(wrapper.text()).toContain('没有审计记录')
    expect(wrapper.text()).not.toContain('演示日志')
  })
})
