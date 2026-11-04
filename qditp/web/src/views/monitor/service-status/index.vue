<template>
  <div class="app-container">
    <el-row :gutter="16" class="mb8">
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">服务总数</div>
          <div class="stat-value">{{ summary.total || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">正常（UP）</div>
          <div class="stat-value is-up">{{ counts.up }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">服务不可用</div>
          <div class="stat-value is-down">{{ counts.unavailable }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-label">探针/配置异常</div>
          <div class="stat-value is-suspect">{{ counts.suspect }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">最近检查时间</div>
          <div class="stat-value is-time">{{ summary.checkedAt || '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row class="mb8" :gutter="12">
      <el-col :span="1.5">
        <el-button type="primary" icon="Refresh" :loading="loading" @click="getList">重新探测全部</el-button>
      </el-col>
      <el-col :span="6">
        <el-checkbox v-model="onlyAbnormal">只看异常</el-checkbox>
      </el-col>
      <el-col :span="12">
        <span class="auto-refresh-tip">
          每 {{ AUTO_REFRESH_MS / 1000 }} 秒自动重探；点阵为**本次页面会话内**最近 {{ HISTORY_MAX }} 次结果，刷新页面即清空（后端不保留探活历史）
        </span>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="visibleList" border :row-class-name="rowClassName">
      <template #empty>
        <empty-state :error="queryError" :filters="{}" plain @retry="getList" />
      </template>
      <el-table-column label="服务" prop="displayName" min-width="140" align="center" />
      <el-table-column label="服务标识" prop="serviceName" width="140" align="center" />
      <el-table-column label="状态" width="150" align="center">
        <template #default="{ row }">
          <status-tag :type="severityOf(row).tagType" :label="severityOf(row).label" />
        </template>
      </el-table-column>
      <el-table-column label="最近探测" width="130" align="center">
        <template #default="{ row }">
          <div class="probe-dots">
            <span
              v-for="(item, index) in historyOf(row.serviceName)"
              :key="index"
              class="probe-dot"
              :class="dotClass(item)"
              :title="`${item.checkedAt || ''} ${item.status}${item.message ? ' · ' + item.message : ''}`"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column label="响应时间" width="100" align="right">
        <template #default="{ row }">
          <span class="tabular">{{ row.responseTimeMs >= 0 ? row.responseTimeMs + ' ms' : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="探活地址" prop="healthUrl" min-width="240" align="center" show-overflow-tooltip />
      <el-table-column label="备注" min-width="200" align="center">
        <template #default="{ row }">
          <span :class="{ 'is-suspect-text': severityOf(row).key === 'suspect' }">{{ row.message || '-' }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup name="ServiceStatus">
import { listServiceStatus } from '@/api/monitor/serviceStatus'

/**
 * 服务状态监控。
 *
 * IT-049 的教训：accEs 的 503 是「服务没配 k8s 探针、主 health 端点被 readiness 拖成 DOWN」，
 * 本体其实健康。原来一个 DOWN 标签把「服务真挂了」和「探针/配置有问题」混成同一种红，
 * 运维无从判断先查哪边。这里按后端 message 的形态把两者分开着色、分开计数。
 */
const AUTO_REFRESH_MS = 30000
const HISTORY_MAX = 10

const loading = ref(false)
const queryError = ref('')
const serviceList = ref([])
const summary = ref({})
const onlyAbnormal = ref(false)
/** serviceName → 最近若干次探测结果；只在本页会话内累积，后端不落库。 */
const history = reactive({})
let timer = null

/**
 * 探测结果分类。判据来自 ServiceStatusService 写 message 的三条分支：
 * 未配置地址 / `HTTP <status>` / `<异常类名>: <原因>`。
 */
function severityOf(row) {
  if (row.status === 'UP') return { key: 'up', tagType: 'success', label: 'UP' }
  const message = String(row.message || '')
  if (!message || message === '未配置服务地址') {
    return { key: 'suspect', tagType: 'warning', label: '配置缺失' }
  }
  const http = message.match(/^HTTP (\d{3})/)
  if (http) {
    const code = Number(http[1])
    // 4xx：服务在应答，是探活路径/权限不对；200 但 body 无 UP：服务自己说它不健康。
    if (code >= 400 && code < 500) return { key: 'suspect', tagType: 'warning', label: '探针异常' }
    return { key: 'unavailable', tagType: 'danger', label: '服务不可用' }
  }
  if (/Timeout|timeout/.test(message)) return { key: 'suspect', tagType: 'warning', label: '探测超时' }
  if (/ConnectException|UnknownHost|SSL|Refused/.test(message)) return { key: 'unavailable', tagType: 'danger', label: '服务不可达' }
  return { key: 'unavailable', tagType: 'danger', label: row.status || 'DOWN' }
}

const SEVERITY_ORDER = { unavailable: 0, suspect: 1, up: 2 }

const sortedList = computed(() =>
  [...serviceList.value].sort((a, b) => {
    const diff = SEVERITY_ORDER[severityOf(a).key] - SEVERITY_ORDER[severityOf(b).key]
    return diff !== 0 ? diff : String(a.serviceName).localeCompare(String(b.serviceName))
  })
)

const visibleList = computed(() =>
  onlyAbnormal.value
    ? sortedList.value.filter((row) => severityOf(row).key !== 'up')
    : sortedList.value
)

const counts = computed(() => {
  const result = { up: 0, unavailable: 0, suspect: 0 }
  serviceList.value.forEach((row) => { result[severityOf(row).key] += 1 })
  return result
})

function historyOf(serviceName) {
  return history[serviceName] || []
}

function dotClass(item) {
  return `dot-${severityOf(item).key}`
}

function rowClassName({ row }) {
  const key = severityOf(row).key
  return key === 'up' ? '' : `row-${key}`
}

function recordHistory(rows, checkedAt) {
  rows.forEach((row) => {
    const list = history[row.serviceName] || (history[row.serviceName] = [])
    list.push({ status: row.status, message: row.message, checkedAt })
    if (list.length > HISTORY_MAX) list.shift()
  })
}

function getList() {
  loading.value = true
  queryError.value = ''
  listServiceStatus()
    .then((response) => {
      serviceList.value = response.list || []
      summary.value = response || {}
      recordHistory(serviceList.value, response.checkedAt)
    })
    .catch((error) => {
      queryError.value = error?.message || '探活接口调用失败'
    })
    .finally(() => { loading.value = false })
}

onMounted(() => {
  getList()
  timer = setInterval(getList, AUTO_REFRESH_MS)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.stat-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 600;
}
.stat-value.is-up {
  color: var(--el-color-success);
}
.stat-value.is-down {
  color: var(--el-color-danger);
}
.stat-value.is-suspect {
  color: var(--el-color-warning);
}
.stat-value.is-time {
  font-size: 16px;
}
.auto-refresh-tip {
  line-height: 32px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.probe-dots {
  display: flex;
  gap: 3px;
  justify-content: center;
}
.probe-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-border-color);
}
.probe-dot.dot-up {
  background: var(--el-color-success);
}
.probe-dot.dot-unavailable {
  background: var(--el-color-danger);
}
.probe-dot.dot-suspect {
  background: var(--el-color-warning);
}
.is-suspect-text {
  color: var(--el-color-warning);
}
/* el-table 的行由组件自身渲染，故 MUST 用 :deep() 穿透作用域，直接写类名不会命中。 */
.app-container :deep(.row-unavailable) td {
  background-color: var(--el-color-danger-light-9);
}
.app-container :deep(.row-suspect) td {
  background-color: var(--el-color-warning-light-9);
}
</style>
