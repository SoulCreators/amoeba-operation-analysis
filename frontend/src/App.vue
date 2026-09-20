<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type AiDraft, type BatchReadiness, type MetricResult, type Perspective, type PnlResult, type PvmResult } from './api'

type View = 'overview' | 'result' | 'pvm' | 'tree' | 'ai'
const view = ref<View>('overview')
const period = ref('2026-08')
const batchNo = ref('BATCH-202608-BASE-001')
const perspective = ref<Perspective>('BASE')
const scope = ref('ALL')
const currentUser = ref(localStorage.getItem('amoeba.user') || 'demo-finance')
const source = ref<'api' | 'demo'>('demo')
const loading = ref(false)
const error = ref('')
const metrics = ref<MetricResult[]>([])
const results = ref<PnlResult[]>([])
const pvmRows = ref<PvmResult[]>([])
const treeRows = ref<MetricResult[]>([])
const aiDraft = ref<AiDraft | null>(null)
const readiness = ref<BatchReadiness | null>(null)

const demoMetrics: MetricResult[] = [
  { code: 'REVENUE', name: '营业收入', actual: 128600, budget: 124800, gap: 3800, aiSummary: '收入较预算提升，主要由义乌与泰国基地贡献。' },
  { code: 'GROSS_PROFIT', name: '毛利', actual: 23400, budget: 25100, gap: -1700, aiSummary: '毛利低于预算，建议下钻材料成本和单位成本。' },
  { code: 'NET_PROFIT', name: '营业利润', actual: 8600, budget: 9200, gap: -600, aiSummary: '营业利润完成率93.5%，费用侧仍有改善空间。' },
  { code: 'PROFIT_RATE', name: '净利率', actual: 0.0669, budget: 0.0737, gap: -0.0068 },
  { code: 'VOLUME', name: '出货电量', actual: 3120, budget: 3030, gap: 90 },
  { code: 'BASE_EXPENSE', name: '基地费用合计', actual: 9800, budget: 9100, gap: 700 },
]
const demoResults: PnlResult[] = [
  { scopeCode: '全域', lines: { VOLUME: 3120, REVENUE: 128600, SALES_COST: 105200, GROSS_PROFIT: 23400, BASE_EXPENSE: 9800, NET_PROFIT: 8600, PROFIT_RATE: 0.0669, GAP_REVENUE: 3800, GAP_NET_PROFIT: -600 } },
  { scopeCode: '义乌基地', lines: { VOLUME: 1020, REVENUE: 44600, SALES_COST: 35800, GROSS_PROFIT: 8800, BASE_EXPENSE: 3200, NET_PROFIT: 4100, PROFIT_RATE: 0.0919, GAP_REVENUE: 1900, GAP_NET_PROFIT: 230 } },
  { scopeCode: '泰国基地', lines: { VOLUME: 760, REVENUE: 31800, SALES_COST: 27200, GROSS_PROFIT: 4600, BASE_EXPENSE: 2600, NET_PROFIT: 1200, PROFIT_RATE: 0.0377, GAP_REVENUE: -420, GAP_NET_PROFIT: -510 } },
]
const demoPvm: PvmResult[] = [
  { scopeCode: '义乌基地', perspective: 'BASE', actualVolume: 1020, budgetVolume: 980, volumeGap: 40, actualRevenue: 44600, budgetRevenue: 42700, revenueGap: 1900, actualSalesCost: 35800, budgetSalesCost: 34200, costGap: 1600, actualGrossProfit: 8800, budgetGrossProfit: 8500, grossProfitGap: 300 },
  { scopeCode: '泰国基地', perspective: 'BASE', actualVolume: 760, budgetVolume: 800, volumeGap: -40, actualRevenue: 31800, budgetRevenue: 32220, revenueGap: -420, actualSalesCost: 27200, budgetSalesCost: 26400, costGap: 800, actualGrossProfit: 4600, budgetGrossProfit: 5820, grossProfitGap: -1220 },
]
const demoAi: AiDraft = { batchNo: batchNo.value, status: 'DRAFT', sections: { 总述: '本期收入完成预算，毛利和利润承压。', 未达标: '泰国基地毛利低于预算，成本GAP为正。', 亮点: '义乌基地量、价均实现正向贡献。', 原因线索: '建议优先核查材料成本、固定制造费用及汇率。', 风险: '海外基地汇率和费用分摊口径需持续监控。', 行动建议: '由基地财务补充异常说明，集团财务确认后发布。' } }

const visiblePnl = computed(() => scope.value === 'ALL' ? results.value : results.value.filter((row) => row.scopeCode === scope.value))
const perspectiveName = computed(() => ({ BASE: '基地视角', PRODUCT_LINE: '产品线视角', BUSINESS_UNIT: '事业部视角' }[perspective.value]))
const title = computed(() => ({ overview: '经营总览', result: '损益计算结果', pvm: 'PVM因素分析', tree: '指标树', ai: 'AI经营分析' }[view.value]))

function setUser() { localStorage.setItem('amoeba.user', currentUser.value.trim() || 'demo-finance'); currentUser.value = localStorage.getItem('amoeba.user') || 'demo-finance' }
function loadDemo() { source.value = 'demo'; metrics.value = demoMetrics; results.value = demoResults; pvmRows.value = demoPvm; treeRows.value = demoMetrics; aiDraft.value = { ...demoAi, batchNo: batchNo.value }; readiness.value = { batchNo: batchNo.value, perspective: perspective.value, readyForCalculation: true, datasets: [{ datasetCode: 'INC_COST', datasetName: '收入成本明细', required: true, present: true, status: 'VALID', issueCount: 0 }, { datasetCode: 'BUDGET', datasetName: '年度预算收入成本', required: true, present: true, status: 'VALID', issueCount: 0 }] } }
async function load() {
  loading.value = true; error.value = ''
  try {
    metrics.value = await api.overview(period.value, perspective.value, scope.value); readiness.value = await api.readiness(batchNo.value); source.value = 'api'
    if (view.value === 'result') results.value = (await api.result(batchNo.value)).pnls
    if (view.value === 'pvm') pvmRows.value = await api.pvm(batchNo.value)
    if (view.value === 'tree') treeRows.value = await api.metricTree(batchNo.value)
    if (view.value === 'ai') aiDraft.value = await api.ai(batchNo.value)
  } catch (e) { loadDemo(); error.value = `后端暂不可用，已切换脱敏演示数据：${e instanceof Error ? e.message : '请求失败'}` }
  finally { loading.value = false }
}
async function generateAi() { try { aiDraft.value = await api.generateAi(batchNo.value); source.value = 'api' } catch { loadDemo(); error.value = 'AI服务暂不可用，当前展示规则引擎生成的演示草稿。' } }
function format(code: string, value: number) { if (code.includes('RATE')) return `${(value * 100).toFixed(2)}%`; return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value) }
onMounted(loadDemo)
</script>

<template>
  <main class="app-shell">
    <header class="topbar"><div><strong>数智财经</strong><span class="brand-sub">FINANCE INTELLIGENCE</span></div><h1>阿米巴经营分析平台</h1><div class="user-box"><input v-model="currentUser" @keyup.enter="setUser" /><button @click="setUser">切换用户</button></div></header>
    <div class="layout">
      <aside class="sidebar"><div class="side-title">平台工作台</div><button v-for="item in ([['overview','经营总览'],['result','损益结果'],['pvm','PVM因素分析'],['tree','指标树'],['ai','AI经营分析']] as [View,string][])" :key="item[0]" :class="['nav-item', { active: view === item[0] }]" @click="view = item[0]; load()">{{ item[1] }}</button><div class="side-note">当前用户<br /><b>{{ currentUser }}</b><br /><span>{{ source === 'api' ? '已连接后端' : '脱敏演示模式' }}</span></div></aside>
      <section class="content"><div class="toolbar"><label>期间<select v-model="period"><option>2026-08</option><option>2026-07</option><option>2026-06</option></select></label><label>视角<select v-model="perspective"><option value="BASE">基地视角</option><option value="PRODUCT_LINE">产品线视角</option><option value="BUSINESS_UNIT">事业部视角</option></select></label><label>批次<input v-model="batchNo" /></label><label>范围<select v-model="scope"><option>ALL</option><option>义乌基地</option><option>泰国基地</option></select></label><button class="primary" :disabled="loading" @click="load">{{ loading ? '加载中…' : '刷新分析' }}</button></div>
        <div v-if="error" class="notice">{{ error }}</div><div class="readiness" :class="readiness?.readyForCalculation ? 'ready' : 'blocked'"><b>批次完整性：</b>{{ readiness?.readyForCalculation ? '已满足计算条件' : '暂不可计算' }}<span v-if="readiness">{{ readiness.datasets.filter(d => d.required && d.present && d.status === 'VALID').length }}/{{ readiness.datasets.filter(d => d.required).length }} 个必需数据集已通过</span></div><div class="breadcrumb">经营分析平台 / {{ title }} · {{ perspectiveName }}</div><h2>{{ title }}</h2>
        <template v-if="view === 'overview'"><div class="cards"><article v-for="m in metrics.slice(0,4)" :key="m.code" class="card"><span>{{ m.name }}</span><strong>{{ format(m.code, m.actual) }}</strong><small :class="m.gap >= 0 ? 'positive' : 'negative'">{{ m.gap >= 0 ? '▲' : '▼' }} {{ format(m.code, Math.abs(m.gap)) }} VS预算</small></article></div><section class="panel"><div class="panel-head"><h3>统一经营指标</h3><span>指标库可配置 · 结果按权限隔离</span></div><table><thead><tr><th>指标</th><th>实际</th><th>预算</th><th>差额</th><th>完成率</th><th>AI简析</th></tr></thead><tbody><tr v-for="m in metrics" :key="m.code"><td>{{ m.name }}</td><td>{{ format(m.code,m.actual) }}</td><td>{{ format(m.code,m.budget) }}</td><td :class="m.gap >= 0 ? 'positive' : 'negative'">{{ format(m.code,m.gap) }}</td><td>{{ m.budget ? ((m.actual/m.budget)*100).toFixed(1) : '—' }}%</td><td>{{ m.aiSummary || '—' }}</td></tr></tbody></table></section></template>
        <template v-else-if="view === 'result'"><section class="panel"><div class="panel-head"><h3>财经损益表 · {{ perspectiveName }}</h3><span>数据来源：已确认/发布结果</span></div><table><thead><tr><th>分析对象</th><th>营业收入</th><th>销售成本</th><th>毛利</th><th>基地费用</th><th>营业利润</th><th>净利率</th></tr></thead><tbody><tr v-for="row in visiblePnl" :key="row.scopeCode"><td>{{ row.scopeCode }}</td><td>{{ format('REVENUE',row.lines.REVENUE || 0) }}</td><td>{{ format('SALES_COST',row.lines.SALES_COST || 0) }}</td><td>{{ format('GROSS_PROFIT',row.lines.GROSS_PROFIT || 0) }}</td><td>{{ format('BASE_EXPENSE',row.lines.BASE_EXPENSE || 0) }}</td><td>{{ format('NET_PROFIT',row.lines.NET_PROFIT || 0) }}</td><td>{{ format('PROFIT_RATE',row.lines.PROFIT_RATE || 0) }}</td></tr></tbody></table></section></template>
        <template v-else-if="view === 'pvm'"><section class="panel"><div class="panel-head"><h3>PVM · {{ perspectiveName }}</h3><span>基地、产品线/事业部采用独立逻辑</span></div><table><thead><tr><th>对象</th><th>电量实际/预算</th><th>电量GAP</th><th>收入实际/预算</th><th>收入GAP</th><th>成本GAP</th><th>毛利GAP</th></tr></thead><tbody><tr v-for="row in pvmRows" :key="row.scopeCode"><td>{{ row.scopeCode }}</td><td>{{ row.actualVolume }} / {{ row.budgetVolume }}</td><td :class="row.volumeGap >= 0 ? 'positive' : 'negative'">{{ row.volumeGap }}</td><td>{{ format('REVENUE',row.actualRevenue) }} / {{ format('REVENUE',row.budgetRevenue) }}</td><td :class="row.revenueGap >= 0 ? 'positive' : 'negative'">{{ format('REVENUE',row.revenueGap) }}</td><td>{{ format('SALES_COST',row.costGap) }}</td><td :class="row.grossProfitGap >= 0 ? 'positive' : 'negative'">{{ format('GROSS_PROFIT',row.grossProfitGap) }}</td></tr></tbody></table></section></template>
        <template v-else-if="view === 'tree'"><section class="panel tree"><div class="panel-head"><h3>指标树 · {{ perspectiveName }}</h3><span>按用户范围过滤</span></div><div v-for="m in treeRows" :key="m.code" class="tree-row"><div><b>{{ m.name }}</b><small>{{ m.code }}</small></div><div class="bar"><i :style="{ width: `${Math.min(100, Math.abs(m.budget) ? Math.abs(m.actual/m.budget)*100 : 0)}%` }"></i></div><strong :class="m.gap >= 0 ? 'positive' : 'negative'">{{ format(m.code,m.gap) }}</strong><button @click="error = `${m.name}：${m.aiSummary || '当前指标暂无AI简析'}`">AI简析</button></div></section></template>
        <template v-else><section class="panel ai-panel"><div class="panel-head"><h3>AI经营分析草稿</h3><span>需财经点击生成，集团财务确认后分发</span><button class="primary" @click="generateAi">生成/刷新草稿</button></div><div v-if="aiDraft" class="ai-grid"><article v-for="(text, name) in aiDraft.sections" :key="name"><h4>{{ name }}</h4><p>{{ text }}</p></article></div><div v-else class="empty">暂无草稿，请点击生成。</div></section></template>
      </section>
    </div>
  </main>
</template>

<style>
:root { font-family: Inter, Arial, "Microsoft YaHei", sans-serif; color: #263241; background: #f2f4f7; }
* { box-sizing: border-box; } body { margin: 0; } button, input, select { font: inherit; }
.app-shell { min-height: 100vh; } .topbar { height: 70px; display: flex; align-items: center; gap: 30px; padding: 0 28px; color: #fff; background: linear-gradient(100deg,#8e1534,#b21e3f); box-shadow: 0 2px 10px #8e153433; } .topbar strong { display:block; font-size: 20px; letter-spacing: 2px; } .brand-sub { font-size: 9px; opacity:.7; letter-spacing: 1.8px; } .topbar h1 { font-size: 18px; font-weight: 500; flex: 1; } .user-box { display:flex; gap:8px; } .user-box input { width:110px; border:1px solid #ffffff66; background:#ffffff18; color:#fff; padding:7px 9px; border-radius:5px; } .user-box button { border:0; border-radius:5px; padding:7px 10px; color:#8e1534; background:#fff; cursor:pointer; }
.layout { display:flex; min-height: calc(100vh - 70px); } .sidebar { width:220px; flex:0 0 220px; padding:24px 16px; background:#252b34; color:#d9dde4; } .side-title { margin: 0 10px 14px; color:#9ca5b2; font-size:13px; letter-spacing:1px; } .nav-item { width:100%; margin:4px 0; padding:12px 14px; border:0; border-radius:6px; color:#d8dce2; background:transparent; text-align:left; cursor:pointer; } .nav-item:hover,.nav-item.active { color:#fff; background:#a7193d; } .side-note { margin:34px 10px; padding-top:18px; border-top:1px solid #ffffff1c; color:#9ca5b2; line-height:1.8; font-size:12px; } .side-note b { color:#fff; } .side-note span { color:#d89aab; }
.content { flex:1; min-width:0; padding:28px 34px 50px; } .toolbar { display:flex; align-items:end; flex-wrap:wrap; gap:14px; padding:16px 18px; background:#fff; border:1px solid #e2e6ea; border-radius:8px; box-shadow:0 3px 12px #27364b0a; } label { display:flex; flex-direction:column; gap:6px; color:#768292; font-size:12px; } select,input { min-width:130px; padding:8px 10px; border:1px solid #cfd6df; border-radius:5px; color:#273242; background:#fff; } .toolbar input { width:190px; } button.primary { padding:9px 16px; border:0; border-radius:5px; color:#fff; background:#a7193d; cursor:pointer; } button.primary:disabled { opacity:.55; cursor:wait; } .breadcrumb { margin:22px 0 8px; color:#8b96a4; font-size:13px; } h2 { margin:0 0 20px; font-size:27px; } .notice { margin-top:15px; padding:10px 14px; border-left:3px solid #d28a17; color:#815510; background:#fff8e5; font-size:13px; }
.readiness { display:flex; gap:12px; align-items:center; margin-top:15px; padding:10px 14px; border-left:3px solid #8a98a7; background:#fff; color:#465463; font-size:13px; } .readiness span { color:#8b96a4; } .readiness.ready { border-color:#14845d; } .readiness.blocked { border-color:#b21e3f; color:#b21e3f; }
.cards { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:16px; margin-bottom:18px; } .card,.panel { background:#fff; border:1px solid #e1e6eb; border-radius:8px; box-shadow:0 3px 12px #27364b0a; } .card { padding:18px; } .card span { color:#7d8997; font-size:13px; } .card strong { display:block; margin:10px 0 8px; font-size:27px; } .card small { font-size:12px; } .positive { color:#14845d; } .negative { color:#b21e3f; } .panel { padding:20px; overflow:auto; } .panel-head { display:flex; align-items:center; gap:14px; margin-bottom:14px; } .panel-head h3 { margin:0; flex:1; font-size:17px; } .panel-head span { color:#8b96a4; font-size:12px; } table { width:100%; border-collapse:collapse; min-width:720px; } th,td { padding:12px 10px; border-bottom:1px solid #edf0f3; text-align:left; font-size:13px; white-space:nowrap; } th { color:#687586; background:#f7f8fa; font-weight:600; } td { color:#354252; } .tree-row { display:grid; grid-template-columns:180px 1fr 100px 80px; gap:16px; align-items:center; padding:14px 0; border-bottom:1px solid #edf0f3; } .tree-row small { display:block; color:#9aa4b0; margin-top:4px; } .bar { height:8px; overflow:hidden; border-radius:8px; background:#edf0f2; } .bar i { display:block; height:100%; border-radius:8px; background:#a7193d; } .tree-row button { padding:6px 8px; border:1px solid #d4dae1; border-radius:4px; color:#8e1534; background:#fff; cursor:pointer; } .ai-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; } .ai-grid article { min-height:130px; padding:15px; border:1px solid #e9d7dd; border-radius:6px; background:#fff8fa; } .ai-grid h4 { margin:0 0 9px; color:#8e1534; } .ai-grid p { margin:0; line-height:1.7; color:#4e5a68; font-size:13px; } .empty { padding:40px; color:#8b96a4; text-align:center; }
@media (max-width: 900px) { .sidebar { width:180px; flex-basis:180px; } .content { padding:20px; } .cards { grid-template-columns:repeat(2,minmax(0,1fr)); } .ai-grid { grid-template-columns:1fr; } } @media (max-width: 640px) { .topbar { gap:12px; padding:0 14px; } .topbar h1 { display:none; } .layout { display:block; } .sidebar { width:auto; padding:10px; } .nav-item { width:auto; margin-right:5px; } .side-note { display:none; } .content { padding:14px; } .cards { grid-template-columns:1fr; } }
</style>
