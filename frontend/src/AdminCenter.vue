<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  api,
  type AdminMenu,
  type AdminRole,
  type AdminScopeGrant,
  type AdminUser,
} from './shared/api'

type AdminMode = 'users' | 'roles' | 'menus' | 'scopes'

const props = defineProps<{ mode: AdminMode }>()

const users = ref<AdminUser[]>([])
const roles = ref<AdminRole[]>([])
const menus = ref<AdminMenu[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')

const editingUserId = ref('')
const userForm = ref<AdminUser>(emptyUser())
const editingRoleCode = ref('')
const roleForm = ref<AdminRole>(emptyRole())
const editingMenuCode = ref('')
const menuForm = ref<AdminMenu>(emptyMenu())

const scopeUserId = ref('')
const scopeDrafts = ref<AdminScopeGrant[]>([])
const menuRoleCode = ref('')
const selectedMenuCodes = ref<string[]>([])

const title = computed(() => ({ users: '用户管理', roles: '角色管理', menus: '菜单管理', scopes: '数据权限管理' }[props.mode]))
const description = computed(() => ({
  users: '维护生产用户、启停状态及角色归属',
  roles: '维护角色编码、职责说明及启停状态',
  menus: '维护服务端菜单，并配置角色可见菜单',
  scopes: '按用户配置基地、产品线、事业部等数据范围',
}[props.mode]))

function emptyUser(): AdminUser {
  return { userId: '', employeeNo: '', username: '', displayName: '', email: '', roleCodes: [], scopes: [], enabled: true }
}

function emptyRole(): AdminRole {
  return { roleCode: '', roleName: '', description: '', menuCodes: [], enabled: true }
}

function emptyMenu(): AdminMenu {
  return { menuCode: '', menuName: '', parentCode: '', path: '', displayOrder: 0, enabled: true }
}

function readableError(reason: unknown) {
  return reason instanceof Error ? reason.message : '请求失败'
}

function replaceByKey<T>(list: T[], saved: T, key: keyof T) {
  const index = list.findIndex((item) => item[key] === saved[key])
  return index < 0 ? [...list, saved] : list.map((item, itemIndex) => itemIndex === index ? saved : item)
}

// 本次修改：每个管理页仅从对应真实接口加载；失败时清空旧数据，避免把过期结果当作当前权限。
async function load() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    if (props.mode === 'users' || props.mode === 'scopes') {
      const [nextUsers, nextRoles] = await Promise.all([api.adminUsers(), api.adminRoles()])
      users.value = nextUsers
      roles.value = nextRoles
      if (scopeUserId.value && !nextUsers.some((user) => user.userId === scopeUserId.value)) resetScopeEditor()
    } else if (props.mode === 'roles') {
      roles.value = await api.adminRoles()
    } else {
      const [nextMenus, nextRoles] = await Promise.all([api.adminMenus(), api.adminRoles()])
      menus.value = nextMenus
      roles.value = nextRoles
      if (menuRoleCode.value && !nextRoles.some((role) => role.roleCode === menuRoleCode.value)) resetRoleMenus()
    }
  } catch (reason) {
    if (props.mode === 'users' || props.mode === 'scopes') users.value = []
    if (props.mode === 'roles' || props.mode === 'users' || props.mode === 'scopes' || props.mode === 'menus') roles.value = []
    if (props.mode === 'menus') menus.value = []
    error.value = `加载失败：${readableError(reason)}`
  } finally {
    loading.value = false
  }
}

function editUser(user: AdminUser) {
  editingUserId.value = user.userId
  userForm.value = { ...user, roleCodes: [...(user.roleCodes || [])], scopes: (user.scopes || []).map(cloneScope) }
  message.value = ''
}

function resetUserForm() {
  editingUserId.value = ''
  userForm.value = emptyUser()
}

function validateUser() {
  if (!userForm.value.userId.trim()) return '用户ID不能为空。'
  if (!userForm.value.displayName.trim()) return '用户姓名不能为空。'
  if (!userForm.value.roleCodes.length) return '请至少选择一个角色。'
  if (userForm.value.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(userForm.value.email)) return '邮箱格式不正确。'
  return ''
}

async function saveUser() {
  const validation = validateUser()
  if (validation) { message.value = validation; return }
  saving.value = true
  message.value = ''
  try {
    const payload = { ...userForm.value, userId: userForm.value.userId.trim(), displayName: userForm.value.displayName.trim(), roleCodes: [...userForm.value.roleCodes] }
    const saved = editingUserId.value ? await api.updateAdminUser(payload) : await api.createAdminUser(payload)
    users.value = replaceByKey(users.value, saved, 'userId')
    message.value = `用户 ${saved.displayName} 已${editingUserId.value ? '更新' : '创建'}。`
    resetUserForm()
  } catch (reason) {
    message.value = `保存失败：${readableError(reason)}`
  } finally {
    saving.value = false
  }
}

async function toggleUser(user: AdminUser) {
  saving.value = true
  message.value = ''
  try {
    const saved = await api.updateAdminUser({ ...user, roleCodes: [...(user.roleCodes || [])], enabled: !user.enabled })
    users.value = replaceByKey(users.value, saved, 'userId')
    message.value = `用户 ${saved.displayName} 已${saved.enabled ? '启用' : '停用'}。`
  } catch (reason) {
    message.value = `状态更新失败：${readableError(reason)}`
  } finally {
    saving.value = false
  }
}

function editRole(role: AdminRole) {
  editingRoleCode.value = role.roleCode
  roleForm.value = { ...role, menuCodes: [...(role.menuCodes || [])] }
  message.value = ''
}

function resetRoleForm() {
  editingRoleCode.value = ''
  roleForm.value = emptyRole()
}

async function saveRole() {
  if (!roleForm.value.roleCode.trim() || !roleForm.value.roleName.trim()) { message.value = '角色编码和角色名称不能为空。'; return }
  saving.value = true
  message.value = ''
  try {
    const payload = { ...roleForm.value, roleCode: roleForm.value.roleCode.trim(), roleName: roleForm.value.roleName.trim(), menuCodes: [...(roleForm.value.menuCodes || [])] }
    const saved = editingRoleCode.value ? await api.updateAdminRole(payload) : await api.createAdminRole(payload)
    roles.value = replaceByKey(roles.value, saved, 'roleCode')
    message.value = `角色 ${saved.roleName} 已${editingRoleCode.value ? '更新' : '创建'}。`
    resetRoleForm()
  } catch (reason) {
    message.value = `保存失败：${readableError(reason)}`
  } finally {
    saving.value = false
  }
}

async function toggleRole(role: AdminRole) {
  saving.value = true
  message.value = ''
  try {
    const saved = await api.updateAdminRole({ ...role, menuCodes: [...(role.menuCodes || [])], enabled: !role.enabled })
    roles.value = replaceByKey(roles.value, saved, 'roleCode')
    message.value = `角色 ${saved.roleName} 已${saved.enabled ? '启用' : '停用'}。`
  } catch (reason) {
    message.value = `状态更新失败：${readableError(reason)}`
  } finally {
    saving.value = false
  }
}

function editMenu(menu: AdminMenu) {
  editingMenuCode.value = menu.menuCode
  menuForm.value = { ...menu }
  message.value = ''
}

function resetMenuForm() {
  editingMenuCode.value = ''
  menuForm.value = emptyMenu()
}

async function saveMenu() {
  if (!menuForm.value.menuCode.trim() || !menuForm.value.menuName.trim()) { message.value = '菜单编码和菜单名称不能为空。'; return }
  if (!Number.isInteger(Number(menuForm.value.displayOrder)) || Number(menuForm.value.displayOrder) < 0) { message.value = '菜单顺序必须是非负整数。'; return }
  saving.value = true
  message.value = ''
  try {
    const payload = { ...menuForm.value, menuCode: menuForm.value.menuCode.trim(), menuName: menuForm.value.menuName.trim(), displayOrder: Number(menuForm.value.displayOrder) }
    const saved = editingMenuCode.value ? await api.updateAdminMenu(payload) : await api.createAdminMenu(payload)
    menus.value = replaceByKey(menus.value, saved, 'menuCode').sort((a, b) => a.displayOrder - b.displayOrder)
    message.value = `菜单 ${saved.menuName} 已${editingMenuCode.value ? '更新' : '创建'}。`
    resetMenuForm()
  } catch (reason) {
    message.value = `保存失败：${readableError(reason)}`
  } finally {
    saving.value = false
  }
}

async function toggleMenu(menu: AdminMenu) {
  saving.value = true
  message.value = ''
  try {
    const saved = await api.updateAdminMenu({ ...menu, enabled: !menu.enabled })
    menus.value = replaceByKey(menus.value, saved, 'menuCode')
    message.value = `菜单 ${saved.menuName} 已${saved.enabled ? '启用' : '停用'}。`
  } catch (reason) {
    message.value = `状态更新失败：${readableError(reason)}`
  } finally {
    saving.value = false
  }
}

function cloneScope(scope: AdminScopeGrant): AdminScopeGrant {
  return { ...scope }
}

function editScopes(user: AdminUser) {
  scopeUserId.value = user.userId
  scopeDrafts.value = (user.scopeGrants || []).map(cloneScope)
  message.value = ''
}

function resetScopeEditor() {
  scopeUserId.value = ''
  scopeDrafts.value = []
}

function addScope() {
  const user = users.value.find((item) => item.userId === scopeUserId.value)
  const roleCode = user?.roleCodes.find((code) => code !== 'ADMIN') || ''
  if (!roleCode) { message.value = '当前用户没有可配置数据范围的业务角色；ADMIN 全量只读权限无需配置范围。'; return }
  scopeDrafts.value.push({ roleCode, perspective: 'BASE', scopeCode: '', canViewDetail: false, canExport: false, canEditConfig: false })
}

async function saveScopes() {
  if (!scopeUserId.value) { message.value = '请先选择用户。'; return }
  const target = users.value.find((user) => user.userId === scopeUserId.value)
  if (!target) { message.value = '用户不存在，请刷新后重试。'; return }
  if (scopeDrafts.value.some((scope) => !scope.roleCode || !scope.scopeCode.trim())) { message.value = '每条授权都必须选择角色并填写范围编码；全域请填写 ALL。'; return }
  const keys = scopeDrafts.value.map((scope) => `${scope.roleCode}|${scope.perspective}|${scope.scopeCode.trim().toUpperCase()}`)
  if (new Set(keys).size !== keys.length) { message.value = '同一角色、视角和范围编码不能重复授权。'; return }
  saving.value = true
  message.value = ''
  try {
    const scopes = scopeDrafts.value.map((scope) => ({ ...cloneScope(scope), scopeCode: scope.scopeCode.trim().toUpperCase() }))
    const saved = await api.updateAdminUserScopes(scopeUserId.value, [...target.roleCodes], scopes)
    users.value = replaceByKey(users.value, saved, 'userId')
    scopeDrafts.value = (saved.scopeGrants || scopes).map(cloneScope)
    message.value = `用户 ${saved.displayName} 的数据范围已保存。`
  } catch (reason) {
    message.value = `数据范围保存失败：${readableError(reason)}`
  } finally {
    saving.value = false
  }
}

function editRoleMenus(role: AdminRole) {
  menuRoleCode.value = role.roleCode
  selectedMenuCodes.value = [...(role.menuCodes || [])]
  message.value = ''
}

function selectRoleMenus() {
  const role = roles.value.find((item) => item.roleCode === menuRoleCode.value)
  if (role) editRoleMenus(role)
  else resetRoleMenus()
}

function resetRoleMenus() {
  menuRoleCode.value = ''
  selectedMenuCodes.value = []
}

async function saveRoleMenus() {
  if (!menuRoleCode.value) { message.value = '请先选择角色。'; return }
  saving.value = true
  message.value = ''
  try {
    const saved = await api.updateAdminRoleMenus(menuRoleCode.value, [...selectedMenuCodes.value])
    roles.value = replaceByKey(roles.value, saved, 'roleCode')
    selectedMenuCodes.value = [...(saved.menuCodes || selectedMenuCodes.value)]
    message.value = `角色 ${saved.roleName} 的菜单权限已保存。`
  } catch (reason) {
    message.value = `菜单权限保存失败：${readableError(reason)}`
  } finally {
    saving.value = false
  }
}

watch(() => props.mode, () => {
  error.value = ''
  message.value = ''
  void load()
})

onMounted(load)
</script>

<template>
  <section class="view active admin-center" :data-mode="mode">
    <div class="section-head">
      <div><h2>{{ title }}</h2><p>{{ description }}，所有记录均来自管理接口</p></div>
      <button class="btn" :disabled="loading || saving" @click="load">刷新</button>
    </div>

    <div v-if="error" class="admin-state error-state" role="alert">
      <span>{{ error }}</span><button class="btn" @click="load">重试</button>
    </div>
    <div v-if="message" class="alert" role="status">{{ message }}</div>
    <div v-if="loading" class="admin-state" aria-busy="true">正在加载{{ title }}…</div>

    <template v-else-if="!error && mode === 'users'">
      <div class="panel admin-form">
        <div class="panel-title">{{ editingUserId ? '编辑用户' : '新增用户' }} <small>带 * 为必填项</small></div>
        <div class="admin-grid">
          <label>用户ID *<input v-model.trim="userForm.userId" :disabled="Boolean(editingUserId)" placeholder="统一认证用户ID"></label>
          <label>用户姓名 *<input v-model.trim="userForm.displayName" placeholder="姓名"></label>
          <label>工号<input v-model.trim="userForm.employeeNo" placeholder="员工工号"></label>
          <label>登录名<input v-model.trim="userForm.username" placeholder="统一认证登录名"></label>
          <label>邮箱<input v-model.trim="userForm.email" placeholder="name@example.com"></label>
          <label>状态<select v-model="userForm.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></label>
        </div>
        <fieldset class="choice-box"><legend>角色 *</legend><label v-for="role in roles" :key="role.roleCode"><input v-model="userForm.roleCodes" type="checkbox" :value="role.roleCode" :disabled="!role.enabled">{{ role.roleName }}（{{ role.roleCode }}）</label><span v-if="!roles.length" class="muted">暂无可选角色，请先维护角色。</span></fieldset>
        <div class="action-row"><button v-if="editingUserId" class="btn" @click="resetUserForm">取消</button><button class="btn primary" :disabled="saving" @click="saveUser">{{ saving ? '保存中…' : '保存用户' }}</button></div>
      </div>
      <div class="panel table-scroll admin-list">
        <table v-if="users.length"><thead><tr><th>用户ID</th><th>姓名</th><th>工号</th><th>角色</th><th>数据范围</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="user in users" :key="user.userId"><td>{{ user.userId }}</td><td>{{ user.displayName }}</td><td>{{ user.employeeNo || '—' }}</td><td>{{ user.roleCodes?.join('、') || '—' }}</td><td>{{ user.scopes?.map(scope => `${scope.perspective}:${scope.scopeCodes.join('/')}`).join('；') || '未配置' }}</td><td><span :class="['status', user.enabled ? 'ok' : 'gray']">{{ user.enabled ? '启用' : '停用' }}</span></td><td><button class="link-button" @click="editUser(user)">编辑</button><button class="link-button" :disabled="saving" @click="toggleUser(user)">{{ user.enabled ? '停用' : '启用' }}</button></td></tr></tbody></table>
        <div v-else class="admin-state empty-state">暂无用户数据，可使用上方表单创建首个用户。</div>
      </div>
    </template>

    <template v-else-if="!error && mode === 'roles'">
      <div class="panel admin-form">
        <div class="panel-title">{{ editingRoleCode ? '编辑角色' : '新增角色' }}</div>
        <div class="admin-grid">
          <label>角色编码 *<input v-model.trim="roleForm.roleCode" :disabled="Boolean(editingRoleCode)" placeholder="例如 HQ_FINANCE"></label>
          <label>角色名称 *<input v-model.trim="roleForm.roleName" placeholder="角色名称"></label>
          <label class="wide">职责说明<input v-model.trim="roleForm.description" placeholder="角色职责与边界"></label>
          <label>状态<select v-model="roleForm.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></label>
        </div>
        <div class="action-row"><button v-if="editingRoleCode" class="btn" @click="resetRoleForm">取消</button><button class="btn primary" :disabled="saving" @click="saveRole">{{ saving ? '保存中…' : '保存角色' }}</button></div>
      </div>
      <div class="panel table-scroll admin-list"><table v-if="roles.length"><thead><tr><th>角色编码</th><th>角色名称</th><th>职责说明</th><th>菜单数</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="role in roles" :key="role.roleCode"><td>{{ role.roleCode }}</td><td>{{ role.roleName }}</td><td>{{ role.description || '—' }}</td><td>{{ role.menuCodes?.length || 0 }}</td><td><span :class="['status', role.enabled ? 'ok' : 'gray']">{{ role.enabled ? '启用' : '停用' }}</span></td><td><button class="link-button" @click="editRole(role)">编辑</button><button class="link-button" :disabled="saving" @click="toggleRole(role)">{{ role.enabled ? '停用' : '启用' }}</button></td></tr></tbody></table><div v-else class="admin-state empty-state">暂无角色数据，可使用上方表单创建首个角色。</div></div>
    </template>

    <template v-else-if="!error && mode === 'scopes'">
      <div class="panel table-scroll admin-list"><table v-if="users.length"><thead><tr><th>用户ID</th><th>姓名</th><th>角色</th><th>当前数据范围</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="user in users" :key="user.userId"><td>{{ user.userId }}</td><td>{{ user.displayName }}</td><td>{{ user.roleCodes?.join('、') || '—' }}</td><td>{{ user.scopes?.map(scope => `${scope.perspective}:${scope.scopeCodes.join('/')}`).join('；') || '未配置' }}</td><td><span :class="['status', user.enabled ? 'ok' : 'gray']">{{ user.enabled ? '启用' : '停用' }}</span></td><td><button class="link-button" @click="editScopes(user)">配置范围</button></td></tr></tbody></table><div v-else class="admin-state empty-state">暂无用户，需先在用户管理中创建生产用户。</div></div>
      <div v-if="scopeUserId" class="panel admin-form admin-list">
        <div class="panel-title">配置用户 {{ scopeUserId }} 的数据权限 <small>逐条授权角色、视角、范围与能力；全域范围编码使用 ALL</small></div>
        <div v-for="(item, index) in scopeDrafts" :key="index" class="scope-row">
          <select v-model="item.roleCode"><option v-for="roleCode in users.find(user => user.userId === scopeUserId)?.roleCodes.filter(code => code !== 'ADMIN')" :key="roleCode" :value="roleCode">{{ roleCode }}</option></select>
          <select v-model="item.perspective"><option value="BASE">基地</option><option value="PRODUCT_LINE">产品线</option><option value="BUSINESS_UNIT">事业部</option></select>
          <input v-model.trim="item.scopeCode" placeholder="例如 NC01 或 ALL">
          <label><input v-model="item.canViewDetail" type="checkbox">明细</label>
          <label><input v-model="item.canExport" type="checkbox">导出/PDF</label>
          <label><input v-model="item.canEditConfig" type="checkbox">配置维护</label>
          <button class="btn" @click="scopeDrafts.splice(index, 1)">移除</button>
        </div>
        <div v-if="!scopeDrafts.length" class="admin-state empty-state">尚未配置业务数据授权；可添加授权，或保存空列表清除现有授权。</div>
        <div class="action-row"><button class="btn" @click="addScope">添加范围</button><button class="btn" @click="resetScopeEditor">取消</button><button class="btn primary" :disabled="saving" @click="saveScopes">{{ saving ? '保存中…' : '保存数据范围' }}</button></div>
      </div>
    </template>

    <template v-else-if="!error && mode === 'menus'">
      <div class="panel admin-form">
        <div class="panel-title">{{ editingMenuCode ? '编辑菜单' : '新增菜单' }}</div>
        <div class="admin-grid">
          <label>菜单编码 *<input v-model.trim="menuForm.menuCode" :disabled="Boolean(editingMenuCode)" placeholder="菜单唯一编码"></label>
          <label>菜单名称 *<input v-model.trim="menuForm.menuName" placeholder="菜单名称"></label>
          <label>父菜单编码<input v-model.trim="menuForm.parentCode" placeholder="顶级菜单留空"></label>
          <label>前端路径<input v-model.trim="menuForm.path" placeholder="例如 /admin/users"></label>
          <label>显示顺序 *<input v-model.number="menuForm.displayOrder" type="number" min="0"></label>
          <label>状态<select v-model="menuForm.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></label>
        </div>
        <div class="action-row"><button v-if="editingMenuCode" class="btn" @click="resetMenuForm">取消</button><button class="btn primary" :disabled="saving" @click="saveMenu">{{ saving ? '保存中…' : '保存菜单' }}</button></div>
      </div>
      <div class="panel table-scroll admin-list"><table v-if="menus.length"><thead><tr><th>菜单编码</th><th>菜单名称</th><th>父菜单</th><th>路径</th><th>顺序</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="menu in menus" :key="menu.menuCode"><td>{{ menu.menuCode }}</td><td>{{ menu.menuName }}</td><td>{{ menu.parentCode || '—' }}</td><td>{{ menu.path || '—' }}</td><td>{{ menu.displayOrder }}</td><td><span :class="['status', menu.enabled ? 'ok' : 'gray']">{{ menu.enabled ? '启用' : '停用' }}</span></td><td><button class="link-button" @click="editMenu(menu)">编辑</button><button class="link-button" :disabled="saving" @click="toggleMenu(menu)">{{ menu.enabled ? '停用' : '启用' }}</button></td></tr></tbody></table><div v-else class="admin-state empty-state">暂无菜单数据，可使用上方表单创建首个菜单。</div></div>
      <div class="panel admin-form admin-list">
        <div class="panel-title">角色菜单授权 <small>勾选结果由角色菜单接口持久化</small></div>
        <select v-model="menuRoleCode" @change="selectRoleMenus"><option value="">请选择角色</option><option v-for="role in roles" :key="role.roleCode" :value="role.roleCode" :disabled="!role.enabled">{{ role.roleName }}（{{ role.roleCode }}）</option></select>
        <fieldset v-if="menuRoleCode" class="choice-box"><legend>可见菜单</legend><label v-for="menu in menus" :key="menu.menuCode"><input v-model="selectedMenuCodes" type="checkbox" :value="menu.menuCode">{{ menu.menuName }}（{{ menu.menuCode }}）<span v-if="!menu.enabled" class="muted"> · 已停用</span></label></fieldset>
        <div v-if="menuRoleCode" class="action-row"><button class="btn primary" :disabled="saving" @click="saveRoleMenus">{{ saving ? '保存中…' : '保存角色菜单' }}</button></div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.admin-state{display:flex;align-items:center;justify-content:center;gap:12px;min-height:92px;padding:18px;border:1px dashed #c8cfd6;border-radius:7px;background:#fafbfc;color:#65717c}.error-state{justify-content:space-between;border-color:#e0adb7;background:#fff7f8;color:#8d1730}.admin-form{margin-bottom:15px}.admin-list{margin-top:15px}.admin-grid{display:grid;grid-template-columns:repeat(3,minmax(180px,1fr));gap:12px}.admin-grid label{display:flex;flex-direction:column;gap:5px;color:#65717c;font-size:12px}.admin-grid label.wide{grid-column:span 2}.admin-grid input,.admin-grid select,.scope-row>input,.scope-row>select{width:100%;padding:8px;border:1px solid #d4dbe1;border-radius:4px;background:#fff;color:#26313b}.choice-box{display:flex;gap:10px 18px;flex-wrap:wrap;margin:14px 0 0;padding:10px 12px;border:1px solid #e1e5e9;border-radius:6px}.choice-box legend{padding:0 5px;color:#65717c;font-size:12px}.choice-box label,.scope-row label{display:flex;align-items:center;gap:5px}.scope-row label input{width:auto}.scope-row{display:grid;grid-template-columns:140px 140px minmax(160px,1fr) auto auto auto auto;align-items:center;gap:9px;margin:9px 0}.link-button+ .link-button{margin-left:10px}.link-button:disabled{opacity:.45;cursor:not-allowed}@media(max-width:1100px){.scope-row{grid-template-columns:1fr 1fr 1fr}.scope-row .btn{justify-self:start}}@media(max-width:900px){.admin-grid{grid-template-columns:1fr 1fr}.scope-row{grid-template-columns:1fr}.admin-grid label.wide{grid-column:span 1}}@media(max-width:640px){.admin-grid{grid-template-columns:1fr}}
</style>
