<template>
  <div class="app-container">
    <el-row :gutter="16" class="mb8">
      <el-col :span="6">
        <el-card shadow="never" class="ratio-card">
          <el-progress
            type="dashboard"
            :percentage="onlineRate"
            :width="110"
            :stroke-width="12"
            :color="ratioColor"
          >
            <template #default="{ percentage }">
              <div class="ratio-value tabular">{{ percentage }}%</div>
              <div class="ratio-label">在线率</div>
            </template>
          </el-progress>
          <div class="ratio-detail">
            <span class="is-up">在线 {{ summary.onlineCount || 0 }}</span>
            <span class="is-offline">离线 {{ summary.offlineCount || 0 }}</span>
            <span>共 {{ summary.total || 0 }} 台</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">最近一次心跳（全部设备）</div>
          <div class="stat-value is-time">{{ summary.lastHeartbeatTms || '-' }}</div>
          <div class="stat-hint">离线判定：超过 {{ OFFLINE_TIMEOUT_SECONDS / 60 }} 分钟无心跳</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div class="stat-label">当前列表</div>
          <div class="stat-value is-time">
            {{ total }} 台
            <span v-if="staleCount" class="is-stale">· {{ staleCount }} 台心跳已超时但状态未翻转</span>
          </div>
          <div class="stat-hint">后端排序已是「离线优先 + 最近心跳倒序」，第一屏即掉线设备</div>
        </el-card>
      </el-col>
    </el-row>

    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="设备类型" prop="channel">
        <el-select v-model="queryParams.channel" clearable placeholder="全部" style="width: 140px">
          <el-option v-for="(label, code) in F2F_DEVICE_TYPE_LABELS" :key="code" :label="label" :value="code" />
        </el-select>
      </el-form-item>
      <el-form-item label="在线状态" prop="onlineFlag">
        <el-select v-model="queryParams.onlineFlag" clearable placeholder="全部" style="width: 120px">
          <el-option label="在线" value="1" />
          <el-option label="离线" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备编号" prop="deviceId">
        <el-input v-model="queryParams.deviceId" placeholder="支持模糊匹配" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button icon="RefreshRight" :loading="loading" @click="getList">刷新</el-button>
        <span class="auto-refresh-tip">每 {{ AUTO_REFRESH_MS / 1000 }} 秒自动刷新</span>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="devices" border :row-class-name="rowClassName">
      <template #empty>
        <empty-state
          :error="queryError"
          :filters="queryParams"
          plain
          hint="设备心跳来自 face-pay-server 的 F2F_DEVICE_STATUS，只反映上报过的设备"
          @clear="resetQuery"
          @retry="getList"
        />
      </template>
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="设备类型" width="110" align="center">
        <template #default="{ row }">{{ f2fDeviceTypeLabel(row.channel) }}</template>
      </el-table-column>
      <el-table-column label="设备编号" prop="deviceId" width="130" align="center">
        <template #default="{ row }"><span class="tabular">{{ row.deviceId }}</span></template>
      </el-table-column>
      <el-table-column label="车站" width="180" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ formatStation(row) }}</template>
      </el-table-column>
      <el-table-column label="在线状态" width="110" align="center">
        <template #default="{ row }">
          <status-tag :type="row.onlineFlag === '1' ? 'success' : 'danger'" :label="row.onlineFlag === '1' ? '在线' : '离线'" />
        </template>
      </el-table-column>
      <el-table-column label="最近心跳时间" prop="lastHeartbeatTms" width="170" align="center">
        <template #default="{ row }"><span class="tabular">{{ row.lastHeartbeatTms || '-' }}</span></template>
      </el-table-column>
      <el-table-column label="距上次心跳" width="140" align="center">
        <template #default="{ row }">
          <span :class="['tabular', { 'is-stale': isStale(row) }]">{{ formatIdle(row.idleSeconds) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="累计心跳次数" prop="heartbeatCount" width="120" align="right">
        <template #default="{ row }"><span class="tabular">{{ row.heartbeatCount ?? '-' }}</span></template>
      </el-table-column>
      <el-table-column label="最后更新时间" prop="updateTms" width="170" align="center">
        <template #default="{ row }"><span class="tabular">{{ row.updateTms || '-' }}</span></template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="DeviceHeartbeat">
import { listDeviceHeartbeat } from '@/api/monitor/deviceHeartbeat'
import { F2F_DEVICE_TYPE_LABELS, f2fDeviceTypeLabel } from '@/utils/f2fChannel'

const AUTO_REFRESH_MS = 30000

/**
 * 离线判定阈值：face-pay-server 的 `f2f.device.offlineTimeoutSeconds` 代码默认值。
 * 2026-11-04 现查测试环境 Deployment env 未覆盖该键，故取默认 300 秒；
 * 换环境时 MUST 重新核对该 env，NEVER 把它当跨环境常量。
 */
const OFFLINE_TIMEOUT_SECONDS = 300

const loading = ref(false)
const queryError = ref('')
const total = ref(0)
const devices = ref([])
const summary = ref({})
const queryParams = reactive({ channel: '', onlineFlag: '', deviceId: '', pageNum: 1, pageSize: 10 })
let timer = null

const onlineRate = computed(() => {
  const count = Number(summary.value.onlineCount || 0)
  const all = Number(summary.value.total || 0)
  return all ? Math.round((count / all) * 100) : 0
})

/** 在线率低于一半才转红：少量设备离线是常态，直接按 percentage 渐变会让人误判整体故障。 */
const ratioColor = computed(() => {
  if (onlineRate.value >= 80) return 'var(--el-color-success)'
  if (onlineRate.value >= 50) return 'var(--el-color-warning)'
  return 'var(--el-color-danger)'
})

/** 状态仍标在线、但心跳已超过离线阈值：说明离线判定任务没跟上，是页面该主动暴露的不一致。 */
const staleCount = computed(() => devices.value.filter((row) => isStale(row)).length)

function isStale(row) {
  return row.onlineFlag === '1' && Number(row.idleSeconds) > OFFLINE_TIMEOUT_SECONDS
}

function getList() {
  loading.value = true
  queryError.value = ''
  listDeviceHeartbeat(queryParams)
    .then((response) => {
      const page = response.data || {}
      devices.value = page.list || []
      total.value = Number(page.total || 0)
      summary.value = page.summary || {}
    })
    .catch((error) => {
      queryError.value = error?.message || '查询失败'
    })
    .finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.channel = ''
  queryParams.onlineFlag = ''
  queryParams.deviceId = ''
  queryParams.pageNum = 1
  getList()
}

/** 离线行整行标红；在线但心跳已超阈值的行标灰，两种异常不能混成同一种颜色。 */
function rowClassName({ row }) {
  if (row.onlineFlag !== '1') return 'row-offline'
  return isStale(row) ? 'row-stale' : ''
}

/** 车站列：`站名(站码)`；STATION_INFO 未覆盖的站码退化为只显示站码，两者都无则显示 `-`。 */
function formatStation(row) {
  const code = row.stationCode || null
  const name = row.stationName || null
  if (name && code) return `${name}(${code})`
  return name || code || '-'
}

/** 距上次心跳：秒数由后端用 DB 时钟现算，前端只做可读化，避免浏览器时区参与判断。 */
function formatIdle(seconds) {
  const value = Number(seconds)
  if (!Number.isFinite(value)) return '-'
  const abs = Math.max(0, Math.round(value))
  if (abs < 60) return `${abs} 秒`
  if (abs < 3600) return `${Math.floor(abs / 60)} 分 ${abs % 60} 秒`
  const hours = Math.floor(abs / 3600)
  if (hours < 24) return `${hours} 小时 ${Math.floor((abs % 3600) / 60)} 分`
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`
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
.stat-value.is-time {
  font-size: 15px;
}
.stat-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ratio-card {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ratio-value {
  font-size: 18px;
  font-weight: 700;
}
.ratio-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ratio-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}
.ratio-detail .is-up {
  color: var(--el-color-success);
}
.ratio-detail .is-offline {
  color: var(--el-color-danger);
}
.is-stale {
  color: var(--el-color-warning);
}
.auto-refresh-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
/* el-table 的行由组件自身渲染，故 MUST 用 :deep() 穿透作用域，直接写 .row-offline 不会命中。 */
.app-container :deep(.row-offline) td {
  background-color: var(--el-color-danger-light-9);
}
.app-container :deep(.row-stale) td {
  background-color: var(--el-fill-color-light);
}
</style>
