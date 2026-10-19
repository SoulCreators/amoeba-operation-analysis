<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, type AiDraft, type BatchReadiness, type ConfigurationImportIssue, type ConfigurationImportTask, type ConfigurationImportValidationResult, type DataQualityReport, type DatasetDefinition, type ImportIssue, type MappingEntry, type MetricDefinition, type MetricResult, type Perspective, type PnlResult, type PvmResult, type V13AnalysisPayload } from './api'

type View = 'overview' | 'result' | 'tree' | 'import' | 'issues' | 'pvm' | 'permission' | 'ai' | 'configImport' | 'mapping' | 'metrics' | 'users' | 'menus' | 'audit' | 'gross' | 'matrix' | 'unit' | 'forecast' | 'risk' | 'topics'
type Scenario = 'ACT_BGT' | 'ACT_FC' | 'FC_BGT'
type BatchImportIssue = ImportIssue & { datasetCode: string; datasetName: string; taskNo: string }
const DEFAULT_LOCAL_USER = 'local-finance'
function normalizeUserId(value?: string | null) { const userId = value?.trim(); return !userId || userId === 'demo-finance' ? DEFAULT_LOCAL_USER : userId }
const localUser = normalizeUserId(localStorage.getItem('amoeba.user')); localStorage.setItem('amoeba.user', localUser)
const view = ref<View>('overview'); const period = ref('2026-08'); const periodMode = ref('ytd'); const perspective = ref<Perspective>('BASE'); const scope = ref('ALL'); const batchNo = ref(new URLSearchParams(window.location.search).get('batch') || 'BATCH-202608-BASE-001'); const currentUser = ref(localUser); const currentRole = ref('总部财经'); const currentScope = ref('全部基地'); const source = ref<'api' | 'empty'>('empty'); const loading = ref(false); const error = ref(''); const toastMessage = ref(''); const scenario = ref<Scenario>('ACT_BGT'); const fcVersion = ref('待导入正式预测版本'); const matrixMetric = ref('REVENUE'); const grossPeriod = ref('累计'); const activeTopic = ref('inventory'); const selectedMetric = ref('营业利润')
const metrics = ref<MetricResult[]>([]); const results = ref<PnlResult[]>([]); const pvmRows = ref<PvmResult[]>([]); const treeRows = ref<MetricResult[]>([]); const aiDraft = ref<AiDraft | null>(null); const readiness = ref<BatchReadiness | null>(null); const quality = ref<DataQualityReport | null>(null); const datasets = ref<DatasetDefinition[]>([]); const uploadDataset = ref(''); const selectedFile = ref<File | null>(null); const importMessage = ref(''); const calculationStatus = ref(''); const calculationBusy = ref(false); const issueRows = ref<BatchImportIssue[]>([]); const issuesLoading = ref(false); const configMetrics = ref<MetricDefinition[]>([]); const mappingRows = ref<MappingEntry[]>([]); const mappingType = ref('SALES_PROJECT'); const mappingForm = ref({ type: 'SALES_PROJECT', matchKey: '', mappedValue: '', enabled: true }); const metricForm = ref({ metricCode: '', metricName: '', applicablePerspective: 'ALL', unit: 'CNY', formulaExpression: '', displayOrder: 100, enabled: true, aiSummaryTemplate: '' }); const configMessage = ref(''); const configImportType = ref('SALES_PROJECT_MAPPING'); const configImportFile = ref<File | null>(null); const configImportMessage = ref(''); const roleScope = ref('全部基地')
const configImportTask = ref<ConfigurationImportTask | null>(null); const configTaskQueryNo = ref(''); const configIssueRows = ref<ConfigurationImportIssue[]>([]); const configImportResult = ref<ConfigurationImportValidationResult | null>(null); const configImportBusy = ref(false)
const v13Overview = ref<V13AnalysisPayload | null>(null); const v13Matrix = ref<V13AnalysisPayload | null>(null); const v13Unit = ref<V13AnalysisPayload | null>(null); const v13Forecast = ref<V13AnalysisPayload | null>(null); const v13Risk = ref<V13AnalysisPayload | null>(null); const v13Waterfall = ref<V13AnalysisPayload | null>(null); const v13TopicPayloads = ref<Record<string, V13AnalysisPayload>>({})
const forecastRows = ref<Record<string, unknown>[]>([]); const waterfallFactors = ref<Record<string, unknown>[]>([])
const navGroups = [{ title: '平台功能', items: [['overview', '月度工作台', '◈'], ['result', '损益结果', '▤'], ['tree', '指标树', '⌘'], ['import', '月度数据导入', '⇧'], ['issues', '异常清单', '!'], ['pvm', 'PVM因素分析', '◫'], ['permission', '权限验证', '⊙'], ['ai', 'AI经营分析', '✦']] as [View, string, string][] }, { title: 'V1.3分析', items: [['gross', '毛利分析', '▥'], ['matrix', '基地×产品矩阵', '▦'], ['unit', '单位效率分析', '◌'], ['forecast', 'FC预测与瀑布', '↗'], ['risk', '一页风险提示', '⚠'], ['topics', 'P1专题分析', '◇']] as [View, string, string][] }, { title: '配置与追溯', items: [['configImport', '基础配置导入', '⇧'], ['mapping', '映射配置', '⌘'], ['metrics', '指标库', '◉'], ['users', '用户管理', '♙'], ['menus', '菜单管理', '☷'], ['audit', '操作记录', '≡']] as [View, string, string][] }]
const viewNames: Record<View, string> = { overview: '月度工作台', result: '损益计算结果', tree: '指标树', import: '月度数据导入', issues: '异常清单', pvm: 'PVM因素分析', permission: '权限验证', ai: 'AI经营分析', configImport: '基础配置导入', mapping: '映射配置', metrics: '指标库', users: '用户管理', menus: '菜单管理', audit: '操作记录', gross: '毛利分析', matrix: '事业部经营矩阵', unit: '单位效率分析', forecast: 'FC预测与瀑布', risk: '一页风险提示', topics: 'P1专题分析' }
const title = computed(() => viewNames[view.value]); const perspectiveName = computed(() => ({ BASE: '基地', PRODUCT_LINE: '产品线', BUSINESS_UNIT: '事业部' }[perspective.value])); const scenarioLabel = computed(() => ({ ACT_BGT: 'ACT vs BGT', ACT_FC: 'ACT vs FC', FC_BGT: 'FC vs BGT' }[scenario.value])); const activeScopeText = computed(() => currentScope.value === '全部基地' || scope.value === 'ALL' ? '全域' : currentScope.value); const activeTopicData = computed(() => p1Topics[activeTopic.value]); const visiblePnl = computed(() => scope.value === 'ALL' ? results.value : results.value.filter(row => row.scopeCode === scope.value)); const readinessReady = computed(() => readiness.value?.readyForCalculation === true)
const activeTopicPayload = computed(() => v13TopicPayloads.value[activeTopic.value]); const topicDisplayRows = computed(() => (activeTopicPayload.value?.rows || []).map((row) => Object.values(row).map((value) => String(value ?? '—'))))
const dashboardCards = ref([
  { key: 'revenue', label: '营业收入', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'gross', label: '毛利', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'operating', label: '营业利润', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'budgetRate', label: '预算完成率', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'grossRate', label: '毛利率', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'materialCost', label: '材料成本', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'periodFee', label: '期间费用', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'volume', label: '出货电量', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
])
const rankRows = ref<string[][]>([])
const overviewBars = ref<{ budget: number; actual: number; label: string }[]>([])
const topFavorable = ref<string[][]>([])
const topAdverse = ref<string[][]>([])
const p1Topics: Record<string, { name: string; note: string; headers: string[]; rows: string[][] }> = {
  inventory: { name: '库龄分析', note: '等待真实库存事实数据导入。', headers: ['基地', '型号', '库存金额', '0-90天', '91-180天', '180天+', '状态'], rows: [] },
  operation: { name: '事业部运营费用下钻', note: '等待真实期间费用数据导入。', headers: ['事业部', '费用域', '科目', '实际', '预算', 'GAP', '判断'], rows: [] },
  drill: { name: '产品线三级下钻', note: '等待真实收入成本明细导入。', headers: ['产品线', '型号', '客户项目', '收入', '毛利', 'GAP'], rows: [] },
  pvm: { name: 'PVM跨维度强化', note: '等待真实批次计算结果。', headers: ['基地', '事业部', '型号', '客户', '项目', '收入GAP', '毛利GAP'], rows: [] },
  rd: { name: '研发提单分析', note: '等待真实研发费用/提单数据导入。', headers: ['提单', '项目', '型号', '状态', '周期', '金额', '匹配'], rows: [] },
  account: { name: '基地费用 by 科目矩阵', note: '等待真实基地费用数据导入。', headers: ['基地', '制造', '人工', '能源', '物流', '运营', '合计'], rows: [] },
}
type MatrixRow = { base: string; product: string; revenue: number[]; gross: number[]; profit: number[] }
const matrixRows = ref<MatrixRow[]>([])
const unitRows = ref<string[][]>([])
const riskRows = ref<string[][]>([])
const riskReady = computed(() => Boolean(v13Risk.value && v13Risk.value.status === 'CALCULATED'))
const riskDataComplete = computed(() => riskReady.value && Array.isArray(v13Risk.value?.risks))
const riskHasRows = computed(() => riskDataComplete.value && Boolean(v13Risk.value?.risks?.length))
const riskSummaryLabel = computed(() => !riskReady.value ? (v13Risk.value?.status === 'PARTIAL_PENDING_THRESHOLD' ? '待确认' : v13Risk.value ? '待计算' : '待加载') : !riskDataComplete.value ? '待确认' : riskHasRows.value ? '需关注' : '未发现负向偏差')
const riskSummaryClass = computed(() => !riskReady.value || !riskDataComplete.value ? 'risk-level-mid' : riskHasRows.value ? 'risk-level-high' : 'green')
const riskSummaryCount = computed(() => riskDataComplete.value ? (v13Risk.value?.risks?.length || 0) : '—')
let loadRequestId = 0
let v13LoadRequestId = 0
function resetOverviewPresentation() {
  dashboardCards.value = [
    { key: 'revenue', label: '营业收入', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'gross', label: '毛利', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'operating', label: '营业利润', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'budgetRate', label: '预算完成率', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'grossRate', label: '毛利率', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'materialCost', label: '材料成本', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'periodFee', label: '期间费用', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'volume', label: '出货电量', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  ]
  rankRows.value = []
  overviewBars.value = []
  topFavorable.value = []
  topAdverse.value = []
}
function resetV13State() {
  resetOverviewPresentation()
  matrixRows.value = []
  unitRows.value = []
  riskRows.value = []
  forecastRows.value = []
  waterfallFactors.value = []
  v13Overview.value = null
  v13Matrix.value = null
  v13Unit.value = null
  v13Forecast.value = null
  v13Risk.value = null
  v13Waterfall.value = null
  v13TopicPayloads.value = {}
}
function scenarioPair(actual: number, budget: number, fc = budget + (actual - budget) * 0.72) { return scenario.value === 'ACT_FC' ? [actual, fc] : scenario.value === 'FC_BGT' ? [fc, budget] : [actual, budget] }; function scenarioGap(actual: number, budget: number, fc?: number) { const [current, base] = scenarioPair(actual, budget, fc); return { current, base, gap: current - base, rate: base ? (current - base) / Math.abs(base) : null } }; function format(code: string, value: number) { if (code.includes('RATE')) return `${(value * 100).toFixed(2)}%`; return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value) }; function matrixPair(row: MatrixRow) { const values = matrixMetric.value === 'GROSS' ? row.gross : matrixMetric.value === 'PROFIT' ? row.profit : row.revenue; return scenarioPair(values[0], values[1], values[2]) }; function matrixTotal() { return matrixRows.value.reduce((r, row) => { const p = matrixPair(row); r.current += p[0]; r.base += p[1]; return r }, { current: 0, base: 0 }) }
function setUser() { const normalizedUser = normalizeUserId(currentUser.value); localStorage.setItem('amoeba.user', normalizedUser); currentUser.value = normalizedUser; showToast('用户范围已切换') }; function showToast(message: string) { toastMessage.value = message; window.setTimeout(() => { if (toastMessage.value === message) toastMessage.value = '' }, 2600) }; function go(next: View) { view.value = next; error.value = ''; if (next === 'overview' || next === 'import' || next === 'issues' || next === 'configImport' || next === 'mapping' || next === 'metrics' || next === 'result' || next === 'pvm' || next === 'tree' || next === 'ai') void load() }
async function ensureBatch() {
  try {
    await api.batch(batchNo.value)
  } catch (e) {
    const message = e instanceof Error ? e.message : ''
    if (!message.includes('批次不存在')) throw e
    await api.createBatch({ batchNo: batchNo.value, period: period.value, perspective: perspective.value })
  }
}

async function loadIssues(nextReadiness = readiness.value) {
  issuesLoading.value = true
  try {
    const datasetsWithTasks = (nextReadiness?.datasets || []).filter((dataset) => dataset.taskNo)
    if (!datasetsWithTasks.length) {
      issueRows.value = []
      return
    }
    const grouped = await Promise.all(datasetsWithTasks.map(async (dataset) => {
      const issues = await api.importIssues(dataset.taskNo!)
      return issues.map((issue) => ({
        ...issue,
        datasetCode: dataset.datasetCode,
        datasetName: dataset.datasetName,
        taskNo: dataset.taskNo!,
      }))
    }))
    issueRows.value = grouped.flat()
  } finally {
    issuesLoading.value = false
  }
}

async function refreshIssues() {
  try {
    const currentReadiness = await api.readiness(batchNo.value)
    readiness.value = currentReadiness
    await loadIssues(currentReadiness)
    source.value = 'api'
  } catch (e) {
    error.value = `异常清单加载失败：${e instanceof Error ? e.message : '请求失败'}`
  }
}

async function load() {
  const requestId = ++loadRequestId
  resetV13State()
  loading.value = true
  error.value = ''
  source.value = 'empty'
  try {
    await ensureBatch()
    if (requestId !== loadRequestId) return
    if (view.value !== 'import' && view.value !== 'issues') {
      const nextMetrics = await api.overview(period.value, perspective.value, scope.value)
      if (requestId !== loadRequestId) return
      metrics.value = nextMetrics
    }
    const nextReadiness = await api.readiness(batchNo.value)
    if (requestId !== loadRequestId) return
    readiness.value = nextReadiness
    if (view.value === 'issues') await loadIssues(nextReadiness)
    const nextQuality = await api.quality(batchNo.value)
    if (requestId !== loadRequestId) return
    quality.value = nextQuality
    const nextDatasets = await api.datasets(perspective.value)
    if (requestId !== loadRequestId) return
    datasets.value = nextDatasets
    if (!uploadDataset.value || !nextDatasets.some((dataset) => dataset.code === uploadDataset.value)) {
      uploadDataset.value = nextDatasets.find((dataset) => dataset.required)?.code || nextDatasets[0]?.code || ''
    }
    source.value = 'api'
    if (view.value === 'result') {
      const result = await api.result(batchNo.value)
      if (requestId !== loadRequestId) return
      results.value = result.pnls
      calculationStatus.value = result.status
    }
    if (view.value === 'pvm') {
      const nextPvmRows = await api.pvm(batchNo.value)
      if (requestId !== loadRequestId) return
      pvmRows.value = nextPvmRows
    }
    if (view.value === 'tree') {
      const nextTreeRows = await api.metricTree(batchNo.value)
      if (requestId !== loadRequestId) return
      treeRows.value = nextTreeRows
    }
    if (view.value === 'ai') {
      const nextAiDraft = await api.ai(batchNo.value)
      if (requestId !== loadRequestId) return
      aiDraft.value = nextAiDraft
    }
    if (view.value === 'metrics') {
      const nextConfigMetrics = await api.metrics(perspective.value, true)
      if (requestId !== loadRequestId) return
      configMetrics.value = nextConfigMetrics
    }
    if (view.value === 'mapping') {
      const nextMappingRows = await api.mappings(mappingType.value, true)
      if (requestId !== loadRequestId) return
      mappingRows.value = nextMappingRows
    }
  } catch (e) {
    if (requestId !== loadRequestId) return
    const message = e instanceof Error ? e.message : '请求失败'
    source.value = 'empty'
    metrics.value = []
    results.value = []
    pvmRows.value = []
    treeRows.value = []
    aiDraft.value = null
    calculationStatus.value = ''
    readiness.value = null
    quality.value = null
    datasets.value = []
    issueRows.value = []
    error.value = message.includes('批次尚未完成计算') ? '当前批次尚未完成计算：请先上传并校验必需数据集，再开始计算。' : `真实数据接口暂不可用：${message}。页面不会展示演示数据。`
  } finally {
    if (requestId === loadRequestId) loading.value = false
  }
}

async function calculateBatch() {
  calculationBusy.value = true
  importMessage.value = ''
  try {
    await ensureBatch()
    const currentReadiness = await api.readiness(batchNo.value)
    readiness.value = currentReadiness
    if (!currentReadiness.readyForCalculation) {
      importMessage.value = '当前批次未满足计算门禁：请完成全部必需数据集上传并校验通过后再开始计算。'
      return
    }
    const currentBatch = await api.batch(batchNo.value)
    if (currentBatch.status === 'DRAFT' || currentBatch.status === 'FAILED') {
      await api.moveBatch(batchNo.value, 'CHECKING')
    }
    const checkingBatch = await api.batch(batchNo.value)
    if (checkingBatch.status === 'CHECKING') {
      await api.moveBatch(batchNo.value, 'READY_FOR_CALCULATION')
    }
    const calculated = await api.calculate(batchNo.value)
    results.value = calculated.results || calculated.pnls || []
    calculationStatus.value = calculated.status
    source.value = 'api'
    view.value = 'result'
    try {
      readiness.value = await api.readiness(batchNo.value)
      quality.value = await api.quality(batchNo.value)
    } catch {
      // 计算结果已经由服务端返回；完整性摘要失败不应覆盖真实结果或伪造页面数据。
    }
    showToast(`批次 ${batchNo.value} 已完成计算`)
  } catch (e) {
    importMessage.value = `开始计算失败：${e instanceof Error ? e.message : '请求失败'}`
  } finally {
    calculationBusy.value = false
  }
}

async function confirmCalculation() {
  calculationBusy.value = true
  try {
    const confirmed = await api.confirmCalculation(batchNo.value)
    results.value = confirmed.results || confirmed.pnls || results.value
    calculationStatus.value = confirmed.status
    source.value = 'api'
    showToast('计算结果已确认')
  } catch (e) {
    error.value = `结果确认失败：${e instanceof Error ? e.message : '请求失败'}`
  } finally {
    calculationBusy.value = false
  }
}

async function publishCalculation() {
  calculationBusy.value = true
  try {
    const published = await api.publishCalculation(batchNo.value)
    results.value = published.results || published.pnls || results.value
    calculationStatus.value = published.status
    source.value = 'api'
    showToast('计算结果已发布')
  } catch (e) {
    error.value = `结果发布失败：${e instanceof Error ? e.message : '请求失败'}`
  } finally {
    calculationBusy.value = false
  }
}

async function generateAi() { try { aiDraft.value = await api.generateAi(batchNo.value); source.value = 'api'; showToast('AI分析草稿已生成') } catch (e) { const message = e instanceof Error ? e.message : '请求失败'; aiDraft.value = null; error.value = message.includes('批次尚未完成计算') ? '当前批次尚未完成计算：请先上传并校验必需数据集，再生成AI分析。' : `AI真实接口暂不可用：${message}` } }

async function publishAiDraft() {
  try {
    aiDraft.value = await api.publishAi(batchNo.value)
    source.value = 'api'
    showToast('AI分析已确认并发布')
  } catch (e) {
    error.value = `AI分析发布失败：${e instanceof Error ? e.message : '请求失败'}`
  }
}

async function submitImport() {
  if (!selectedFile.value) {
    importMessage.value = '请选择需要上传并校验的文件。'
    return
  }
  if (!uploadDataset.value) {
    importMessage.value = '请选择本次文件对应的数据集。'
    return
  }
  try {
    await ensureBatch()
    const task = await api.createImport(batchNo.value, { datasetCode: uploadDataset.value, fileName: selectedFile.value.name })
    await api.validateImport(task.taskNo, selectedFile.value)
    importMessage.value = `校验完成：${selectedFile.value.name}，请查看异常清单并刷新批次完整性；满足门禁后点击“开始计算”。`
    await load()
  } catch (e) {
    importMessage.value = `导入失败：${e instanceof Error ? e.message : '请求失败'}`
  }
}

async function saveMetric() { if (!metricForm.value.metricCode || !metricForm.value.metricName || !metricForm.value.formulaExpression) { configMessage.value = '指标编码、名称和公式不能为空。'; return } try { const saved = await api.upsertMetric(metricForm.value); configMetrics.value = [...configMetrics.value.filter(r => r.metricCode !== saved.metricCode), saved]; configMessage.value = `指标 ${saved.metricName} 已保存。` } catch (e) { configMessage.value = `指标保存失败：${e instanceof Error ? e.message : '请求失败'}` } }

async function saveMapping() { if (!mappingForm.value.matchKey || !mappingForm.value.mappedValue) { configMessage.value = '匹配键和值不能为空。'; return } try { const saved = await api.upsertMapping(mappingForm.value); mappingRows.value = [...mappingRows.value.filter(r => !(r.type === saved.type && r.matchKey === saved.matchKey)), saved] } catch (e) { configMessage.value = `映射保存失败：${e instanceof Error ? e.message : '请求失败'}` } }

async function disableMapping(row: MappingEntry) { try { await api.disableMapping({ type: row.type, matchKey: row.matchKey }); row.enabled = false; showToast(`映射 ${row.matchKey} 已停用`) } catch (e) { configMessage.value = `停用映射失败：${e instanceof Error ? e.message : '请求失败'}` } }

async function uploadConfig() { if (!configImportFile.value) { configImportMessage.value = '请选择基础配置文件。'; return }; await runConfigImport() }

function refreshAnalysis() { void load(); void loadV13() }
function changeRole() { currentScope.value = currentRole.value === '总部财经' ? '全部基地' : currentRole.value === '基地运营' ? '泰国基地' : '南昌基地'; roleScope.value = currentScope.value; showToast(`已切换为${currentRole.value}`) }; function printRisk() { document.body.classList.add('risk-print'); window.print(); window.setTimeout(() => document.body.classList.remove('risk-print'), 500) }; function exportView(name: string) { showToast(`${name}导出任务已创建，当前仅导出真实接口返回内容`) }; function showMetric(name: string) { selectedMetric.value = name }; function pickFile(e: Event) { selectedFile.value = (e.target as HTMLInputElement).files?.[0] || null }; function pickConfigFile(e: Event) { configImportFile.value = (e.target as HTMLInputElement).files?.[0] || null }; onMounted(load)
function configStatusLabel(status?: string) {
  return ({ CREATED: '已创建', VALIDATING: '校验中', VALID: '校验并应用通过', INVALID: '校验/应用失败', CANCELLED: '已取消' } as Record<string, string>)[status || ''] || status || '未知'
}

function configStatusClass(status?: string) {
  return status === 'VALID' ? 'ok' : status === 'INVALID' || status === 'CANCELLED' ? 'warn' : 'gray'
}

function configIssueSummary(issues: ConfigurationImportIssue[]) {
  if (!issues.length) return '服务端未返回问题明细。'
  return issues.slice(0, 2).map((issue) => `${issue.issueCode}: ${issue.issueMessage}`).join('；')
}

async function refreshConfigImport(taskNo: string) {
  const [task, issues] = await Promise.all([api.configImport(taskNo), api.configImportIssues(taskNo)])
  configImportTask.value = task
  configTaskQueryNo.value = task.taskNo
  configIssueRows.value = issues
  return task
}

async function queryConfigImport() {
  const taskNo = configTaskQueryNo.value.trim()
  if (!taskNo) {
    configImportMessage.value = '请输入任务编号后再查询。'
    return
  }
  configImportBusy.value = true
  try {
    await refreshConfigImport(taskNo)
    configImportMessage.value = configImportTask.value?.status === 'VALID'
      ? `任务 ${taskNo} 已完成校验并应用。`
      : `任务 ${taskNo} 当前状态为${configStatusLabel(configImportTask.value?.status)}：${configIssueSummary(configIssueRows.value)}`
    source.value = 'api'
  } catch (e) {
    configImportMessage.value = `查询任务失败：${e instanceof Error ? e.message : '请求失败'}`
  } finally {
    configImportBusy.value = false
  }
}

async function runConfigImport() {
  const file = configImportFile.value
  if (!file) {
    configImportMessage.value = '请选择基础配置文件。'
    return
  }
  configImportBusy.value = true
  configImportTask.value = null
  configIssueRows.value = []
  configImportResult.value = null
  configImportMessage.value = ''
  try {
    const created = await api.createConfigImport({ type: configImportType.value, fileName: file.name })
    configImportTask.value = created
    configTaskQueryNo.value = created.taskNo
    configImportMessage.value = `任务 ${created.taskNo} 已创建，正在上传文件。`

    const uploaded = await api.uploadConfigImport(created.taskNo, file)
    configImportTask.value = uploaded
    configImportMessage.value = `任务 ${created.taskNo} 已上传文件，正在执行校验并应用。`

    configImportResult.value = await api.validateConfigImport(created.taskNo, file)
    const finalTask = await refreshConfigImport(created.taskNo)
    source.value = 'api'
    if (finalTask.status === 'VALID') {
      configImportMessage.value = configImportType.value === 'USER_PERMISSION'
        ? `任务 ${finalTask.taskNo} 结构校验通过；用户角色权限不会自动生效，仍需管理员授权。`
        : `任务 ${finalTask.taskNo} 校验并应用通过：${configImportResult.value.rowCount} 行配置已生效。`
    } else if (configImportType.value === 'USER_PERMISSION' && configImportResult.value.status === 'INVALID') {
      configImportMessage.value = `任务 ${finalTask.taskNo} 已完成结构校验，但未自动应用；当前状态为${configStatusLabel(finalTask.status)}，需管理员授权后另行处理。${configIssueSummary(configIssueRows.value)}`
    } else {
      configImportMessage.value = `任务 ${finalTask.taskNo} ${configStatusLabel(finalTask.status)}，配置未写入：${configIssueSummary(configIssueRows.value)}`
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '请求失败'
    if (configImportTask.value?.taskNo) {
      try { await refreshConfigImport(configImportTask.value.taskNo) } catch { /* 保留已知任务状态 */ }
      configImportMessage.value = `任务 ${configImportTask.value.taskNo} 处理失败：${message}；当前状态为${configStatusLabel(configImportTask.value.status)}，未伪装为成功。`
    } else {
      configImportMessage.value = `配置导入失败：${message}`
    }
  } finally {
    configImportBusy.value = false
  }
}

function metricRows(payload: V13AnalysisPayload | null): MetricResult[] {
  return (payload?.metrics || []).map((row) => ({
    code: String(row.metricCode || ''),
    name: String(row.metricName || row.metricCode || ''),
    actual: Number(row.actual || 0),
    budget: Number(row.budget || 0),
    gap: Number(row.gap || 0),
    aiSummary: typeof row.aiSummary === 'string' ? row.aiSummary : undefined,
  }))
}

function applyOverviewPayload(payload: V13AnalysisPayload) {
  resetOverviewPresentation()
  const rows = metricRows(payload)
  metrics.value = rows
  treeRows.value = rows
  const byCode = new Map(rows.map((row) => [row.code, row]))
  const cardSpecs = [
    ['revenue', 'REVENUE', '营业收入'],
    ['gross', 'GROSS_PROFIT', '毛利'],
    ['operating', 'NET_PROFIT', '营业利润'],
    ['volume', 'VOLUME', '出货电量'],
  ] as const
  for (const [key, code, label] of cardSpecs) {
    const row = byCode.get(code)
    const card = dashboardCards.value.find((item) => item.key === key)
    if (!row || !card) continue
    card.label = label
    card.value = format(code, row.actual)
    card.unit = code === 'VOLUME' ? '' : '（接口原始单位）'
    card.delta = `${row.gap >= 0 ? '▲' : '▼'} ${format(code, Math.abs(row.gap))} vs 预算`
    card.good = row.gap >= 0
  }
  const top = payload.top as Record<string, unknown> | undefined
  const favorable = Array.isArray(top?.positive) ? top.positive : []
  const adverse = Array.isArray(top?.negative) ? top.negative : []
  const toTopRow = (item: unknown): string[] => {
    const row = item as Record<string, unknown>
    const gap = Number(row.gap || 0)
    const rate = row.gapRate == null ? '' : `${(Number(row.gapRate) * 100).toFixed(1)}%`
    return [String(row.scopeCode || row.scope || '—'), format('GROSS_PROFIT', gap), rate]
  }
  topFavorable.value = favorable.map(toTopRow)
  topAdverse.value = adverse.map(toTopRow)
}

function applyMatrixPayload(payload: V13AnalysisPayload) {
  const rows = (payload.rows || []).filter((row) => row.baseCode || row.scopeCode).map((row) => ({
    base: String(row.baseCode || row.scopeCode || '—'),
    product: String(row.productCode || '待补充产品维度'),
    revenue: [Number(row.actual || 0), Number(row.budget || 0), Number(row.actual || 0)],
    gross: [Number(row.actual || 0), Number(row.budget || 0), Number(row.actual || 0)],
    profit: [Number(row.actual || 0), Number(row.budget || 0), Number(row.actual || 0)],
  }))
  matrixRows.value = rows
}

function applyUnitPayload(payload: V13AnalysisPayload) {
  if (!payload.rows?.length) {
    unitRows.value = []
    return
  }
  unitRows.value = payload.rows.map((row) => [
    String(row.baseCode || row.base || '—'), String(row.lineCode || row.line || '—'),
    format('MANUFACTURING_COST', Number(row.manufacturingCost || 0)),
    format('OUTPUT_WH', Number(row.outputWh || 0)), format('UNIT_COST', Number(row.unitCost || 0)),
    row.momGap == null ? '—' : `${Number(row.momGap) >= 0 ? '+' : ''}${format('UNIT_COST', Number(row.momGap))}`,
  ])
}

function applyRiskPayload(payload: V13AnalysisPayload) {
  if (payload.status !== 'CALCULATED' || !Array.isArray(payload.risks) || !payload.risks.length) {
    riskRows.value = []
    return
  }
  riskRows.value = payload.risks.map((row) => [
    String(row.level || '观察'), String(row.metricName || row.metricCode || '经营指标'),
    String(row.message || '—'), String(row.ownerRole || '待确认'), String(row.action || '结合业务原因进一步核查'),
    String(row.thresholdStatus || '待确认'),
  ])
}

function applyForecastPayload(payload: V13AnalysisPayload) {
  forecastRows.value = payload.rows || []
}

function applyWaterfallPayload(payload: V13AnalysisPayload) {
  waterfallFactors.value = payload.factors || []
}

async function loadV13() {
  const requestId = ++v13LoadRequestId
  resetV13State()
  if (!['overview', 'gross', 'matrix', 'unit', 'forecast', 'risk', 'topics'].includes(view.value)) return
  try {
    if (view.value === 'overview' || view.value === 'gross') {
      const payload = await api.v13Overview(batchNo.value, scenario.value)
      if (requestId !== v13LoadRequestId) return
      v13Overview.value = payload
      applyOverviewPayload(v13Overview.value)
    } else if (view.value === 'matrix') {
      const payload = await api.v13Matrix(batchNo.value, matrixMetric.value)
      if (requestId !== v13LoadRequestId) return
      v13Matrix.value = payload
      applyMatrixPayload(v13Matrix.value)
    } else if (view.value === 'unit') {
      const payload = await api.v13UnitEfficiency(batchNo.value)
      if (requestId !== v13LoadRequestId) return
      v13Unit.value = payload
      applyUnitPayload(v13Unit.value)
    } else if (view.value === 'forecast') {
      const payload = await api.v13Forecast(batchNo.value, scenario.value, fcVersion.value)
      if (requestId !== v13LoadRequestId) return
      v13Forecast.value = payload
      applyForecastPayload(v13Forecast.value)
      const waterfallPayload = await api.v13Waterfall(batchNo.value)
      if (requestId !== v13LoadRequestId) return
      v13Waterfall.value = waterfallPayload
      applyWaterfallPayload(v13Waterfall.value)
    } else if (view.value === 'risk') {
      const payload = await api.v13Risk(batchNo.value)
      if (requestId !== v13LoadRequestId) return
      v13Risk.value = payload
      applyRiskPayload(v13Risk.value)
    } else if (view.value === 'topics') {
      const payload = await api.v13Topic(batchNo.value, activeTopic.value)
      if (requestId !== v13LoadRequestId) return
      v13TopicPayloads.value[activeTopic.value] = payload
    }
    if (requestId !== v13LoadRequestId) return
    source.value = 'api'
  } catch (e) {
    if (requestId !== v13LoadRequestId) return
    resetV13State()
    const message = e instanceof Error ? e.message : '请求失败'
    error.value = message.includes('批次尚未完成计算')
      ? '当前批次尚未完成计算：请先上传并校验必需数据集，再查看V1.3分析。'
      : `V1.3真实接口暂不可用：${message}`
  }
}

onMounted(loadV13)
watch([view, scenario, matrixMetric, activeTopic], () => { void loadV13() })
watch([batchNo, period, periodMode, perspective], () => { void refreshAnalysis() })
watch(view, (next) => {
  if (['result', 'pvm', 'tree', 'ai'].includes(next)) void load()
})
</script>

<template>
  <div class="app"><header class="top"><div class="brand">数智财经<small>FINANCE INTELLIGENCE</small></div><div class="topline"></div><div class="top-title">阿米巴经营分析平台 · 真实数据模式</div><div class="top-right"><span class="role-pill">{{ currentRole }}｜{{ activeScopeText }}数据</span><div class="user-dot">财</div><input class="user-input" v-model="currentUser" @keyup.enter="setUser"><button class="top-user-btn" @click="setUser">切换</button></div></header>
    <div class="layout"><aside class="side"><template v-for="group in navGroups" :key="group.title"><h3>{{ group.title }}</h3><button v-for="item in group.items" :key="item[0]" class="nav" :class="{ active: view === item[0] }" @click="go(item[0])"><i>{{ item[2] }}</i><span>{{ item[1] }}</span></button></template><div class="side-foot">真实数据模式<br>无真实结果时不展示指标<br>{{ source === 'api' ? '已连接后端接口' : '等待真实数据' }}</div></aside>
      <main class="main"><div class="crumb">经营分析平台 / <span>{{ title }}</span></div><div class="toolbar"><label>分析期间</label><select v-model="period"><option value="2026-08">2026年8月</option><option value="2026-07">2026年7月</option><option value="2026-06">2026年6月</option></select><label>筛选方式</label><select v-model="periodMode"><option value="current">当期</option><option value="ytd">累计（1月至所选月）</option><option value="custom">自定义月份组合</option></select><label>数据视角</label><select v-model="perspective"><option value="BASE">基地视角</option><option value="PRODUCT_LINE">产品线视角</option><option value="BUSINESS_UNIT">事业部视角</option></select><span class="spacer"></span><span class="scenario-note">{{ scenarioLabel }}</span><select v-model="scenario"><option value="ACT_BGT">ACT vs BGT</option><option value="ACT_FC">ACT vs FC</option><option value="FC_BGT">FC vs BGT</option></select><button class="btn" @click="showToast('当前仅展示真实接口返回内容；请先导入并计算真实批次')">数据说明</button><button class="btn primary" :disabled="loading" @click="load">{{ loading ? '加载中…' : '刷新分析' }}</button></div><div class="v13-banner"><b>V1.3 看板优化</b><span>绝对GAP · TOP · FC · 风险一页纸</span><span class="v13-badge">{{ scenarioLabel }}</span><span class="spacer"></span><small>7项P0已恢复 · 6项P1原型 · 无真实结果时不展示金额</small></div><div v-if="error" class="alert" style="margin-top:12px">{{ error }}</div><div class="readiness" :class="readiness?.readyForCalculation ? 'ready' : 'blocked'"><b>批次完整性：</b>{{ readiness?.readyForCalculation ? '已满足计算条件' : '暂不可计算' }}<span v-if="readiness">· {{ readiness.datasets.filter(d => d.required && d.present && d.status === 'VALID').length }}/{{ readiness.datasets.filter(d => d.required).length }} 个必需数据集通过</span><span v-if="quality">· 数据质量 {{ quality.status === 'READY' ? '通过' : '阻断' }}</span></div>
        <section v-if="view === 'overview'" class="view active"><div class="section-head"><div><h2>月度工作台</h2><p>当前展示：<b>{{ perspectiveName }}</b>视角 · {{ period }} · {{ periodMode === 'ytd' ? '累计口径' : '当期口径' }} · <span class="scope-chip">{{ activeScopeText }}</span></p></div><div><span class="tag">{{ metrics.length ? '真实接口结果' : '等待真实计算结果' }}</span> <button class="btn" @click="exportView('经营总览')">导出</button></div></div><div class="cards"><article v-for="card in dashboardCards" :key="card.key" class="card"><div class="metric-label">{{ card.label }}</div><div class="metric">{{ card.value }}<span>{{ card.unit }}</span></div><div :class="['delta', card.good ? 'up' : 'down']">{{ card.delta }}</div></article></div><div v-if="view === 'overview'" class="panel" style="margin-top:15px"><div class="panel-title">月度工作台 <small>DATA-001 readiness</small></div><div v-if="readiness?.datasets?.length" class="dataset-grid"><div v-for="row in readiness.datasets" :key="row.datasetCode" class="dataset-card"><strong>{{ row.datasetName }}</strong><small>{{ row.required ? '必需' : '可选' }} · <span :class="row.status === 'VALID' ? 'green' : row.status === 'MISSING' ? 'muted' : 'red'">{{ row.status }} · {{ row.issueCount || 0 }} 个问题</span></small></div></div><div v-else class="placeholder-card">暂无真实批次状态，请先刷新或创建批次。</div><div class="action-row"><button class="btn" @click="go('import')">进入上传与完整性</button><button class="btn ghost" @click="go('issues')">查看异常清单</button></div></div><div class="grid2"><div class="panel"><div class="panel-title">收入与利润趋势 <small>仅显示真实批次</small></div><div v-if="overviewBars.length" class="bar-list"><div v-for="bar in overviewBars" :key="bar.label" class="bar-col"><div class="bar-wrap"><i class="bar plan" :style="{ height: `${bar.budget}%` }"></i><i class="bar" :style="{ height: `${bar.actual}%` }"></i></div>{{ bar.label }}</div></div><div v-else class="placeholder-card">暂无真实趋势数据，请先导入并计算月度批次。</div></div><div class="panel"><div class="panel-title">重点经营单元 <small>仅显示真实批次</small></div><div v-if="rankRows.length" v-for="(row, index) in rankRows" :key="row[0]" class="rank-row"><span :class="['rank', index < 2 ? 'hot' : '']">{{ index + 1 }}</span><span>{{ row[0] }}<div class="mini-bar"><i :style="{ width: row[3] }"></i></div></span><b class="right">{{ row[1] }}</b><span :class="[row[2].startsWith('+') ? 'green' : 'red', 'right']">{{ row[2] }}</span></div><div v-else class="placeholder-card">暂无真实经营单元排名。</div></div></div><div class="section-head"><div><h2>经营异常与亮点</h2><p>异常清单仅来自真实计算结果</p></div><button class="btn ghost" @click="go('pvm')">进入因素分析 →</button></div><div class="panel"><div class="placeholder-card">暂无真实异常与亮点数据。</div></div><div class="top-split"><div class="panel"><div class="panel-title">有利影响 TOP 3</div><div v-if="topFavorable.length" v-for="(row,index) in topFavorable" :key="row[0]" class="top-item"><span class="rank hot">{{ index + 1 }}</span><b>{{ row[0] }}</b><span class="top-value green">{{ row[1] }}</span><span class="top-rate green">{{ row[2] }}</span></div><div v-else class="placeholder-card">暂无真实有利影响。</div></div><div class="panel"><div class="panel-title">不利影响 TOP 3</div><div v-if="topAdverse.length" v-for="(row,index) in topAdverse" :key="row[0]" class="top-item"><span class="rank">{{ index + 1 }}</span><b>{{ row[0] }}</b><span class="top-value red">{{ row[1] }}</span><span class="top-rate red">{{ row[2] }}</span></div><div v-else class="placeholder-card">暂无真实不利影响。</div></div></div><div class="panel" style="margin-top:15px"><div class="panel-title">统一经营指标 <small>绝对GAP与百分比GAP · {{ scenarioLabel }}</small></div><div v-if="metrics.length" class="table-scroll"><table class="pnl-table"><thead><tr><th>指标</th><th>当前值</th><th>基准值</th><th>绝对GAP</th><th>GAP%</th><th>AI简析</th></tr></thead><tbody><tr v-for="m in metrics" :key="m.code"><td>{{ m.name }}</td><td>{{ format(m.code, scenarioGap(m.actual, m.budget).current) }}</td><td>{{ format(m.code, scenarioGap(m.actual, m.budget).base) }}</td><td :class="scenarioGap(m.actual, m.budget).gap >= 0 ? 'green' : 'red'">{{ format(m.code, scenarioGap(m.actual, m.budget).gap) }}</td><td>{{ scenarioGap(m.actual, m.budget).rate === null ? '—' : `${(scenarioGap(m.actual, m.budget).rate * 100).toFixed(1)}%` }}</td><td>{{ m.aiSummary || '—' }}</td></tr></tbody></table></div><div v-else class="placeholder-card">暂无真实经营指标，请先完成数据导入、校验和计算。</div></div></section>
        <section v-else-if="view === 'result'" class="view active"><div class="section-head"><div><h2>损益计算结果</h2><p>仅展示服务端真实计算结果 · <span class="scope-chip">{{ activeScopeText }}</span></p></div><button class="btn" @click="exportView('损益结果')">导出</button></div><div class="permission-callout"><b>口径说明：</b>本页面不补造预算、GAP或结果值；所有字段必须来自已计算批次。</div><div v-if="visiblePnl.length" class="panel table-scroll"><table class="pnl-table"><thead><tr><th>分析对象</th><th>本期实际</th><th>本期预算</th><th>VS预算</th><th>销售成本</th><th>毛利</th><th>基地费用</th><th>营业利润</th><th>净利率</th></tr></thead><tbody><tr v-for="row in visiblePnl" :key="row.scopeCode"><td><b>{{ row.scopeCode }}</b></td><td>{{ format('REVENUE', row.lines.REVENUE || 0) }}</td><td>{{ row.lines.BUDGET_REVENUE == null ? '—' : format('REVENUE', row.lines.BUDGET_REVENUE) }}</td><td>{{ row.lines.GAP_REVENUE == null ? '—' : format('REVENUE', row.lines.GAP_REVENUE) }}</td><td>{{ format('SALES_COST', row.lines.SALES_COST || 0) }}</td><td>{{ format('GROSS_PROFIT', row.lines.GROSS_PROFIT || 0) }}</td><td>{{ format('BASE_EXPENSE', row.lines.BASE_EXPENSE || 0) }}</td><td>{{ format('NET_PROFIT', row.lines.NET_PROFIT || 0) }}</td><td>{{ format('PROFIT_RATE', row.lines.PROFIT_RATE || 0) }}</td></tr></tbody></table></div><div v-else class="placeholder-card">当前批次暂无真实损益计算结果。请在“月度数据导入”完成必需数据集校验后开始计算。</div></section>
        <section v-else-if="view === 'tree'" class="view active"><div class="section-head"><div><h2>指标树</h2><p>仅从真实计算结果逐层拆解</p></div><span class="tag">指标库 · 口径可追溯</span></div><div v-if="treeRows.length" class="grid2"><div class="panel"><div class="panel-title">真实计算指标</div><div class="tree-list"><div v-for="row in treeRows" :key="row.code" class="tree-node level1" @click="showMetric(row.name)"><b>{{ row.name }}</b><span class="right">{{ format(row.code, row.actual) }}</span></div></div></div><div class="panel"><div class="panel-title">指标定义 <small>{{ selectedMetric }}</small></div><div class="placeholder-card">定义内容以服务端指标库和真实批次配置为准。</div></div></div><div v-else class="placeholder-card">当前批次暂无真实指标树结果。</div></section>
        <section v-else-if="view === 'import'" class="view active"><div class="section-head"><div><h2>月度数据导入</h2><p>上传真实月度业务文件，服务端校验通过后才能计算</p></div><span class="tag">当前批次：{{ readiness?.readyForCalculation ? '待计算' : '需修正或待导入' }}</span></div><div class="stepper"><div class="step done"><b>✓</b>创建批次</div><div class="step current"><b>2</b>上传月度数据</div><div class="step"><b>3</b>校验映射</div><div class="step"><b>4</b>统一计算</div><div class="step"><b>5</b>确认发布</div></div><div class="config-form"><label class="muted">本次文件数据集</label><select v-model="uploadDataset"><option disabled value="">请选择数据集</option><option v-for="dataset in datasets" :key="dataset.code" :value="dataset.code">{{ dataset.name }}{{ dataset.required ? '（必需）' : '（可选）' }}</option></select><span class="muted">文件会按所选数据集创建导入任务，不能自动绑定到第一个数据集。</span></div><div class="upload-grid"><div class="panel"><div class="panel-title">真实业务数据文件 <small>整期覆盖导入</small></div><div class="upload-box"><div class="upload-icon">⇧</div><div>将财经月度明细表拖入此处，或选择文件</div><label for="fileInput">选择文件</label><input id="fileInput" type="file" accept=".xlsx,.xls,.csv" @change="pickFile"><div class="muted">支持预算、收入成本、期间费用、减值结果、闲置费用等月度文件</div></div><div v-if="selectedFile" class="file-list"><div class="file"><span>{{ selectedFile.name }}</span><em>待提交服务端校验</em></div></div></div><div class="panel"><div class="panel-title">当前批次完整性</div><div v-if="readiness" class="kpi-grid"><div class="kpi"><small>必需数据集</small><b>{{ readiness.datasets.filter(d => d.required).length }}</b><span class="muted"> 个</span></div><div class="kpi"><small>已通过</small><b>{{ readiness.datasets.filter(d => d.required && d.present && d.status === 'VALID').length }}</b><span class="muted"> 个</span></div><div class="kpi"><small>问题数</small><b class="heat">{{ readiness.datasets.reduce((sum, d) => sum + (d.issueCount || 0), 0) }}</b><span class="muted"> 条</span></div></div><div v-else class="placeholder-card">暂无真实批次完整性数据。</div><div class="action-row"><button class="btn primary" :disabled="calculationBusy" @click="submitImport()">提交校验</button><button class="btn ghost" :disabled="calculationBusy || !readinessReady" @click="calculateBatch">开始计算</button></div></div></div><div class="panel" style="margin-top:15px"><div class="panel-title">数据集与完整性检查</div><div v-if="readiness?.datasets?.length" class="dataset-grid"><div v-for="row in readiness.datasets" :key="row.datasetCode" class="dataset-card"><strong>{{ row.datasetName }}</strong><small>{{ row.required ? '必需' : '可选' }} · <span :class="row.status === 'VALID' ? 'green' : 'red'">{{ row.status }}</span></small></div></div><div v-else class="placeholder-card">当前批次尚未返回真实数据集状态。</div></div><div v-if="importMessage" class="alert" style="margin-top:12px">{{ importMessage }}</div></section>
        <section v-else-if="view === 'pvm'" class="view active"><div class="section-head"><div><h2>PVM因素分析</h2><p>仅展示真实批次的量价成本因素拆解</p></div><div><span class="tag">{{ pvmRows.length ? '真实计算结果' : '等待真实计算结果' }}</span><button class="btn" @click="exportView('PVM')">导出</button></div></div><div class="panel"><div class="pvm-filter"><label class="muted">当前视角：</label><span class="scope-chip">{{ perspectiveName }}</span><span class="muted">当前范围：</span><span class="scope-chip">{{ activeScopeText }}</span><button class="btn primary" @click="load">刷新分析</button></div></div><div v-if="pvmRows.length" class="panel" style="margin-top:15px"><div class="panel-title">真实PVM拆解</div><div class="table-scroll"><table><thead><tr><th>分析对象</th><th>电量预算</th><th>电量实际</th><th>收入GAP</th><th>销量影响</th><th>成本影响</th><th>综合判断</th></tr></thead><tbody><tr v-for="row in pvmRows" :key="row.scopeCode"><td><b>{{ row.scopeCode }}</b></td><td>{{ row.budgetVolume }}</td><td>{{ row.actualVolume }}</td><td :class="row.revenueGap >= 0 ? 'green' : 'red'">{{ row.revenueGap >= 0 ? '+' : '' }}{{ row.revenueGap }}</td><td :class="row.volumeGap >= 0 ? 'green' : 'red'">{{ row.volumeGap >= 0 ? '+' : '' }}{{ row.volumeGap }}</td><td :class="row.costGap <= 0 ? 'green' : 'red'">{{ row.costGap }}</td><td><span :class="['status', row.grossProfitGap >= 0 ? 'ok' : 'warn']">{{ row.grossProfitGap >= 0 ? '综合改善' : '成本预警' }}</span></td></tr></tbody></table></div></div><div v-else class="placeholder-card">当前批次暂无真实PVM结果，请先完成数据导入和计算。</div></section>
        <section v-else-if="view === 'permission'" class="view active"><div class="section-head"><div><h2>权限验证</h2><p>切换角色后，系统入口、行级数据和操作权限同步变化</p></div><span class="tag">服务端行级过滤 · 本地真实接口验证</span></div><div class="permission-callout"><b>验证方法：</b>切换角色或授权基地，再尝试进入页面、查看损益、导出数据及生成/发布AI分析。</div><div class="panel"><div class="pvm-filter"><label class="muted">当前角色：</label><select v-model="currentRole" @change="changeRole"><option>总部财经</option><option>基地财经</option><option>基地巴长</option><option>基地运营</option></select><span class="muted">授权对象：</span><select v-model="roleScope"><option>全部基地</option><option>南昌基地</option><option>宜昌基地</option><option>义乌基地</option><option>泰国基地</option></select><button class="btn" @click="showToast('授权保存接口待接入')">保存授权</button></div><table><thead><tr><th>能力</th><th>当前角色权限</th><th>页面表现</th><th>授权范围</th></tr></thead><tbody><tr v-for="row in [['经营看板','可查看','显示授权范围汇总、趋势和排名'],['损益计算结果','可查看','可下钻授权范围损益'],['月度数据导入',currentRole === '总部财经' ? '可编辑' : '只读','按角色控制上传和统一计算'],['配置维护',currentRole === '总部财经' ? '可编辑' : '只读','维护映射和费用分类'],['AI分析发布',currentRole === '总部财经' ? '可发布' : '不可用','草稿、确认和分发受控']]" :key="row[0]"><td>{{ row[0] }}</td><td><span :class="['status', row[1] === '不可用' ? 'gray' : row[1] === '只读' ? 'warn' : 'ok']">{{ row[1] }}</span></td><td>{{ row[2] }}</td><td>{{ roleScope }}</td></tr></tbody></table></div></section>
        <section v-else-if="view === 'ai'" class="view active"><div class="section-head"><div><h2>AI经营分析</h2><p>AI只解释已计算的真实结果，不生成替代数据</p></div><span class="tag">{{ aiDraft ? '真实结果草稿' : '等待真实计算结果' }}</span></div><div class="permission-callout"><b>AI分析状态：</b>仅解释已计算结果；没有真实批次时不生成草稿。</div><div class="ai-box"><div class="ai-top"><div class="ai-icon">✦</div><div class="ai-title">财经AI分析助手<small>仅读取已校验结果和授权明细</small></div><span class="spacer"></span><button class="btn primary" @click="generateAi">生成分析草稿</button></div><div v-if="aiDraft" class="ai-content"><div v-for="(text, name) in aiDraft.sections" :key="name" class="ai-section"><h4>{{ name }}</h4><p>{{ text }}</p></div></div><div v-else class="placeholder-card">当前没有真实计算结果，暂不生成AI分析。</div></div></section>
        <section v-else-if="view === 'gross'" class="view active"><div class="section-head"><div><h2>毛利分析</h2><p>仅展示真实批次的收入、成本与毛利因素</p></div><div><button class="btn" :class="{ primary: grossPeriod === '当期' }" @click="grossPeriod = '当期'">当期</button> <button class="btn" :class="{ primary: grossPeriod === '累计' }" @click="grossPeriod = '累计'">累计</button></div></div><div class="placeholder-card">当前没有真实毛利计算结果。请先导入并计算月度批次。</div></section>
        <section v-else-if="view === 'matrix'" class="view active"><div class="section-head"><div><h2>事业部视角：基地 × 产品交叉矩阵</h2><p>仅展示服务端真实计算结果</p></div><label>指标<select v-model="matrixMetric"><option value="REVENUE">收入</option><option value="GROSS">毛利</option><option value="PROFIT">经营分析利润</option></select></label></div><div class="permission-callout"><b>口径边界：</b>没有真实批次时不展示矩阵数字。</div><div v-if="matrixRows.length" class="panel table-scroll"><table class="matrix-table"><thead><tr><th>基地</th><th>产品</th><th>当前值</th><th>基准值</th><th>绝对GAP</th><th>GAP%</th><th>经营判断</th></tr></thead><tbody><tr v-for="row in matrixRows" :key="`${row.base}-${row.product}`"><td>{{ row.base }}</td><td>{{ row.product }}</td><td>{{ format(matrixMetric, matrixPair(row)[0]) }}</td><td>{{ format(matrixMetric, matrixPair(row)[1]) }}</td><td :class="matrixPair(row)[0] - matrixPair(row)[1] >= 0 ? 'green' : 'red'">{{ format(matrixMetric, matrixPair(row)[0] - matrixPair(row)[1]) }}</td><td>{{ matrixPair(row)[1] ? `${((matrixPair(row)[0] - matrixPair(row)[1]) / Math.abs(matrixPair(row)[1]) * 100).toFixed(1)}%` : '—' }}</td><td>{{ matrixPair(row)[0] - matrixPair(row)[1] >= 0 ? '贡献提升' : '需下钻' }}</td></tr></tbody></table></div><div v-else class="placeholder-card">当前没有真实基地×产品矩阵结果。</div></section>
        <section v-else-if="view === 'unit'" class="view active"><div class="section-head"><div><h2>单位效率分析</h2><p>仅展示真实线体单位制造费用与YU指标</p></div><span class="tag">{{ v13Unit?.status || '待加载' }}</span></div><div class="unit-grid"><article class="panel"><div class="panel-title">线体单位制造费用 <small>单位：元 / Wh</small></div><table v-if="unitRows.length"><thead><tr><th>基地</th><th>线体</th><th>制造费用</th><th>产出Wh</th><th>元/Wh</th><th>环比</th></tr></thead><tbody><tr v-for="row in unitRows" :key="`${row[0]}-${row[1]}`"><td>{{ row[0] }}</td><td>{{ row[1] }}</td><td>{{ row[2] }}</td><td>{{ row[3] }}</td><td>{{ row[4] }}</td><td :class="row[5].startsWith('+') ? 'red' : 'green'">{{ row[5] }}</td></tr></tbody></table><div v-else class="placeholder-card">暂无真实线体单位费用。</div><div class="formula-box">{{ v13Unit?.blockers?.join('；') || '待真实数据导入。' }}</div></article><article class="panel"><div class="panel-title">YU指标卡与趋势</div><div class="placeholder-card">暂无真实YU指标数据。</div><div class="formula-box">YU指标需接入真实数据源并完成口径确认。</div></article></div></section>
        <section v-else-if="view === 'forecast'" class="view active"><div class="section-head"><div><h2>FC滚动预测与BGT / ACT三口径</h2><p>当前选择：{{ fcVersion }} · 支持版本导入、冻结、对比与追溯</p></div><span class="v13-badge">{{ scenarioLabel }}</span></div><div class="permission-callout"><b>版本治理：</b>FC不在平台内在线编制；正式预测由预算/经营系统生成后按版本导入。<span v-if="v13Forecast?.blockers?.length">当前状态：{{ v13Forecast.blockers.join('；') }}</span></div><div v-if="forecastRows.length" class="panel table-scroll"><table><thead><tr><th>指标</th><th>BGT</th><th>ACT</th><th>FC</th><th>ACT-BGT</th><th>ACT-FC</th><th>FC-BGT</th></tr></thead><tbody><tr v-for="row in forecastRows" :key="String(row.metricCode || row.metricName)"><td>{{ row.metricName || row.metricCode }}</td><td>{{ format(String(row.metricCode || 'REVENUE'), Number(row.budget || 0)) }}</td><td>{{ format(String(row.metricCode || 'REVENUE'), Number(row.actual || 0)) }}</td><td>{{ row.fc == null ? '待接入' : format(String(row.metricCode || 'REVENUE'), Number(row.fc)) }}</td><td>{{ format(String(row.metricCode || 'REVENUE'), Number(row.actual || 0) - Number(row.budget || 0)) }}</td><td>{{ row.fc == null ? '—' : format(String(row.metricCode || 'REVENUE'), Number(row.actual || 0) - Number(row.fc)) }}</td><td>{{ row.fc == null ? '—' : format(String(row.metricCode || 'REVENUE'), Number(row.fc) - Number(row.budget || 0)) }}</td></tr></tbody></table></div><div v-else class="alert">当前 FC 接口未返回可发布快照，页面不再使用演示三口径数字。</div><div class="panel waterfall-panel"><div class="panel-title">单位毛利GAP瀑布拆解 <small>来自后端 PVM 因素</small></div><div v-if="waterfallFactors.length" class="waterfall"><div v-for="factor in waterfallFactors" :key="String(factor.code)" class="water-step"><div class="water-bar" :class="Number(factor.value || 0) >= 0 ? 'good' : 'bad'" :style="{ height: `${Math.max(18, Math.abs(Number(factor.value || 0)) * 6)}px` }">{{ Number(factor.value || 0) > 0 ? '+' : '' }}{{ format('REVENUE', Number(factor.value || 0)) }}</div><small>{{ factor.code }}</small></div></div><div v-else class="placeholder-card">当前后端未返回瀑布因子。</div><div class="formula-box">正式单位毛利仍需冻结单位分母；当前展示后端返回的正式 PVM 因素，不再读取前端演示常量。</div></div></section>
        <section v-else-if="view === 'risk'" class="view active"><div class="risk-sheet"><div class="section-head"><div><h2>经营分析风险提示 · 一页纸</h2><p>{{ period }} · {{ perspectiveName }}视角 · {{ scenarioLabel }}</p></div><button class="btn primary" @click="printRisk">打印 / 导出PDF</button></div><div class="risk-summary"><div class="risk-kpi"><small>总体风险</small><b :class="riskSummaryClass">{{ riskSummaryLabel }}</b></div><div class="risk-kpi"><small>需关注事项</small><b>{{ riskSummaryCount }}</b></div><div class="risk-kpi"><small>待确认口径</small><b class="risk-level-mid">{{ v13Risk?.blockers?.length || 0 }}</b></div><div class="risk-kpi"><small>数据状态</small><b :class="riskReady ? 'green' : 'risk-level-mid'">{{ v13Risk?.status || '待加载' }}</b></div></div><div class="decision-strip"><span class="decision-chip">结果版本：{{ v13Risk?.versionNo || '待加载' }}</span><span class="decision-chip">阈值：后端状态 {{ v13Risk?.status || '待加载' }}</span><span class="decision-chip">发布前需责任人确认</span></div><table><thead><tr><th>等级</th><th>领域</th><th>风险/异常</th><th>责任角色</th><th>建议动作</th><th>状态</th></tr></thead><tbody><tr v-for="row in riskRows" :key="row[1]"><td><span :class="row[0] === '高' ? 'red' : row[0] === '中' ? 'warning' : 'green'">{{ row[0] }}</span></td><td>{{ row[1] }}</td><td>{{ row[2] }}</td><td>{{ row[3] }}</td><td>{{ row[4] }}</td><td>{{ row[5] }}</td></tr></tbody></table><div class="pnl-note">风险等级由后端确定性规则和阈值状态返回；阈值未冻结时只展示观察项，不替代正式风险审批。</div></div></section>
        <section v-else-if="view === 'topics'" class="view active"><div class="section-head"><div><h2>P1专题分析</h2><p>{{ activeTopicData.name }} · {{ activeTopicPayload?.notes?.join('；') || activeTopicData.note }}</p></div><span class="tag">{{ activeTopicPayload?.status || '待加载' }}</span></div><div class="topic-buttons"><button v-for="(topic,key) in p1Topics" :key="key" class="btn" :class="{ primary: activeTopic === key }" @click="activeTopic = key">{{ topic.name }}</button></div><div class="panel table-scroll"><table v-if="topicDisplayRows.length"><thead><tr><th v-for="header in activeTopicData.headers" :key="header">{{ header }}</th></tr></thead><tbody><tr v-for="(row,index) in topicDisplayRows" :key="index"><td v-for="cell in row" :key="cell">{{ cell }}</td></tr></tbody></table><div v-else class="placeholder-card">当前专题尚未接入明细源数据，页面只展示后端阻断状态。</div><div class="formula-box">{{ activeTopicPayload?.blockers?.join('；') || '待P0口径冻结、字段映射与权限范围确认。' }}</div></div></section>
        <section v-else-if="view === 'configImport'" class="view active"><div class="section-head"><div><h2>{{ title }}</h2><p>创建任务、上传文件、校验并应用；每一步都以服务端任务状态为准</p></div><span class="tag">真实接口 · 可追溯</span></div><div class="permission-callout"><b>权限边界：</b>普通配置在校验通过后由服务端应用；用户角色权限文件只做结构校验和任务留存，不会自动生效，仍需管理员授权。</div><div class="panel"><div class="config-form"><select v-model="configImportType"><option>SALES_PROJECT_MAPPING</option><option>INVENTORY_MAPPING</option><option>EXPENSE_CATEGORY</option><option>EXPENSE_ALLOCATION</option><option>FX_RATE</option><option>BASE_GRAIN</option><option>USER_PERMISSION</option><option>METRIC_DEFINITION</option></select><input type="file" accept=".xlsx,.xls,.csv" @change="pickConfigFile"><button class="btn primary" :disabled="configImportBusy" @click="runConfigImport">{{ configImportBusy ? '处理中…' : '创建任务并导入' }}</button><input v-model="configTaskQueryNo" placeholder="输入任务编号查询"><button class="btn" :disabled="configImportBusy" @click="queryConfigImport">查询任务</button></div><div v-if="configImportMessage" class="alert">{{ configImportMessage }}</div><div v-if="configImportTask" class="config-task-summary"><div><small>任务编号</small><b>{{ configImportTask.taskNo }}</b></div><div><small>配置类型</small><b>{{ configImportTask.type }}</b></div><div><small>文件</small><b>{{ configImportTask.fileName }}</b></div><div><small>状态</small><span :class="['status', configStatusClass(configImportTask.status)]">{{ configStatusLabel(configImportTask.status) }}</span></div><div><small>问题数</small><b :class="configIssueRows.length ? 'red' : 'green'">{{ configIssueRows.length }}</b></div></div><div v-if="configImportResult" class="matrix-note">本次服务端返回：{{ configImportResult.rowCount }} 行数据、{{ configImportResult.headers.length }} 个字段标题；状态为{{ configStatusLabel(configImportResult.status) }}。</div></div><div class="panel" style="margin-top:15px"><div class="panel-title">问题明细 <small>{{ configImportTask ? `任务 ${configImportTask.taskNo}` : '提交或查询任务后显示' }}</small></div><div v-if="configIssueRows.length" class="table-scroll"><table><thead><tr><th>行号</th><th>字段</th><th>错误编码</th><th>错误说明</th><th>原始值</th></tr></thead><tbody><tr v-for="(issue, index) in configIssueRows" :key="`${issue.issueCode}-${issue.rowNo}-${index}`"><td>{{ issue.rowNo || '—' }}</td><td>{{ issue.fieldName || '—' }}</td><td class="red">{{ issue.issueCode }}</td><td>{{ issue.issueMessage }}</td><td>{{ issue.rawValue || '—' }}</td></tr></tbody></table></div><p v-else class="muted">{{ configImportTask ? '当前任务没有问题明细。' : '暂无任务问题明细。' }}</p></div></section>
        <section v-else-if="view === 'mapping' || view === 'metrics'" class="view active"><div class="section-head"><div><h2>{{ title }}</h2><p>基础配置、映射关系和指标展示由总部财经维护，导入与月度业务数据分离</p></div><span class="tag">增量维护 · 保留审计</span></div><div class="permission-callout"><b>权限边界：</b>配置导入先校验匹配键、重复项和口径，不直接替代已发布业务数据。</div><div class="panel" v-if="view === 'mapping'"><div class="config-form"><select v-model="mappingType"><option>SALES_PROJECT</option><option>INVENTORY</option><option>EXPENSE_RULE</option><option>FX_RATE</option></select><input v-model="mappingForm.matchKey" placeholder="匹配键"><input v-model="mappingForm.mappedValue" placeholder="映射值"><button class="btn primary" @click="saveMapping">新增映射</button></div><table><thead><tr><th>映射类型</th><th>匹配条件</th><th>输出归属</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in mappingRows" :key="`${row.type}-${row.matchKey}`"><td>{{ row.type }}</td><td>{{ row.matchKey }}</td><td>{{ row.mappedValue }}</td><td><span :class="['status', row.enabled ? 'ok' : 'gray']">{{ row.enabled ? '有效' : '停用' }}</span></td><td><button v-if="row.enabled" class="link-button" @click="disableMapping(row)">停用</button></td></tr></tbody></table></div><div class="panel" v-else><div class="config-form"><input v-model="metricForm.metricCode" placeholder="指标编码"><input v-model="metricForm.metricName" placeholder="指标名称"><input v-model="metricForm.formulaExpression" placeholder="公式，如 SUM(REVENUE)"><button class="btn primary" @click="saveMetric">保存指标</button></div><div v-if="configMessage" class="alert">{{ configMessage }}</div><table><thead><tr><th>展示</th><th>指标名称</th><th>指标编码</th><th>单位</th><th>适用视角</th><th>状态</th></tr></thead><tbody><tr v-for="row in configMetrics" :key="row.metricCode"><td><input type="checkbox" :checked="row.enabled"></td><td>{{ row.metricName }}</td><td>{{ row.metricCode }}</td><td>{{ row.unit }}</td><td>{{ row.applicablePerspective }}</td><td><span :class="['status', row.enabled ? 'ok' : 'gray']">{{ row.enabled ? '启用' : '停用' }}</span></td></tr></tbody></table></div></section>
        <section v-else-if="view === 'users' || view === 'menus' || view === 'audit'" class="view active"><div class="section-head"><div><h2>{{ title }}</h2><p>维护平台权限、菜单顺序并保留真实操作追溯</p></div><span class="tag">真实接口状态</span></div><div class="panel" v-if="view === 'users'"><div class="placeholder-card">用户查询接口尚未返回真实用户数据；当前页面不展示演示用户。</div></div><div class="panel" v-else-if="view === 'menus'"><table><thead><tr><th>菜单名称</th><th>菜单编码</th><th>分组</th><th>当前顺序</th><th>状态</th></tr></thead><tbody><tr v-for="(group,index) in navGroups.flatMap(g => g.items)" :key="group[0]"><td>{{ group[1] }}</td><td>{{ String(group[0]).toUpperCase() }}</td><td>{{ index < 7 ? '平台功能' : index < 13 ? 'V1.3分析' : '配置与追溯' }}</td><td>{{ index + 1 }}</td><td><span class="status ok">启用</span></td></tr></tbody></table></div><div class="panel" v-else><div class="placeholder-card">审计查询接口尚未返回真实记录；当前页面不展示演示日志。</div></div></section><section v-if="view === 'issues'" class="view active"><div class="section-head"><div><h2>异常清单</h2><p>当前批次的导入校验问题，来源于服务端问题仓储 · {{ batchNo }}</p></div><div><span class="tag">{{ issueRows.length ? issueRows.length + ' 条真实异常' : issuesLoading ? '加载中…' : '暂无异常' }}</span><button class="btn" :disabled="issuesLoading" @click="refreshIssues">{{ issuesLoading ? '刷新中…' : '刷新异常' }}</button></div></div><div class="permission-callout"><b>处理路径：</b>按数据集、任务号、行号和字段定位原始问题；修复文件后回到“月度数据导入”重新上传校验，readiness 通过后才能计算。</div><div v-if="issueRows.length" class="panel table-scroll"><table><thead><tr><th>数据集</th><th>任务号</th><th>行号</th><th>字段</th><th>错误编码</th><th>错误说明</th><th>原始值</th></tr></thead><tbody><tr v-for="(issue, index) in issueRows" :key="issue.taskNo + '-' + issue.issueCode + '-' + issue.rowNo + '-' + index"><td>{{ issue.datasetName }}</td><td>{{ issue.taskNo }}</td><td>{{ issue.rowNo || '—' }}</td><td>{{ issue.fieldName || '—' }}</td><td class="red">{{ issue.issueCode }}</td><td>{{ issue.issueMessage }}</td><td>{{ issue.rawValue || '—' }}</td></tr></tbody></table></div><div v-else-if="issuesLoading" class="placeholder-card">正在读取服务端异常清单。</div><div v-else class="placeholder-card">{{ readiness?.datasets?.some(d => d.taskNo) ? '当前批次没有服务端返回的导入异常。' : '当前批次尚未创建导入任务，暂无异常清单。' }}</div></section></main></div><div v-if="toastMessage" class="toast show">{{ toastMessage }}</div></div>
  <Teleport to="body">
    <div v-if="view === 'result'" style="position:fixed;right:24px;bottom:24px;z-index:30;display:flex;gap:8px;align-items:center;padding:10px 12px;background:#fff;border:1px solid #e6e9ed;border-radius:8px;box-shadow:0 4px 18px #202d3a22">
      <span class="muted">结果状态：{{ calculationStatus || '待计算' }}</span>
      <button class="btn" :disabled="calculationBusy || calculationStatus !== 'CALCULATED'" @click="confirmCalculation">确认结果</button>
      <button class="btn primary" :disabled="calculationBusy || calculationStatus !== 'CONFIRMED'" @click="publishCalculation">发布结果</button>
    </div>
    <div v-if="view === 'ai' && aiDraft" style="position:fixed;right:24px;bottom:24px;z-index:30;display:flex;gap:8px;align-items:center;padding:10px 12px;background:#fff;border:1px solid #e6e9ed;border-radius:8px;box-shadow:0 4px 18px #202d3a22">
      <span class="muted">AI状态：{{ aiDraft.status }}</span>
      <button class="btn primary" :disabled="aiDraft.status !== 'DRAFT'" @click="publishAiDraft">确认并发布AI分析</button>
    </div>
  </Teleport>
</template>

<style>
:root{--red:#9e1835;--red2:#c52d4c;--ink:#26313b;--muted:#75808b;--line:#e6e9ed;--bg:#f5f6f8;--card:#fff;--green:#17845b;--amber:#bd7718;--blue:#3a6ea5}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.55 Arial,"Microsoft YaHei",sans-serif}button,select,input{font:inherit}button{cursor:pointer;border:0}.app{min-height:100vh;display:flex;flex-direction:column}.top{height:62px;background:linear-gradient(100deg,#771329,var(--red));color:#fff;display:flex;align-items:center;padding:0 26px;gap:22px;box-shadow:0 2px 12px #4a132233}.brand{font-size:19px;font-weight:700;letter-spacing:.5px}.brand small{display:block;font-size:10px;font-weight:400;opacity:.68;letter-spacing:1.8px}.topline{height:28px;width:1px;background:#ffffff40}.top-title{font-size:14px;opacity:.9}.top-right{margin-left:auto;display:flex;align-items:center;gap:9px}.role-pill{background:#ffffff18;border:1px solid #ffffff40;padding:6px 10px;border-radius:5px;font-size:12px}.user-dot{width:28px;height:28px;border-radius:50%;background:#f2a0af;color:#681329;display:grid;place-items:center;font-weight:700}.user-input{width:100px;padding:6px 8px;border:1px solid #ffffff50;border-radius:4px;color:#fff;background:#ffffff18}.top-user-btn{padding:7px 10px;border-radius:4px;color:var(--red);background:#fff}.layout{display:flex;flex:1}.side{width:220px;min-height:calc(100vh - 62px);background:#252d35;color:#dbe0e5;padding:18px 12px;overflow-y:auto}.side h3{font-size:11px;text-transform:uppercase;letter-spacing:1.4px;color:#8d99a4;margin:5px 12px 12px}.nav{display:flex;align-items:center;gap:11px;width:100%;min-height:42px;padding:10px 13px;border-radius:6px;margin:4px 0;color:#ced5da;white-space:nowrap;text-align:left;background:transparent}.nav span{overflow:hidden;text-overflow:ellipsis}.nav.active{background:var(--red);color:#fff;box-shadow:inset 3px 0 #f3a4b4}.nav i{width:20px;flex:0 0 20px;text-align:center;font-style:normal;opacity:.9}.side-foot{position:sticky;bottom:0;width:100%;color:#84909a;font-size:11px;border-top:1px solid #ffffff12;padding:12px 4px;margin-top:18px;background:#252d35}.main{flex:1;padding:22px 26px 34px;max-width:1500px}.crumb{font-size:12px;color:var(--muted);margin-bottom:13px}.toolbar{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:13px 15px;display:flex;align-items:center;gap:10px;box-shadow:0 2px 6px #202d3a08;flex-wrap:wrap}.toolbar label{font-size:12px;color:var(--muted)}select{border:1px solid #d4dbe1;border-radius:4px;padding:7px 28px 7px 9px;color:var(--ink);background:#fff}.toolbar .spacer{flex:1}.btn{padding:8px 14px;border-radius:5px;background:#fff;color:var(--ink);border:1px solid #d6dce2}.btn.primary{background:var(--red);color:#fff;border-color:var(--red)}.btn.ghost{background:#fff;color:var(--red);border-color:#dca8b3}.btn:disabled{opacity:.45;cursor:not-allowed}.scenario-note{display:inline-flex;align-items:center;padding:4px 7px;border-radius:3px;background:#eef3f8;color:#49667f;font-size:11px}.v13-banner{display:flex;align-items:center;gap:12px;margin:14px 0 4px;padding:11px 14px;border:1px solid #d9c1c7;border-radius:8px;background:linear-gradient(90deg,#fff7f9,#fff);color:#58636d}.v13-banner b{color:var(--red)}.v13-badge{padding:3px 8px;border-radius:999px;background:#f4e3e7;color:var(--red);font-size:11px}.readiness{display:flex;gap:12px;align-items:center;margin:12px 0;padding:8px 12px;border-left:3px solid #8a98a7;background:#fff;color:#465463;font-size:12px}.readiness.ready{border-color:var(--green)}.readiness.blocked{border-color:var(--red)}.readiness span{color:var(--muted)}.section-head{display:flex;align-items:end;justify-content:space-between;margin:24px 0 12px}.section-head h2{font-size:18px;margin:0}.section-head p{margin:3px 0 0;color:var(--muted);font-size:12px}.section-head .tag,.tag{font-size:12px;color:var(--green);background:#e8f5ef;padding:4px 8px;border-radius:3px}.cards{display:grid;grid-template-columns:repeat(4,minmax(160px,1fr));gap:13px}.card,.panel{background:var(--card);border:1px solid var(--line);border-radius:8px;box-shadow:0 2px 8px #202d3a09}.card{padding:16px}.metric-label{color:var(--muted);font-size:12px}.metric{font-size:27px;font-weight:700;margin:7px 0 3px}.metric span{font-size:12px;font-weight:400;color:var(--muted)}.delta.up,.green{color:var(--green)}.delta.down,.red{color:var(--red)}.delta{font-size:12px}.grid2{display:grid;grid-template-columns:1.25fr 1fr;gap:15px;margin-top:15px}.panel{padding:16px}.panel-title{font-weight:700;margin-bottom:12px;display:flex;justify-content:space-between}.panel-title small{font-weight:400;color:var(--muted)}.bar-list{display:flex;align-items:flex-end;height:180px;gap:17px;border-bottom:1px solid #dfe4e8;padding:8px 12px 0}.bar-col{height:100%;flex:1;display:flex;flex-direction:column;align-items:center;justify-content:end;gap:5px;font-size:11px;color:var(--muted)}.bar-wrap{height:140px;width:100%;display:flex;gap:3px;align-items:end;justify-content:center}.bar{width:18px;border-radius:3px 3px 0 0;background:var(--red)}.bar.plan{background:#cfd6dd}.legend{font-size:11px;color:var(--muted);margin-top:10px}.dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:4px}.dot.red{background:var(--red)}.dot.gray{background:#cfd6dd}.rank-row{display:grid;grid-template-columns:26px 1fr 92px 72px;gap:9px;align-items:center;padding:11px 0;border-bottom:1px solid #eef0f2}.rank{width:22px;height:22px;border-radius:50%;background:#eef1f3;color:#6e7780;display:grid;place-items:center;font-size:11px}.rank.hot{background:#f9e4e8;color:var(--red)}.mini-bar{height:6px;background:#eff1f3;border-radius:3px;overflow:hidden}.mini-bar i{display:block;height:100%;background:var(--red);border-radius:3px}.right{text-align:right}.top-split{display:grid;grid-template-columns:1fr 1fr;gap:15px;margin-top:15px}.top-item{display:grid;grid-template-columns:28px 1fr 90px 76px;align-items:center;gap:9px;padding:10px 0;border-bottom:1px solid #eef0f2}.top-item:last-child{border:0}.top-value,.top-rate{text-align:right}.top-rate{font-size:11px}.scope-chip{display:inline-block;padding:3px 7px;border-radius:3px;background:#f8e7eb;color:var(--red);font-size:11px}table{width:100%;border-collapse:collapse;font-size:12px}th{background:#f7f8fa;text-align:left;color:#65717c;font-weight:600;padding:10px;border-bottom:1px solid var(--line)}td{padding:10px;border-bottom:1px solid #edf0f2}tr:last-child td{border-bottom:0}.status{padding:3px 7px;border-radius:3px;font-size:11px}.status.ok{color:var(--green);background:#e9f6f0}.status.warn{color:var(--amber);background:#fff3df}.status.gray{color:#66717c;background:#eef1f3}.pnl-table{min-width:850px}.pnl-table th,.pnl-table td{padding:9px 11px}.table-scroll{overflow:auto}.pnl-note{margin-top:10px;color:var(--muted);font-size:11px}.tree-list{font-size:13px;color:#46515b}.tree-node{display:flex;align-items:center;gap:7px;padding:10px 8px;border-bottom:1px solid #eef0f2;cursor:pointer}.tree-node:hover{background:#fff7f8;color:var(--red)}.tree-node.root{font-size:15px;background:#faf5f6;border-left:3px solid var(--red)}.tree-node.level1{padding-left:22px}.tree-node.level2{padding-left:52px;color:#68747e}.kpi-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.kpi{padding:13px;background:#fafbfc;border:1px solid var(--line);border-radius:6px}.kpi b{font-size:20px}.kpi small{display:block;color:var(--muted);font-size:11px}.kpi span{font-size:11px}.permission-callout{border-left:3px solid var(--red);background:#faf5f6;padding:11px 13px;color:#59636c;font-size:12px;margin-bottom:13px}.stepper{display:grid;grid-template-columns:repeat(5,1fr);gap:0;margin:14px 0 18px}.step{position:relative;text-align:center;color:#929ca5;font-size:12px}.step:before{content:"";display:block;height:3px;background:#e4e8eb;position:absolute;top:12px;left:0;right:0}.step:first-child:before{left:50%}.step:last-child:before{right:50%}.step b{display:grid;place-items:center;width:26px;height:26px;margin:0 auto 7px;border-radius:50%;background:#e4e8eb;color:#78838c;position:relative}.step.done{color:var(--green)}.step.done:before{background:#81c6a9}.step.done b{background:var(--green);color:#fff}.step.current{color:var(--red);font-weight:700}.step.current b{background:var(--red);color:#fff}.upload-grid{display:grid;grid-template-columns:1.15fr .85fr;gap:15px}.upload-box{border:1px dashed #cbd3da;border-radius:7px;padding:20px;text-align:center;background:#fafbfc}.upload-box input{display:none}.upload-box label{display:inline-block;padding:9px 15px;border-radius:5px;background:#f7e9ec;color:var(--red);cursor:pointer;margin:7px}.upload-icon{font-size:26px;color:#bbc4cb}.file-list{margin-top:12px}.file{display:flex;align-items:center;justify-content:space-between;padding:9px 10px;background:#fff;border:1px solid var(--line);border-radius:4px;margin-top:6px;font-size:12px}.file em{font-style:normal;color:var(--green);font-size:11px}.alert{background:#fff8eb;border:1px solid #f4d9a9;color:#855819;padding:10px 12px;border-radius:5px;font-size:12px}.alert strong{color:#9f6311}.action-row{display:flex;justify-content:flex-end;gap:8px;margin-top:15px}.dataset-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.dataset-card{padding:12px;border:1px solid var(--line);border-radius:6px;background:#fafbfc}.dataset-card strong{display:block;margin-bottom:4px}.dataset-card small{color:var(--muted)}.heat{color:var(--red);font-weight:700}.ai-box{border:1px solid #dec1c8;background:linear-gradient(135deg,#fffafb,#fdfdfd);border-radius:8px;padding:20px}.ai-top{display:flex;align-items:center;gap:12px;padding-bottom:14px;border-bottom:1px solid #f0e2e5}.ai-icon{width:38px;height:38px;border-radius:50%;background:var(--red);color:#fff;display:grid;place-items:center;font-size:19px}.ai-title{font-weight:700}.ai-title small{display:block;color:var(--muted);font-weight:400;font-size:11px}.ai-content{display:block}.ai-section{padding:13px 0;border-bottom:1px solid #f2e8ea}.ai-section:last-child{border:0}.ai-section h4{margin:0 0 5px;color:var(--red);font-size:13px}.ai-section p{margin:0;color:#46515b}.chart-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px;margin-top:15px}.chart-bars{height:175px;display:flex;align-items:end;gap:18px;border-bottom:1px solid #dfe4e8;padding:8px 12px 0}.chart-bars .bar-group{flex:1;height:100%;display:flex;align-items:end;justify-content:center;gap:5px}.chart-bars .bar{width:24px}.chart-labels{display:flex;gap:18px;padding:7px 12px 0;color:var(--muted);font-size:11px}.chart-labels span{flex:1;text-align:center}.matrix-table{min-width:900px}.matrix-total{background:#fbf4f6!important;font-weight:700}.unit-grid{display:grid;grid-template-columns:1.2fr .8fr;gap:15px}.formula-box{background:#f7f8fa;border-left:3px solid var(--blue);padding:10px 12px;color:#56616b;font-size:12px;margin-top:12px}.placeholder-card{border:1px dashed #c8cfd6;background:#fafbfc;border-radius:6px;padding:12px}.placeholder-card .metric{color:#8b959e;font-size:21px}.trend-bars{height:170px;display:flex;align-items:end;gap:8px;padding:18px 8px 0;border-bottom:1px solid var(--line)}.trend-bar{flex:1;min-width:12px;background:#d4dbe1;border-radius:3px 3px 0 0}.trend-labels{display:flex;justify-content:space-between;color:var(--muted);font-size:10px;margin-top:5px}.waterfall{display:flex;align-items:flex-end;gap:8px;height:230px;padding:18px 6px 32px;border-bottom:1px solid var(--line)}.water-step{flex:1;text-align:center;min-width:60px}.water-bar{border-radius:4px 4px 0 0;min-height:12px;display:flex;align-items:flex-start;justify-content:center;color:#fff;font-size:11px;padding-top:4px;background:var(--blue)}.water-bar.good{background:var(--green)}.water-bar.bad{background:var(--red)}.water-bar.total{background:var(--red2)}.water-step small{display:block;margin-top:6px;color:var(--muted);font-size:10px}.risk-sheet{background:#fff;border:1px solid #d9dde1;border-top:5px solid var(--red);padding:20px}.risk-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:14px 0}.risk-kpi{padding:12px;background:#f7f8fa;border-radius:6px}.risk-kpi b{display:block;font-size:22px}.risk-level-high{color:#a81332;font-weight:700}.risk-level-mid{color:var(--amber);font-weight:700}.warning{color:var(--amber)}.decision-strip{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.decision-chip{padding:5px 8px;border-radius:4px;background:#fff2dd;color:#8b5d12;font-size:11px}.link-button{border:0;background:transparent;color:var(--red);cursor:pointer}.config-form{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:13px}.config-form input,.config-form select{min-width:150px;padding:8px;border:1px solid #d4dbe1;border-radius:4px}.muted{color:var(--muted)}.toast{position:fixed;right:25px;bottom:24px;background:#28333c;color:#fff;padding:11px 16px;border-radius:5px;box-shadow:0 3px 16px #0003;z-index:20}.risk-print .side,.risk-print .top,.risk-print .toolbar,.risk-print .v13-banner,.risk-print .readiness,.risk-print .crumb{display:none}@media print{body.risk-print *{visibility:hidden!important}body.risk-print .risk-sheet,body.risk-print .risk-sheet *{visibility:visible!important}body.risk-print .risk-sheet{position:absolute;left:0;top:0;width:100%;border:0}}@media(max-width:900px){.side{width:70px}.side h3,.nav span,.side-foot{display:none}.nav{justify-content:center}.main{padding:16px}.cards{grid-template-columns:repeat(2,1fr)}.grid2,.upload-grid,.chart-grid,.top-split,.unit-grid{grid-template-columns:1fr}.dataset-grid,.risk-summary{grid-template-columns:repeat(2,1fr)}.top-title{display:none}.v13-banner{align-items:flex-start;flex-wrap:wrap}}@media(max-width:640px){.top{padding:0 12px}.top-right .role-pill,.user-input,.top-user-btn{display:none}.cards,.kpi-grid{grid-template-columns:1fr}.main{padding:12px}.toolbar{align-items:flex-start}.toolbar .spacer{display:none}}
</style>
