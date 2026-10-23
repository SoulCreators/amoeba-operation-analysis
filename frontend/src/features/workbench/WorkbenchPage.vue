<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, type AiDraft, type BatchReadiness, type ConfigurationImportIssue, type ConfigurationImportTask, type ConfigurationImportValidationResult, type DataQualityReport, type DatasetDefinition, type ExportDownloadResponse, type ExportResultStatus, type ExportTaskResponse, type ExportTaskStatus, type ImportIssue, type MappingEntry, type MetricDefinition, type MetricResult, type Perspective, type PnlResult, type PvmResult, type V13AnalysisPayload } from '../../shared/api'

import { navGroups, viewNames, type View } from './navigation'
import { analysisBlocker, finiteAmount, forecastBlocker, forecastRowsFrom, matrixRowsFrom, type MatrixRow, type Scenario } from './analysis-presentation'
type BatchImportIssue = ImportIssue & { datasetCode: string; datasetName: string; taskNo: string }
type ExportTask = ExportTaskResponse
type ExportDownload = ExportDownloadResponse
const DEFAULT_LOCAL_USER = 'local-finance'
function normalizeUserId(value?: string | null) { const userId = value?.trim(); return !userId || userId === 'demo-finance' ? DEFAULT_LOCAL_USER : userId }
const localUser = normalizeUserId(localStorage.getItem('amoeba.user')); localStorage.setItem('amoeba.user', localUser)
const view = ref<View>('overview'); const period = ref('2026-08'); const periodMode = ref('current'); const perspective = ref<Perspective>('BASE'); const scope = ref('ALL'); const batchNo = ref(new URLSearchParams(window.location.search).get('batch') || 'BATCH-202608-BASE-001'); const currentUser = ref(localUser); const currentRole = ref('总部财经'); const currentScope = ref('全部基地'); const source = ref<'api' | 'empty'>('empty'); const loading = ref(false); const error = ref(''); const toastMessage = ref(''); const scenario = ref<Scenario>('ACT_BGT'); const fcVersion = ref(''); const matrixMetric = ref('REVENUE'); const activeTopic = ref('inventory'); const selectedMetric = ref('营业利润')
const metrics = ref<MetricResult[]>([]); const results = ref<PnlResult[]>([]); const pvmRows = ref<PvmResult[]>([]); const treeRows = ref<MetricResult[]>([]); const aiDraft = ref<AiDraft | null>(null); const readiness = ref<BatchReadiness | null>(null); const quality = ref<DataQualityReport | null>(null); const datasets = ref<DatasetDefinition[]>([]); const uploadDataset = ref(''); const selectedFile = ref<File | null>(null); const importMessage = ref(''); const calculationStatus = ref(''); const resultVersion = ref(''); const calculationBusy = ref(false); const exportBusy = ref(false); const issueRows = ref<BatchImportIssue[]>([]); const issuesLoading = ref(false); const configMetrics = ref<MetricDefinition[]>([]); const mappingRows = ref<MappingEntry[]>([]); const mappingType = ref('SALES_PROJECT'); const mappingForm = ref({ type: 'SALES_PROJECT', matchKey: '', mappedValue: '', enabled: true }); const metricForm = ref({ metricCode: '', metricName: '', applicablePerspective: 'ALL', unit: 'CNY', formulaExpression: '', displayOrder: 100, enabled: true, aiSummaryTemplate: '' }); const configMessage = ref(''); const configImportType = ref('SALES_PROJECT_MAPPING'); const configImportFile = ref<File | null>(null); const configImportMessage = ref(''); const roleScope = ref('全部基地')
const configImportTask = ref<ConfigurationImportTask | null>(null); const configTaskQueryNo = ref(''); const configIssueRows = ref<ConfigurationImportIssue[]>([]); const configImportResult = ref<ConfigurationImportValidationResult | null>(null); const configImportBusy = ref(false)
const v13Overview = ref<V13AnalysisPayload | null>(null); const v13Matrix = ref<V13AnalysisPayload | null>(null); const v13Unit = ref<V13AnalysisPayload | null>(null); const v13Forecast = ref<V13AnalysisPayload | null>(null); const v13Risk = ref<V13AnalysisPayload | null>(null); const v13Waterfall = ref<V13AnalysisPayload | null>(null); const v13TopicPayloads = ref<Record<string, V13AnalysisPayload>>({})
const forecastRows = ref<Record<string, unknown>[]>([]); const waterfallFactors = ref<Record<string, unknown>[]>([])
const title = computed(() => viewNames[view.value]); const perspectiveName = computed(() => ({ BASE: '基地', PRODUCT_LINE: '产品线', BUSINESS_UNIT: '事业部' }[perspective.value])); const scenarioLabel = computed(() => ({ ACT_BGT: 'ACT vs BGT', ACT_FC: 'ACT vs FC', FC_BGT: 'FC vs BGT' }[scenario.value])); const activeScopeText = computed(() => currentScope.value === '全部基地' || scope.value === 'ALL' ? '全域' : currentScope.value); const activeTopicData = computed(() => p1Topics[activeTopic.value]); const visiblePnl = computed(() => scope.value === 'ALL' ? results.value : results.value.filter(row => row.scopeCode === scope.value)); const readinessReady = computed(() => readiness.value?.readyForCalculation === true)
const activeTopicPayload = computed(() => v13TopicPayloads.value[activeTopic.value]); const topicDisplayRows = computed(() => (activeTopicPayload.value?.rows || []).map((row) => Object.values(row).map((value) => String(value ?? '—'))))
const dashboardCards = ref([
  { key: 'revenue', label: '营业收入', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'gross', label: '毛利', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
  { key: 'operating', label: '净利润', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
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
const overviewMetrics = ref<MetricResult[]>([])
const analysisNotice = ref('')
const batchPeriod = ref('')
const matrixRows = ref<MatrixRow[]>([])
const unitRows = ref<string[][]>([])
const riskRows = ref<string[][]>([])
const riskReady = computed(() => Boolean(v13Risk.value && v13Risk.value.status === 'CALCULATED'))
const riskDataComplete = computed(() => (riskReady.value || v13Risk.value?.status === 'PARTIAL_PENDING_THRESHOLD') && Array.isArray(v13Risk.value?.risks))
const riskHasRows = computed(() => riskDataComplete.value && Boolean(v13Risk.value?.risks?.length))
const riskSummaryLabel = computed(() => !riskReady.value ? (v13Risk.value?.status === 'PARTIAL_PENDING_THRESHOLD' ? '待确认' : v13Risk.value ? '待计算' : '待加载') : !riskDataComplete.value ? '待确认' : riskHasRows.value ? '需关注' : '未发现负向偏差')
const riskSummaryClass = computed(() => !riskReady.value || !riskDataComplete.value ? 'risk-level-mid' : riskHasRows.value ? 'risk-level-high' : 'green')
const riskSummaryCount = computed(() => riskDataComplete.value ? (v13Risk.value?.risks?.length || 0) : '—')
let loadRequestId = 0
let v13LoadRequestId = 0
function resetOverviewPresentation() {
  overviewMetrics.value = []
  dashboardCards.value = [
    { key: 'revenue', label: '营业收入', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'gross', label: '毛利', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
    { key: 'operating', label: '净利润', value: '—', unit: '', delta: '暂无真实计算结果', good: false },
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
  analysisNotice.value = ''
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
function format(code: string, value: number) { if (code.includes('RATE')) return `${(value * 100).toFixed(2)}%`; return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value) }
function matrixPair(row: MatrixRow) { return [row.actual, row.budget] }

function setUser() { const normalizedUser = normalizeUserId(currentUser.value); localStorage.setItem('amoeba.user', normalizedUser); currentUser.value = normalizedUser; showToast('用户范围已切换') }; function showToast(message: string) { toastMessage.value = message; window.setTimeout(() => { if (toastMessage.value === message) toastMessage.value = '' }, 2600) }; function go(next: View) { view.value = next; error.value = ''; if (next === 'overview' || next === 'import' || next === 'issues' || next === 'configImport' || next === 'mapping' || next === 'metrics' || next === 'result' || next === 'pvm' || next === 'tree' || next === 'ai') void load() }

function exportStatus(status: string | undefined): ExportTaskStatus | null {
  const normalized = status?.toUpperCase()
  return normalized === 'QUEUED' || normalized === 'RUNNING' || normalized === 'SUCCEEDED' || normalized === 'FAILED' || normalized === 'EXPIRED'
    ? normalized
    : null
}

function exportRequestKey(name: string) {
  return ['FE-EXPORT-001', name, batchNo.value, resultVersion.value, perspective.value, scope.value || 'ALL'].join(':')
}

async function pollExportTask(task: ExportTask) {
  let currentTask = task
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const status = exportStatus(currentTask.status)
    if (status !== 'QUEUED' && status !== 'RUNNING') return currentTask
    await new Promise((resolve) => window.setTimeout(resolve, 500))
    currentTask = await api.exportStatus(task.taskId)
  }
  return currentTask
}

function decodeExportContent(content: ExportDownload['content']) {
  if (typeof content !== 'string' || !content) return null
  try {
    const binary = window.atob(content)
    if (!binary.length) return null
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index)
    return bytes
  } catch {
    return null
  }
}

function triggerExportDownload(file: ExportDownload, bytes: Uint8Array) {
  const blob = new Blob([bytes], { type: file.contentType || 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = file.fileName || `${batchNo.value}-${resultVersion.value}.csv`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

function exportStatusMessage(name: string, task: ExportTask, suffix = '') {
  const status = exportStatus(task.status) || task.status || 'UNKNOWN'
  const taskLabel = `taskId=${task.taskId}，status=${status}`
  if (status === 'QUEUED') return `${name}导出任务已创建：${taskLabel}（排队中，暂未下载）${suffix}`
  if (status === 'RUNNING') return `${name}导出任务已创建：${taskLabel}（生成中，暂未下载）${suffix}`
  if (status === 'FAILED') return `${name}导出任务失败：${taskLabel}${task.errorMessage ? `，${task.errorMessage}` : ''}${suffix}`
  if (status === 'EXPIRED') return `${name}导出任务已过期：${taskLabel}，未下载${suffix}`
  return `${name}导出任务：${taskLabel}${suffix}`
}

async function exportView(name: string) {
  if (exportBusy.value) return
  exportBusy.value = true
  try {
    const result = await api.result(batchNo.value)
    resultVersion.value = result.versionNo || ''
    calculationStatus.value = result.status
    const resultStatus = String(result.status || '').toUpperCase()
    if (!resultVersion.value || !['CALCULATED', 'CONFIRMED', 'PUBLISHED'].includes(resultStatus)) {
      throw new Error('当前批次没有可导出的结果版本或结果状态')
    }
    const requestedScopes = scope.value && scope.value !== 'ALL' ? [scope.value] : []
    const created = await api.createExport({
      requestKey: exportRequestKey(name),
      batchNo: batchNo.value,
      resultVersion: resultVersion.value,
      perspective: perspective.value,
      format: 'CSV',
      resultStatus: resultStatus as ExportResultStatus,
      requestedScopes,
    })
    if (!created?.taskId) throw new Error('服务端未返回导出 taskId')
    showToast(exportStatusMessage(name, created))
    const task = await pollExportTask(created)
    const status = exportStatus(task.status)
    if (status !== 'SUCCEEDED') {
      showToast(exportStatusMessage(name, task, status === 'QUEUED' || status === 'RUNNING' ? '，仍在处理中，未下载' : ''))
      return
    }
    const file = await api.downloadExport(task.taskId)
    const bytes = decodeExportContent(file?.content)
    if (!bytes || !bytes.length || (file.contentLength !== undefined && Number(file.contentLength) <= 0)) {
      showToast(exportStatusMessage(name, task, '，下载接口未返回有效文件内容，未触发下载'))
      return
    }
    triggerExportDownload(file, bytes)
    showToast(exportStatusMessage(name, task, '，文件已下载'))
  } catch (e) {
    showToast(`${name}导出失败：${e instanceof Error ? e.message : '请求失败'}，未触发下载`)
  } finally {
    exportBusy.value = false
  }
}

type BatchContext = { batchNo: string; period: string; perspective: Perspective }
const pendingBatchLoads = new Map<string, Promise<BatchContext>>()
async function ensureBatch() {
  const requestedBatch = batchNo.value
  const requestedPeriod = period.value
  const requestedPerspective = perspective.value
  // Initialization and navigation can overlap before a new batch exists.
  // Share that request instead of creating the same batch twice.
  let pending = pendingBatchLoads.get(requestedBatch)
  if (!pending) {
    pending = (async () => {
      try {
        return await api.batch(requestedBatch)
      } catch (e) {
        const message = e instanceof Error ? e.message : ''
        if (!message.includes('批次不存在')) throw e
        await api.createBatch({ batchNo: requestedBatch, period: requestedPeriod, perspective: requestedPerspective })
        return { batchNo: requestedBatch, period: requestedPeriod, perspective: requestedPerspective }
      }
    })()
    pendingBatchLoads.set(requestedBatch, pending)
  }
  try {
    const batch = await pending
    if (requestedBatch === batchNo.value) batchPeriod.value = batch.period
  } finally {
    if (pendingBatchLoads.get(requestedBatch) === pending) pendingBatchLoads.delete(requestedBatch)
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
  batchPeriod.value = ''
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
      const result = periodMode.value === 'ytd'
        ? await api.cumulative(batchNo.value)
        : await api.result(batchNo.value)
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

async function submitWorkbookImport() {
  if (!selectedFile.value) {
    importMessage.value = '请选择整本财务样表工作簿。'
    return
  }
  try {
    await ensureBatch()
    const tasks = await api.importWorkbook(batchNo.value, selectedFile.value)
    importMessage.value = `整本导入完成：已从 ${selectedFile.value.name} 识别并校验 ${tasks.length} 个数据集；请刷新批次完整性，满足门禁后点击“开始计算”。`
    await load()
  } catch (e) {
    importMessage.value = `整本导入失败：${e instanceof Error ? e.message : '请求失败'}`
  }
}

async function saveMetric() { if (!metricForm.value.metricCode || !metricForm.value.metricName || !metricForm.value.formulaExpression) { configMessage.value = '指标编码、名称和公式不能为空。'; return } try { const saved = await api.upsertMetric(metricForm.value); configMetrics.value = [...configMetrics.value.filter(r => r.metricCode !== saved.metricCode), saved]; configMessage.value = `指标 ${saved.metricName} 已保存。` } catch (e) { configMessage.value = `指标保存失败：${e instanceof Error ? e.message : '请求失败'}` } }

async function saveMapping() { if (!mappingForm.value.matchKey || !mappingForm.value.mappedValue) { configMessage.value = '匹配键和值不能为空。'; return } try { const saved = await api.upsertMapping(mappingForm.value); mappingRows.value = [...mappingRows.value.filter(r => !(r.type === saved.type && r.matchKey === saved.matchKey)), saved] } catch (e) { configMessage.value = `映射保存失败：${e instanceof Error ? e.message : '请求失败'}` } }

async function disableMapping(row: MappingEntry) { try { await api.disableMapping({ type: row.type, matchKey: row.matchKey }); row.enabled = false; showToast(`映射 ${row.matchKey} 已停用`) } catch (e) { configMessage.value = `停用映射失败：${e instanceof Error ? e.message : '请求失败'}` } }

async function uploadConfig() { if (!configImportFile.value) { configImportMessage.value = '请选择基础配置文件。'; return }; await runConfigImport() }

async function refreshAnalysis() {
  // Invalidate in-flight analysis before waiting for batch metadata.
  ++v13LoadRequestId
  resetV13State()
  const metadataRequestId = loadRequestId + 1
  await load()
  if (metadataRequestId === loadRequestId && source.value === 'api') await loadV13()
}
function changeRole() { currentScope.value = currentRole.value === '总部财经' ? '全部基地' : currentRole.value === '基地运营' ? '泰国基地' : '南昌基地'; roleScope.value = currentScope.value; showToast(`已切换为${currentRole.value}`) }; function printRisk() { document.body.classList.add('risk-print'); window.print(); window.setTimeout(() => document.body.classList.remove('risk-print'), 500) }; function showMetric(name: string) { selectedMetric.value = name }; function pickFile(e: Event) { selectedFile.value = (e.target as HTMLInputElement).files?.[0] || null }; function pickConfigFile(e: Event) { configImportFile.value = (e.target as HTMLInputElement).files?.[0] || null }; onMounted(load)
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
  const rows = payload?.metrics || []
  if (rows.some((row) => ['actual', 'budget', 'gap'].some((key) => finiteAmount(row[key]) === null))) {
    analysisNotice.value = '经营指标字段不完整，已停止展示。'
    return []
  }
  return rows.map((row) => ({
    code: String(row.metricCode || ''),
    name: String(row.metricName || row.metricCode || ''),
    actual: Number(row.actual || 0),
    budget: Number(row.budget || 0),
    gap: Number(row.gap),
    gapRate: finiteAmount(row.gapRate),
    aiSummary: typeof row.aiSummary === 'string' ? row.aiSummary : undefined,
  }))
}

function applyOverviewPayload(payload: V13AnalysisPayload) {
  resetOverviewPresentation()
  const rows = metricRows(payload)
  overviewMetrics.value = rows
  const byCode = new Map(rows.map((row) => [row.code, row]))
  const cardSpecs = [
    ['revenue', 'REVENUE', '营业收入'],
    ['gross', 'GROSS_PROFIT', '毛利'],
    ['operating', 'NET_PROFIT', '净利润'],
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
    const gap = finiteAmount(row.gap)
    const rateValue = finiteAmount(row.gapRate)
    if (gap === null) return [String(row.scopeCode || row.scope || '—'), '—', '—']
    const rate = rateValue === null ? '—' : `${(rateValue * 100).toFixed(1)}%`
    return [String(row.scopeCode || row.scope || '—'), format(String(row.metricCode || 'GROSS_PROFIT'), gap), rate]
  }
  topFavorable.value = favorable.slice(0, 3).map(toTopRow)
  topAdverse.value = adverse.slice(0, 3).map(toTopRow)
}

function applyMatrixPayload(payload: V13AnalysisPayload) {
  matrixRows.value = matrixRowsFrom(payload)
  if (payload.rows?.length && !matrixRows.value.length) analysisNotice.value = '矩阵维度或金额字段不完整，已停止展示。'
}

function firstPresent(row: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = row[key]
    if (value !== null && value !== undefined && value !== '') return value
  }
  return null
}

function formatUnitValue(code: string, value: unknown) {
  if (value === null || value === undefined || value === '') return '—'
  const number = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(number) ? format(code, number) : '—'
}

function applyUnitPayload(payload: V13AnalysisPayload) {
  if (payload.status !== 'CALCULATED') {
    unitRows.value = []
    return
  }
  if (!payload.rows?.length) {
    unitRows.value = []
    return
  }
  unitRows.value = payload.rows.map((row) => {
    const scopeCode = firstPresent(row, ['scopeCode', 'baseCode', 'base'])
    const manufacturingCost = firstPresent(row, ['actualManufacturingCost', 'manufacturingCost'])
    const outputWh = firstPresent(row, ['actualOutputWh', 'outputWh'])
    const actualPerWh = firstPresent(row, ['actualPerWh', 'unitCost'])
    const momGap = row.momGap
    const momGapNumber = momGap === null || momGap === undefined || momGap === '' ? null : Number(momGap)
    return [
      String(scopeCode ?? '—'),
      String(firstPresent(row, ['lineCode', 'line']) ?? '—'),
      formatUnitValue('MANUFACTURING_COST', manufacturingCost),
      formatUnitValue('OUTPUT_WH', outputWh),
      formatUnitValue('UNIT_COST', actualPerWh),
      momGapNumber !== null && Number.isFinite(momGapNumber)
        ? `${momGapNumber >= 0 ? '+' : ''}${format('UNIT_COST', momGapNumber)}`
        : '—',
    ]
  })
}

function applyRiskPayload(payload: V13AnalysisPayload) {
  if (!['CALCULATED', 'PARTIAL_PENDING_THRESHOLD'].includes(payload.status) || !Array.isArray(payload.risks)) {
    riskRows.value = []
    return
  }
  riskRows.value = payload.risks.map((row) => [
    String(row.level === 'OBSERVE' ? '观察' : row.level || '观察'), String(row.metricName || row.metricCode || '经营指标'),
    String(row.message || '—'), String(row.ownerRole || '待确认'), String(row.action || '结合业务原因进一步核查'),
    String(row.thresholdStatus || '待确认'),
  ])
}

function applyForecastPayload(payload: V13AnalysisPayload) {
  forecastRows.value = forecastRowsFrom(payload, scenario.value)
  if (payload.rows?.length && !forecastRows.value.length) analysisNotice.value = '预测行缺少当前场景的服务端差额或上下文字段，暂不展示。'
}

function applyWaterfallPayload(payload: V13AnalysisPayload) {
  waterfallFactors.value = payload.status === 'CALCULATED' ? payload.factors || [] : []
}

async function loadV13() {
  const requestId = ++v13LoadRequestId
  resetV13State()
  if (!['overview', 'gross', 'matrix', 'unit', 'forecast', 'risk', 'topics'].includes(view.value)) return
  try {
    if (view.value === 'overview' || view.value === 'gross') {
      const payload = await api.v13Overview(batchNo.value, 'ACT_BGT')
      if (requestId !== v13LoadRequestId) return
      v13Overview.value = payload
      analysisNotice.value = analysisBlocker(payload, batchNo.value, perspective.value, 'ACT_BGT')
      if (analysisNotice.value) return
      applyOverviewPayload(v13Overview.value)
    } else if (view.value === 'matrix') {
      const payload = await api.v13Matrix(batchNo.value, matrixMetric.value)
      if (requestId !== v13LoadRequestId) return
      v13Matrix.value = payload
      analysisNotice.value = analysisBlocker(payload, batchNo.value, perspective.value, 'ACT_BGT')
      if (!analysisNotice.value && payload.metricCode !== matrixMetric.value) analysisNotice.value = '矩阵指标与当前选择不一致，已停止展示。'
      if (analysisNotice.value) return
      applyMatrixPayload(v13Matrix.value)
    } else if (view.value === 'unit') {
      const payload = await api.v13UnitEfficiency(batchNo.value)
      if (requestId !== v13LoadRequestId) return
      v13Unit.value = payload
      analysisNotice.value = analysisBlocker(payload, batchNo.value, perspective.value, 'ACT_BGT')
      if (payload.status !== 'CALCULATED' && payload.blockers?.length) analysisNotice.value = payload.blockers.join('；')
      applyUnitPayload(v13Unit.value)
    } else if (view.value === 'forecast') {
      const payload = await api.v13Forecast(batchNo.value, scenario.value, fcVersion.value.trim() || undefined)
      if (requestId !== v13LoadRequestId) return
      v13Forecast.value = payload
      analysisNotice.value = analysisBlocker(payload, batchNo.value, perspective.value, scenario.value) || forecastBlocker(payload)
      if (!analysisNotice.value && fcVersion.value.trim() && payload.fcVersion !== fcVersion.value.trim()) analysisNotice.value = '返回的预测版本与当前选择不一致，已停止展示。'
      if (analysisNotice.value) return
      applyForecastPayload(v13Forecast.value)
      const waterfallPayload = await api.v13Waterfall(batchNo.value)
      if (requestId !== v13LoadRequestId) return
      v13Waterfall.value = waterfallPayload
      applyWaterfallPayload(v13Waterfall.value)
      if (!analysisNotice.value && waterfallPayload.status !== 'CALCULATED' && waterfallPayload.blockers?.length) analysisNotice.value = waterfallPayload.blockers.join('；')
    } else if (view.value === 'risk') {
      const payload = await api.v13Risk(batchNo.value)
      if (requestId !== v13LoadRequestId) return
      v13Risk.value = payload
      analysisNotice.value = analysisBlocker(payload, batchNo.value, perspective.value, 'ACT_BGT')
      if (payload.blockers?.length) analysisNotice.value = payload.blockers.join('；')
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
watch([view, scenario, matrixMetric, activeTopic, fcVersion], () => {
  // Only the forecast endpoint supports FC comparisons.
  if (view.value !== 'forecast' && scenario.value !== 'ACT_BGT') { scenario.value = 'ACT_BGT'; return }
  if (view.value === 'forecast' && scenario.value === 'ACT_BGT') { scenario.value = 'ACT_FC'; return }
  void loadV13()
})
watch([batchNo, period, periodMode, perspective], () => { void refreshAnalysis() })
watch(view, (next) => {
  if (['result', 'pvm', 'tree', 'ai'].includes(next)) void load()
})
</script>

<template>
  <div class="app"><header class="top"><div class="brand">数智财经<small>FINANCE INTELLIGENCE</small></div><div class="topline"></div><div class="top-title">阿米巴经营分析平台 · 真实数据模式</div><div class="top-right"><span class="role-pill">{{ currentRole }}｜{{ activeScopeText }}数据</span><div class="user-dot">财</div><input class="user-input" v-model="currentUser" @keyup.enter="setUser"><button class="top-user-btn" @click="setUser">切换</button></div></header>
    <div class="layout"><aside class="side"><template v-for="group in navGroups" :key="group.title"><h3>{{ group.title }}</h3><button v-for="item in group.items" :key="item[0]" class="nav" :class="{ active: view === item[0] }" @click="go(item[0])"><i>{{ item[2] }}</i><span>{{ item[1] }}</span></button></template><div class="side-foot">真实数据模式<br>无真实结果时不展示指标<br>{{ source === 'api' ? '已连接后端接口' : '等待真实数据' }}</div></aside>
      <main class="main"><div class="crumb">经营分析平台 / <span>{{ title }}</span></div><div class="toolbar"><label>分析期间</label><select v-if="view === 'import'" v-model="period" aria-label="新批次期间"><option value="2026-08">2026年8月</option><option value="2026-07">2026年7月</option><option value="2026-06">2026年6月</option></select><span v-else data-testid="batch-period">{{ batchPeriod || '待加载批次期间' }}</span><label>筛选方式</label><select v-model="periodMode" aria-label="期间口径"><option value="current">当期</option><option value="ytd">累计</option><option value="custom" disabled>自定义月份（尚未支持）</option></select><label>数据视角</label><select v-model="perspective"><option value="BASE">基地视角</option><option value="PRODUCT_LINE">产品线视角</option><option value="BUSINESS_UNIT">事业部视角</option></select><span class="spacer"></span><span class="scenario-note">{{ scenarioLabel }}</span><select v-model="scenario" aria-label="对比场景" :disabled="view !== 'forecast'"><option value="ACT_BGT" :disabled="view === 'forecast'">ACT vs BGT</option><option value="ACT_FC">ACT vs FC</option><option value="FC_BGT">FC vs BGT</option></select><button class="btn" @click="showToast('当前仅展示真实接口返回内容；请先导入并计算真实批次')">数据说明</button><button class="btn primary" :disabled="loading" @click="refreshAnalysis">{{ loading ? '加载中…' : '刷新分析' }}</button></div><div class="v13-banner"><b>V1.3 看板优化</b><span>绝对GAP · TOP · FC · 风险一页纸</span><span class="v13-badge">{{ scenarioLabel }}</span><span class="spacer"></span><small>按接口状态展示V1.3能力 · P1专题占位 · 无真实结果时不展示金额</small></div><div v-if="error" class="alert" style="margin-top:12px">{{ error }}</div><div v-if="analysisNotice" class="alert" role="status" data-testid="analysis-notice">{{ analysisNotice }}</div><div class="readiness" :class="readiness?.readyForCalculation ? 'ready' : 'blocked'"><b>批次完整性：</b>{{ readiness?.readyForCalculation ? '已满足计算条件' : '暂不可计算' }}<span v-if="readiness">· {{ readiness.datasets.filter(d => d.required && d.present && d.status === 'VALID').length }}/{{ readiness.datasets.filter(d => d.required).length }} 个必需数据集通过</span><span v-if="quality">· 数据质量 {{ quality.status === 'READY' ? '通过' : '阻断' }}</span></div>
        <section v-if="view === 'overview'" class="view active"><div class="section-head"><div><h2>月度工作台</h2><p>当前展示：<b>{{ perspectiveName }}</b>视角 · {{ batchPeriod || '待加载批次期间' }} · 当期口径（累计及自定义月份尚未支持） · <span class="scope-chip">{{ activeScopeText }}</span></p></div><div><span class="tag">{{ overviewMetrics.length ? '真实接口结果' : '等待真实计算结果' }}</span> <button class="btn" @click="exportView('经营总览')">导出</button></div></div><div class="cards"><article v-for="card in dashboardCards" :key="card.key" class="card"><div class="metric-label">{{ card.label }}</div><div class="metric">{{ card.value }}<span>{{ card.unit }}</span></div><div :class="['delta', card.good ? 'up' : 'down']">{{ card.delta }}</div></article></div><div v-if="view === 'overview'" class="panel" style="margin-top:15px"><div class="panel-title">月度工作台 <small>DATA-001 readiness</small></div><div v-if="readiness?.datasets?.length" class="dataset-grid"><div v-for="row in readiness.datasets" :key="row.datasetCode" class="dataset-card"><strong>{{ row.datasetName }}</strong><small>{{ row.required ? '必需' : '可选' }} · <span :class="row.status === 'VALID' ? 'green' : row.status === 'MISSING' ? 'muted' : 'red'">{{ row.status }} · {{ row.issueCount || 0 }} 个问题</span></small></div></div><div v-else class="placeholder-card">暂无真实批次状态，请先刷新或创建批次。</div><div class="action-row"><button class="btn" @click="go('import')">进入上传与完整性</button><button class="btn ghost" @click="go('issues')">查看异常清单</button></div></div><div class="grid2"><div class="panel"><div class="panel-title">收入与利润趋势 <small>仅显示真实批次</small></div><div v-if="overviewBars.length" class="bar-list"><div v-for="bar in overviewBars" :key="bar.label" class="bar-col"><div class="bar-wrap"><i class="bar plan" :style="{ height: `${bar.budget}%` }"></i><i class="bar" :style="{ height: `${bar.actual}%` }"></i></div>{{ bar.label }}</div></div><div v-else class="placeholder-card">暂无真实趋势数据，请先导入并计算月度批次。</div></div><div class="panel"><div class="panel-title">重点经营单元 <small>仅显示真实批次</small></div><div v-if="rankRows.length" v-for="(row, index) in rankRows" :key="row[0]" class="rank-row"><span :class="['rank', index < 2 ? 'hot' : '']">{{ index + 1 }}</span><span>{{ row[0] }}<div class="mini-bar"><i :style="{ width: row[3] }"></i></div></span><b class="right">{{ row[1] }}</b><span :class="[row[2].startsWith('+') ? 'green' : 'red', 'right']">{{ row[2] }}</span></div><div v-else class="placeholder-card">暂无真实经营单元排名。</div></div></div><div class="section-head"><div><h2>经营异常与亮点</h2><p>异常清单仅来自真实计算结果</p></div><button class="btn ghost" @click="go('pvm')">进入因素分析 →</button></div><div class="panel"><div class="placeholder-card">暂无真实异常与亮点数据。</div></div><div class="top-split"><div class="panel"><div class="panel-title">有利影响 TOP 3</div><div v-if="topFavorable.length" v-for="(row,index) in topFavorable" :key="row[0]" class="top-item"><span class="rank hot">{{ index + 1 }}</span><b>{{ row[0] }}</b><span class="top-value green">{{ row[1] }}</span><span class="top-rate green">{{ row[2] }}</span></div><div v-else class="placeholder-card">暂无真实有利影响。</div></div><div class="panel"><div class="panel-title">不利影响 TOP 3</div><div v-if="topAdverse.length" v-for="(row,index) in topAdverse" :key="row[0]" class="top-item"><span class="rank">{{ index + 1 }}</span><b>{{ row[0] }}</b><span class="top-value red">{{ row[1] }}</span><span class="top-rate red">{{ row[2] }}</span></div><div v-else class="placeholder-card">暂无真实不利影响。</div></div></div><div class="panel" style="margin-top:15px"><div class="panel-title">统一经营指标 <small>绝对GAP与百分比GAP · {{ scenarioLabel }}</small></div><div v-if="overviewMetrics.length" class="table-scroll"><table class="pnl-table"><thead><tr><th>指标</th><th>当前值</th><th>基准值</th><th>绝对GAP</th><th>GAP%</th><th>AI简析</th></tr></thead><tbody><tr v-for="m in overviewMetrics" :key="m.code"><td>{{ m.name }}</td><td>{{ format(m.code, m.actual) }}</td><td>{{ format(m.code, m.budget) }}</td><td :class="m.gap >= 0 ? 'green' : 'red'">{{ format(m.code, m.gap) }}</td><td>{{ m.budget === 0 || m.gapRate == null ? '—' : `${(m.gapRate * 100).toFixed(1)}%` }}</td><td>{{ m.aiSummary || '—' }}</td></tr></tbody></table></div><div v-else class="placeholder-card">暂无真实经营指标，请先完成数据导入、校验和计算。</div></div></section>
        <section v-else-if="view === 'result'" class="view active"><div class="section-head"><div><h2>损益计算结果</h2><p>仅展示服务端真实计算结果 · <span class="scope-chip">{{ activeScopeText }}</span></p></div><button class="btn" @click="exportView('损益结果')">导出</button></div><div class="permission-callout"><b>口径说明：</b>本页面不补造预算、GAP或结果值；所有字段必须来自已计算批次。</div><div v-if="visiblePnl.length" class="panel table-scroll"><table class="pnl-table"><thead><tr><th>分析对象</th><th>本期实际</th><th>本期预算</th><th>VS预算</th><th>销售成本</th><th>毛利</th><th>基地费用</th><th>营业利润</th><th>净利率</th></tr></thead><tbody><tr v-for="row in visiblePnl" :key="row.scopeCode"><td><b>{{ row.scopeCode }}</b></td><td>{{ format('REVENUE', row.lines.REVENUE || 0) }}</td><td>{{ row.lines.BUDGET_REVENUE == null ? '—' : format('REVENUE', row.lines.BUDGET_REVENUE) }}</td><td>{{ row.lines.GAP_REVENUE == null ? '—' : format('REVENUE', row.lines.GAP_REVENUE) }}</td><td>{{ format('SALES_COST', row.lines.SALES_COST || 0) }}</td><td>{{ format('GROSS_PROFIT', row.lines.GROSS_PROFIT || 0) }}</td><td>{{ format('BASE_EXPENSE', row.lines.BASE_EXPENSE || 0) }}</td><td>{{ format('NET_PROFIT', row.lines.NET_PROFIT || 0) }}</td><td>{{ format('PROFIT_RATE', row.lines.PROFIT_RATE || 0) }}</td></tr></tbody></table></div><div v-else class="placeholder-card">当前批次暂无真实损益计算结果。请在“月度数据导入”完成必需数据集校验后开始计算。</div></section>
        <section v-else-if="view === 'tree'" class="view active"><div class="section-head"><div><h2>指标树</h2><p>仅从真实计算结果逐层拆解</p></div><span class="tag">指标库 · 口径可追溯</span></div><div v-if="treeRows.length" class="grid2"><div class="panel"><div class="panel-title">真实计算指标</div><div class="tree-list"><div v-for="row in treeRows" :key="row.code" class="tree-node level1" @click="showMetric(row.name)"><b>{{ row.name }}</b><span class="right">{{ format(row.code, row.actual) }}</span></div></div></div><div class="panel"><div class="panel-title">指标定义 <small>{{ selectedMetric }}</small></div><div class="placeholder-card">定义内容以服务端指标库和真实批次配置为准。</div></div></div><div v-else class="placeholder-card">当前批次暂无真实指标树结果。</div></section>
        <section v-else-if="view === 'import'" class="view active"><div class="section-head"><div><h2>月度数据导入</h2><p>上传真实月度业务文件，服务端校验通过后才能计算</p></div><span class="tag">当前批次：{{ readiness?.readyForCalculation ? '待计算' : '需修正或待导入' }}</span></div><div class="stepper"><div class="step done"><b>✓</b>创建批次</div><div class="step current"><b>2</b>上传月度数据</div><div class="step"><b>3</b>校验映射</div><div class="step"><b>4</b>统一计算</div><div class="step"><b>5</b>确认发布</div></div><div class="config-form"><label class="muted">本次文件数据集</label><select v-model="uploadDataset"><option disabled value="">请选择数据集</option><option v-for="dataset in datasets" :key="dataset.code" :value="dataset.code">{{ dataset.name }}{{ dataset.required ? '（必需）' : '（可选）' }}</option></select><span class="muted">文件会按所选数据集创建导入任务，不能自动绑定到第一个数据集。</span></div><div class="upload-grid"><div class="panel"><div class="panel-title">真实业务数据文件 <small>整期覆盖导入</small></div><div class="upload-box"><div class="upload-icon">⇧</div><div>将财经月度明细表拖入此处，或选择文件</div><label for="fileInput">选择文件</label><input id="fileInput" type="file" accept=".xlsx,.xls,.csv" @change="pickFile"><div class="muted">支持预算、收入成本、期间费用、减值结果、闲置费用等月度文件</div></div><div v-if="selectedFile" class="file-list"><div class="file"><span>{{ selectedFile.name }}</span><em>待提交服务端校验</em></div></div></div><div class="panel"><div class="panel-title">当前批次完整性</div><div v-if="readiness" class="kpi-grid"><div class="kpi"><small>必需数据集</small><b>{{ readiness.datasets.filter(d => d.required).length }}</b><span class="muted"> 个</span></div><div class="kpi"><small>已通过</small><b>{{ readiness.datasets.filter(d => d.required && d.present && d.status === 'VALID').length }}</b><span class="muted"> 个</span></div><div class="kpi"><small>问题数</small><b class="heat">{{ readiness.datasets.reduce((sum, d) => sum + (d.issueCount || 0), 0) }}</b><span class="muted"> 条</span></div></div><div v-else class="placeholder-card">暂无真实批次完整性数据。</div><div class="action-row"><button class="btn primary" :disabled="calculationBusy" @click="submitImport()">提交校验</button><button class="btn ghost" :disabled="calculationBusy" @click="submitWorkbookImport()">整本样表直接上传</button><button class="btn ghost" :disabled="calculationBusy || !readinessReady" @click="calculateBatch">开始计算</button></div></div></div><div class="panel" style="margin-top:15px"><div class="panel-title">数据集与完整性检查</div><div v-if="readiness?.datasets?.length" class="dataset-grid"><div v-for="row in readiness.datasets" :key="row.datasetCode" class="dataset-card"><strong>{{ row.datasetName }}</strong><small>{{ row.required ? '必需' : '可选' }} · <span :class="row.status === 'VALID' ? 'green' : 'red'">{{ row.status }}</span></small></div></div><div v-else class="placeholder-card">当前批次尚未返回真实数据集状态。</div></div><div v-if="importMessage" class="alert" style="margin-top:12px">{{ importMessage }}</div></section>
        <section v-else-if="view === 'pvm'" class="view active"><div class="section-head"><div><h2>PVM因素分析</h2><p>仅展示真实批次的量价成本因素拆解</p></div><div><span class="tag">{{ pvmRows.length ? '真实计算结果' : '等待真实计算结果' }}</span><button class="btn" @click="exportView('PVM')">导出</button></div></div><div class="panel"><div class="pvm-filter"><label class="muted">当前视角：</label><span class="scope-chip">{{ perspectiveName }}</span><span class="muted">当前范围：</span><span class="scope-chip">{{ activeScopeText }}</span><button class="btn primary" @click="load">刷新分析</button></div></div><div v-if="pvmRows.length" class="panel" style="margin-top:15px"><div class="panel-title">样表 PVM 拆解 <small>金额按导入统一单位；桥差额保留样表原值</small></div><div class="table-scroll"><table><thead><tr><th>分析对象</th><th>预算量</th><th>实际量</th><th>收入GAP</th><th>收入价差</th><th>收入量差</th><th>收入结构差</th><th>收入桥差额</th><th>毛利GAP</th><th>毛利价差</th><th>毛利量差</th><th>毛利成本差</th><th>毛利结构差</th><th>毛利桥差额</th></tr></thead><tbody><tr v-for="row in pvmRows" :key="row.scopeCode"><td><b>{{ row.scopeCode }}</b></td><td>{{ row.budgetVolume }}</td><td>{{ row.actualVolume }}</td><td>{{ row.revenueGap }}</td><td>{{ row.revenuePriceImpact }}</td><td>{{ row.volumeGap }}</td><td>{{ row.revenueStructureImpact }}</td><td>{{ row.revenueBridgeDiff }}</td><td>{{ row.grossProfitGap }}</td><td>{{ row.profitPriceImpact }}</td><td>{{ row.profitVolumeImpact }}</td><td>{{ row.profitCostImpact }}</td><td>{{ row.profitStructureImpact }}</td><td>{{ row.profitBridgeDiff }}</td></tr></tbody></table></div></div><div v-else class="placeholder-card">当前批次暂无真实PVM结果，请先完成数据导入和计算。</div></section>
        <section v-else-if="view === 'permission'" class="view active"><div class="section-head"><div><h2>权限验证</h2><p>切换角色后，系统入口、行级数据和操作权限同步变化</p></div><span class="tag">服务端行级过滤 · 本地真实接口验证</span></div><div class="permission-callout"><b>验证方法：</b>切换角色或授权基地，再尝试进入页面、查看损益、导出数据及生成/发布AI分析。</div><div class="panel"><div class="pvm-filter"><label class="muted">当前角色：</label><select v-model="currentRole" @change="changeRole"><option>总部财经</option><option>基地财经</option><option>基地巴长</option><option>基地运营</option></select><span class="muted">授权对象：</span><select v-model="roleScope"><option>全部基地</option><option>南昌基地</option><option>宜昌基地</option><option>义乌基地</option><option>泰国基地</option></select><button class="btn" @click="showToast('授权保存接口待接入')">保存授权</button></div><table><thead><tr><th>能力</th><th>当前角色权限</th><th>页面表现</th><th>授权范围</th></tr></thead><tbody><tr v-for="row in [['经营看板','可查看','显示授权范围汇总、趋势和排名'],['损益计算结果','可查看','可下钻授权范围损益'],['月度数据导入',currentRole === '总部财经' ? '可编辑' : '只读','按角色控制上传和统一计算'],['配置维护',currentRole === '总部财经' ? '可编辑' : '只读','维护映射和费用分类'],['AI分析发布',currentRole === '总部财经' ? '可发布' : '不可用','草稿、确认和分发受控']]" :key="row[0]"><td>{{ row[0] }}</td><td><span :class="['status', row[1] === '不可用' ? 'gray' : row[1] === '只读' ? 'warn' : 'ok']">{{ row[1] }}</span></td><td>{{ row[2] }}</td><td>{{ roleScope }}</td></tr></tbody></table></div></section>
        <section v-else-if="view === 'ai'" class="view active"><div class="section-head"><div><h2>AI经营分析</h2><p>AI只解释已计算的真实结果，不生成替代数据</p></div><span class="tag">{{ aiDraft ? '真实结果草稿' : '等待真实计算结果' }}</span></div><div class="permission-callout"><b>AI分析状态：</b>仅解释已计算结果；没有真实批次时不生成草稿。</div><div class="ai-box"><div class="ai-top"><div class="ai-icon">✦</div><div class="ai-title">财经AI分析助手<small>仅读取已校验结果和授权明细</small></div><span class="spacer"></span><button class="btn primary" @click="generateAi">生成分析草稿</button></div><div v-if="aiDraft" class="ai-content"><div v-for="(text, name) in aiDraft.sections" :key="name" class="ai-section"><h4>{{ name }}</h4><p>{{ text }}</p></div></div><div v-else class="placeholder-card">当前没有真实计算结果，暂不生成AI分析。</div></div></section>
        <section v-else-if="view === 'gross'" class="view active"><div class="section-head"><div><h2>毛利分析</h2><p>仅展示真实批次的收入、成本与毛利因素</p></div><span>当期口径；累计分析尚未支持</span></div><div class="placeholder-card">当前没有真实毛利计算结果。请先导入并计算月度批次。</div></section>
        <section v-else-if="view === 'matrix'" class="view active"><div class="section-head"><div><h2>事业部视角：基地 × 产品交叉矩阵</h2><p>仅展示服务端真实计算结果</p></div><label>指标<select v-model="matrixMetric"><option value="REVENUE">收入</option><option value="GROSS_PROFIT">毛利</option><option value="NET_PROFIT">净利润（产品归属费用范围）</option></select></label></div><div class="permission-callout"><b>口径边界：</b>没有真实批次时不展示矩阵数字。</div><div v-if="matrixRows.length" class="panel table-scroll"><table class="matrix-table"><thead><tr><th>基地</th><th>产品</th><th>当前值</th><th>基准值</th><th>绝对GAP</th><th>GAP%</th><th>经营判断</th></tr></thead><tbody><tr v-for="row in matrixRows" :key="`${row.base}-${row.product}`"><td>{{ row.base }}</td><td>{{ row.product }}</td><td>{{ format(matrixMetric, matrixPair(row)[0]) }}</td><td>{{ format(matrixMetric, matrixPair(row)[1]) }}</td><td :class="row.gap >= 0 ? 'green' : 'red'">{{ format(matrixMetric, row.gap) }}</td><td>{{ row.budget === 0 || row.gapRate === null ? '—' : `${(row.gapRate * 100).toFixed(1)}%` }}</td><td>{{ row.gap >= 0 ? '贡献提升' : '需下钻' }}</td></tr></tbody></table></div><div v-else class="placeholder-card">当前没有真实基地×产品矩阵结果。</div></section>
        <section v-else-if="view === 'unit'" class="view active"><div class="section-head"><div><h2>单位效率分析</h2><p>仅展示真实线体单位制造费用与YU指标</p></div><span class="tag">{{ v13Unit?.status || '待加载' }}</span></div><div class="unit-grid"><article class="panel"><div class="panel-title">线体单位制造费用 <small>单位：元 / Wh</small></div><table v-if="unitRows.length"><thead><tr><th>基地</th><th>线体</th><th>制造费用</th><th>产出Wh</th><th>元/Wh</th><th>环比</th></tr></thead><tbody><tr v-for="row in unitRows" :key="`${row[0]}-${row[1]}`"><td>{{ row[0] }}</td><td>{{ row[1] }}</td><td>{{ row[2] }}</td><td>{{ row[3] }}</td><td>{{ row[4] }}</td><td :class="row[5].startsWith('+') ? 'red' : 'green'">{{ row[5] }}</td></tr></tbody></table><div v-else class="placeholder-card">暂无真实线体单位费用。</div><div class="formula-box">{{ v13Unit?.blockers?.join('；') || '待真实数据导入。' }}</div></article><article class="panel"><div class="panel-title">YU指标卡与趋势</div><div class="placeholder-card">暂无真实YU指标数据。</div><div class="formula-box">YU指标需接入真实数据源并完成口径确认。</div></article></div></section>
        <section v-else-if="view === 'forecast'" class="view active"><div class="section-head"><div><h2>FC滚动预测与BGT / ACT三口径</h2><p>当前选择：{{ v13Forecast?.fcVersion || '未找到正式预测版本' }} · 仅展示已发布的预测快照</p></div><span class="v13-badge">{{ scenarioLabel }}</span></div><div class="permission-callout"><label>预测版本<input v-model.lazy="fcVersion" aria-label="预测版本" placeholder="留空使用正式版本"></label><b>版本治理：</b>FC不在平台内在线编制；正式预测由预算/经营系统生成后按版本导入。<span v-if="v13Forecast?.blockers?.length">当前状态：{{ v13Forecast.blockers.join('；') }}</span></div><div v-if="forecastRows.length" class="panel table-scroll"><table><thead><tr><th>指标</th><th>BGT</th><th>ACT</th><th>FC</th><th>{{ scenarioLabel }} GAP</th><th>GAP%</th></tr></thead><tbody><tr v-for="row in forecastRows" :key="String(row.metricCode || row.metricName)"><td>{{ row.metricName || row.metricCode }}</td><td>{{ format(String(row.metricCode || 'REVENUE'), Number(row.budget)) }}</td><td>{{ format(String(row.metricCode || 'REVENUE'), Number(row.actual)) }}</td><td>{{ format(String(row.metricCode || 'REVENUE'), Number(row.fc)) }}</td><td>{{ format(String(row.metricCode || 'REVENUE'), Number(row.gap)) }}</td><td>{{ (row.comparison === 'FC_BGT' ? Number(row.budget) : Number(row.fc)) === 0 || row.gapRate == null ? '—' : `${(Number(row.gapRate) * 100).toFixed(1)}%` }}</td></tr></tbody></table></div><div v-else class="alert">当前 FC 接口未返回可发布快照，页面不再使用演示三口径数字。</div><div class="panel waterfall-panel"><div class="panel-title">单位毛利GAP瀑布拆解 <small>来自后端 PVM 因素</small></div><div v-if="waterfallFactors.length" class="waterfall"><div v-for="factor in waterfallFactors" :key="String(factor.code)" class="water-step"><div class="water-bar" :class="Number(factor.value || 0) >= 0 ? 'good' : 'bad'" :style="{ height: `${Math.max(18, Math.abs(Number(factor.value || 0)) * 6)}px` }">{{ Number(factor.value || 0) > 0 ? '+' : '' }}{{ format('REVENUE', Number(factor.value || 0)) }}</div><small>{{ factor.code }}</small></div></div><div v-else class="placeholder-card">瀑布口径尚未确认，技术候选结果暂不展示。</div><div class="formula-box">单位毛利分母仍待财经确认；口径确认前不展示技术候选数值。</div></div></section>
        <section v-else-if="view === 'risk'" class="view active"><div class="risk-sheet"><div class="section-head"><div><h2>经营分析风险提示 · 一页纸</h2><p>{{ batchPeriod || period }} · {{ perspectiveName }}视角 · {{ scenarioLabel }}</p></div><button class="btn primary" @click="printRisk">打印 / 浏览器另存为 PDF</button></div><div class="risk-summary"><div class="risk-kpi"><small>总体风险</small><b :class="riskSummaryClass">{{ riskSummaryLabel }}</b></div><div class="risk-kpi"><small>需关注事项</small><b>{{ riskSummaryCount }}</b></div><div class="risk-kpi"><small>待确认口径</small><b class="risk-level-mid">{{ v13Risk?.blockers?.length || 0 }}</b></div><div class="risk-kpi"><small>数据状态</small><b :class="riskReady ? 'green' : 'risk-level-mid'">{{ v13Risk?.status || '待加载' }}</b></div></div><div class="decision-strip"><span class="decision-chip">结果版本：{{ v13Risk?.versionNo || '待加载' }}</span><span class="decision-chip">阈值：后端状态 {{ v13Risk?.status || '待加载' }}</span><span class="decision-chip">发布前需责任人确认</span></div><table><thead><tr><th>等级</th><th>领域</th><th>风险/异常</th><th>责任角色</th><th>建议动作</th><th>状态</th></tr></thead><tbody><tr v-for="row in riskRows" :key="row[1]"><td><span :class="row[0] === '高' ? 'red' : row[0] === '中' ? 'warning' : 'green'">{{ row[0] }}</span></td><td>{{ row[1] }}</td><td>{{ row[2] }}</td><td>{{ row[3] }}</td><td>{{ row[4] }}</td><td>{{ row[5] }}</td></tr></tbody></table><div class="pnl-note">风险等级由后端确定性规则和阈值状态返回；阈值未冻结时只展示观察项，不替代正式风险审批。</div></div></section>
        <section v-else-if="view === 'nonfinancial'" class="view active"><div class="section-head"><div><h2>非财经指标</h2><p>电芯优率目标/实际按期间、基地、巴长、拉线、型号和类目查询</p></div><span class="tag">待接入</span></div><div class="panel"><h3>当前不可用</h3><p>后端尚未提供非财经指标的独立导入、结果查询和巴长型号授权契约。接入前不显示推算值或财务损益数据。</p><div class="permission-callout"><b>计划数据：</b>目标值、实际值、差额、达成率与未匹配型号状态；巴长范围必须由服务端按型号映射授权。</div></div></section><section v-else-if="view === 'topics'" class="view active"><div class="section-head"><div><h2>P1专题分析</h2><p>{{ activeTopicData.name }} · {{ activeTopicPayload?.notes?.join('；') || activeTopicData.note }}</p></div><span class="tag">{{ activeTopicPayload?.status || '待加载' }}</span></div><div class="topic-buttons"><button v-for="(topic,key) in p1Topics" :key="key" class="btn" :class="{ primary: activeTopic === key }" @click="activeTopic = key">{{ topic.name }}</button></div><div class="panel table-scroll"><table v-if="topicDisplayRows.length"><thead><tr><th v-for="header in activeTopicData.headers" :key="header">{{ header }}</th></tr></thead><tbody><tr v-for="(row,index) in topicDisplayRows" :key="index"><td v-for="cell in row" :key="cell">{{ cell }}</td></tr></tbody></table><div v-else class="placeholder-card">当前专题尚未接入明细源数据，页面只展示后端阻断状态。</div><div class="formula-box">{{ activeTopicPayload?.blockers?.join('；') || '待P0口径冻结、字段映射与权限范围确认。' }}</div></div></section>
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

<style src="./workbench.css"></style>
