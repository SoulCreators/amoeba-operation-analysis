<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type AuditEvent } from './shared/api'

const rows = ref<AuditEvent[]>([])
const loading = ref(false)
const error = ref('')
const filters = ref({ operator: '', action: '', objectType: '', objectId: '', result: '' })

function readableError(reason: unknown) {
  return reason instanceof Error ? reason.message : '请求失败'
}

// 本次修改：审计页面只展示服务端真实只追加事件，不再使用占位记录或前端演示日志。
async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await api.auditLogs(filters.value)
  } catch (reason) {
    rows.value = []
    error.value = `加载失败：${readableError(reason)}`
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.value = { operator: '', action: '', objectType: '', objectId: '', result: '' }
  void load()
}

onMounted(load)
</script>

<template>
  <section class="view active audit-center">
    <div class="section-head">
      <div><h2>操作记录</h2><p>查询服务端只追加审计事件，页面不提供修改或删除入口</p></div>
      <span class="tag">真实审计接口</span>
    </div>
    <div class="panel audit-filter">
      <input v-model.trim="filters.operator" placeholder="操作人工号">
      <input v-model.trim="filters.action" placeholder="动作">
      <input v-model.trim="filters.objectType" placeholder="对象类型">
      <input v-model.trim="filters.objectId" placeholder="对象编码">
      <select v-model="filters.result"><option value="">全部结果</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option></select>
      <button class="btn primary" :disabled="loading" @click="load">查询</button>
      <button class="btn" :disabled="loading" @click="reset">重置</button>
    </div>
    <div v-if="error" class="admin-state error-state" role="alert"><span>{{ error }}</span><button class="btn" @click="load">重试</button></div>
    <div v-else-if="loading" class="admin-state" aria-busy="true">正在加载审计记录…</div>
    <div v-else class="panel table-scroll audit-list">
      <table v-if="rows.length"><thead><tr><th>时间</th><th>操作人</th><th>服务/动作</th><th>对象</th><th>状态变化</th><th>结果</th><th>追踪号</th></tr></thead><tbody><tr v-for="row in rows" :key="row.eventId"><td>{{ row.occurredAt }}</td><td>{{ row.operator }}</td><td>{{ row.service }} / {{ row.action }}</td><td>{{ row.objectType }} / {{ row.objectId }}</td><td>{{ row.fromState || '—' }} → {{ row.toState || '—' }}</td><td><span :class="['status', row.result === 'SUCCESS' ? 'ok' : 'warn']">{{ row.result }}</span><small v-if="row.reason" class="audit-reason">{{ row.reason }}</small></td><td>{{ row.traceId || '—' }}</td></tr></tbody></table>
      <div v-else class="admin-state empty-state">当前筛选条件下没有审计记录。</div>
    </div>
  </section>
</template>

<style scoped>
.audit-filter{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:14px}.audit-filter input,.audit-filter select{min-width:140px;padding:8px;border:1px solid #d4dbe1;border-radius:4px}.audit-list table{min-width:980px}.admin-state{padding:24px;text-align:center;border:1px dashed #c8cfd6;border-radius:6px;background:#fafbfc}.error-state{display:flex;justify-content:space-between;align-items:center;color:#9e1835}.audit-reason{display:block;margin-top:4px;color:#75808b;max-width:220px;white-space:normal}
</style>
