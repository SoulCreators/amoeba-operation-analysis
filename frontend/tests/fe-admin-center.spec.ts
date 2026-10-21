import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminCenter from '../src/AdminCenter.vue'

const apiMock = vi.hoisted(() => ({
  adminUsers: vi.fn(),
  createAdminUser: vi.fn(),
  updateAdminUser: vi.fn(),
  adminRoles: vi.fn(),
  createAdminRole: vi.fn(),
  updateAdminRole: vi.fn(),
  adminMenus: vi.fn(),
  createAdminMenu: vi.fn(),
  updateAdminMenu: vi.fn(),
  updateAdminUserScopes: vi.fn(),
  updateAdminRoleMenus: vi.fn(),
}))

vi.mock('../src/api', () => ({ api: apiMock }))

const role = { roleCode: 'HQ_FINANCE', roleName: '总部财经', menuCodes: ['OVERVIEW'], enabled: true }
const user = { userId: 'u-001', employeeNo: '1001', displayName: '张三', roleCodes: ['HQ_FINANCE'], scopes: [], enabled: true }
const menu = { menuCode: 'OVERVIEW', menuName: '经营总览', parentCode: '', path: '/overview', displayOrder: 1, enabled: true }

function resetMocks() {
  Object.values(apiMock).forEach((mock) => mock.mockReset())
  apiMock.adminUsers.mockResolvedValue([user])
  apiMock.adminRoles.mockResolvedValue([role])
  apiMock.adminMenus.mockResolvedValue([menu])
}

function findButton(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

// 本次修改：覆盖加载、空态、错误态、校验、启停和两类授权保存的页面逻辑。
describe('FE-ADMIN-CENTER-001 管理中心页面逻辑', () => {
  beforeEach(resetMocks)

  it('展示真实加载态，接口为空后展示空态', async () => {
    let resolveUsers!: (value: unknown[]) => void
    apiMock.adminUsers.mockReturnValueOnce(new Promise((resolve) => { resolveUsers = resolve }))
    apiMock.adminRoles.mockResolvedValueOnce([])
    const wrapper = mount(AdminCenter, { props: { mode: 'users' } })

    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('正在加载用户管理')
    resolveUsers([])
    await flushPromises()
    expect(wrapper.text()).toContain('暂无用户数据')
    expect(wrapper.text()).not.toContain('张三')
  })

  it('展示接口错误并允许重试', async () => {
    apiMock.adminUsers.mockRejectedValueOnce(new Error('服务不可用')).mockResolvedValueOnce([user])
    const wrapper = mount(AdminCenter, { props: { mode: 'users' } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('加载失败：服务不可用')
    await findButton(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('张三')
  })

  it('校验用户必填项并通过真实接口创建用户', async () => {
    apiMock.adminUsers.mockResolvedValueOnce([])
    const saved = { ...user, userId: 'u-002', displayName: '李四' }
    apiMock.createAdminUser.mockResolvedValueOnce(saved)
    const wrapper = mount(AdminCenter, { props: { mode: 'users' } })
    await flushPromises()

    await findButton(wrapper, '保存用户')!.trigger('click')
    expect(wrapper.text()).toContain('用户ID不能为空')
    expect(apiMock.createAdminUser).not.toHaveBeenCalled()

    await wrapper.get('input[placeholder="统一认证用户ID"]').setValue('u-002')
    await wrapper.get('input[placeholder="姓名"]').setValue('李四')
    await wrapper.get('fieldset input[type="checkbox"]').setValue(true)
    await findButton(wrapper, '保存用户')!.trigger('click')
    await flushPromises()

    expect(apiMock.createAdminUser).toHaveBeenCalledWith(expect.objectContaining({ userId: 'u-002', displayName: '李四', roleCodes: ['HQ_FINANCE'], enabled: true }))
    expect(wrapper.text()).toContain('用户 李四 已创建')
  })

  it('通过更新接口停用用户，不做前端假更新', async () => {
    apiMock.updateAdminUser.mockResolvedValueOnce({ ...user, enabled: false })
    const wrapper = mount(AdminCenter, { props: { mode: 'users' } })
    await flushPromises()

    const row = wrapper.findAll('tbody tr')[0]
    await row.findAll('button').find((button) => button.text() === '停用')!.trigger('click')
    await flushPromises()

    expect(apiMock.updateAdminUser).toHaveBeenCalledWith(expect.objectContaining({ userId: 'u-001', enabled: false }))
    expect(row.text()).toContain('停用')
  })

  it('校验并保存用户数据范围', async () => {
    const saved = { ...user, scopes: [{ perspective: 'BASE', scopeCodes: ['NC01', 'YC01'] }] }
    apiMock.updateAdminUserScopes.mockResolvedValueOnce(saved)
    const wrapper = mount(AdminCenter, { props: { mode: 'scopes' } })
    await flushPromises()

    await findButton(wrapper, '配置范围')!.trigger('click')
    await wrapper.get('.scope-row input').setValue('NC01，YC01 NC01')
    await findButton(wrapper, '保存数据范围')!.trigger('click')
    await flushPromises()

    expect(apiMock.updateAdminUserScopes).toHaveBeenCalledWith('u-001', [{ perspective: 'BASE', scopeCodes: ['NC01', 'YC01'] }])
    expect(wrapper.text()).toContain('数据范围已保存')
  })

  it('从真实菜单与角色数据保存角色菜单授权', async () => {
    apiMock.updateAdminRoleMenus.mockResolvedValueOnce({ ...role, menuCodes: [] })
    const wrapper = mount(AdminCenter, { props: { mode: 'menus' } })
    await flushPromises()

    const roleSelect = wrapper.findAll('select').find((select) => select.find('option[value="HQ_FINANCE"]').exists())!
    await roleSelect.setValue('HQ_FINANCE')
    const menuCheckbox = wrapper.find('.choice-box input[type="checkbox"]')
    await menuCheckbox.setValue(false)
    await findButton(wrapper, '保存角色菜单')!.trigger('click')
    await flushPromises()

    expect(apiMock.updateAdminRoleMenus).toHaveBeenCalledWith('HQ_FINANCE', [])
    expect(wrapper.text()).toContain('菜单权限已保存')
  })
})
