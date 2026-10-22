import { beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  interceptors: { request: { use: vi.fn() } },
}))

vi.mock('axios', () => ({ default: { create: vi.fn(() => httpMock) } }))

import { api, type AdminMenu, type AdminRole, type AdminUser } from '../src/api'

function success<T>(data: T) {
  return { data: { code: '0', message: 'ok', data } }
}

const user: AdminUser = { userId: 'u-001', displayName: '张三', roleCodes: ['HQ_FINANCE'], enabled: true }
const role: AdminRole = { roleCode: 'HQ_FINANCE', roleName: '总部财经', menuCodes: ['OVERVIEW'], enabled: true }
const menu: AdminMenu = { menuCode: 'OVERVIEW', menuName: '经营总览', displayOrder: 1, enabled: true }

// 本次修改：锁定管理中心全部真实接口的 HTTP 方法、路径和请求体，不允许退回静态数据。
describe('FE-ADMIN-API-001 管理中心接口合同', () => {
  beforeEach(() => {
    httpMock.get.mockReset()
    httpMock.post.mockReset()
    httpMock.put.mockReset()
  })

  it('加载用户、角色、菜单并兼容数组和常见分页容器', async () => {
    httpMock.get
      .mockResolvedValueOnce(success([user]))
      .mockResolvedValueOnce(success({ items: [role] }))
      .mockResolvedValueOnce(success({ content: [menu] }))

    await expect(api.adminUsers()).resolves.toEqual([user])
    await expect(api.adminRoles()).resolves.toEqual([role])
    await expect(api.adminMenus()).resolves.toEqual([menu])
    expect(httpMock.get.mock.calls).toEqual([[ '/admin/users' ], [ '/admin/roles' ], [ '/admin/menus' ]])
  })

  it('通过集合地址新增和更新用户、角色、菜单', async () => {
    httpMock.post.mockResolvedValueOnce(success(user)).mockResolvedValueOnce(success(role)).mockResolvedValueOnce(success(menu))
    httpMock.put.mockResolvedValueOnce(success(user)).mockResolvedValueOnce(success(role)).mockResolvedValueOnce(success(menu))

    await api.createAdminUser(user)
    await api.updateAdminUser(user)
    await api.createAdminRole(role)
    await api.updateAdminRole(role)
    await api.createAdminMenu(menu)
    await api.updateAdminMenu(menu)

    expect(httpMock.post.mock.calls).toEqual([
      ['/admin/users', user],
      ['/admin/roles', role],
      ['/admin/menus', menu],
    ])
    expect(httpMock.put.mock.calls).toEqual([
      ['/admin/users', user],
      ['/admin/roles', role],
      ['/admin/menus', menu],
    ])
  })

  it('保存用户数据范围和角色菜单，并安全编码路径主键', async () => {
    const scopes = [{ roleCode: 'HQ_FINANCE', perspective: 'BASE' as const, scopeCode: 'NC01', canViewDetail: true, canExport: true, canEditConfig: false }]
    httpMock.put.mockResolvedValueOnce(success({ ...user, scopes })).mockResolvedValueOnce(success(role))

    await api.updateAdminUserScopes('user/a', ['HQ_FINANCE'], scopes)
    await api.updateAdminRoleMenus('HQ FINANCE', ['OVERVIEW'])

    expect(httpMock.put).toHaveBeenNthCalledWith(1, '/admin/users/user%2Fa/scopes', { roleCodes: ['HQ_FINANCE'], scopes })
    expect(httpMock.put).toHaveBeenNthCalledWith(2, '/admin/roles/HQ%20FINANCE/menus', { menuCodes: ['OVERVIEW'] })
  })

  it('透传管理接口的业务错误', async () => {
    httpMock.get.mockResolvedValueOnce({ data: { code: 'ADMIN_FORBIDDEN', message: '无管理权限', data: null } })
    await expect(api.adminUsers()).rejects.toThrow('无管理权限')
  })
})
