<template>
  <div class="app-container">
    <el-row :gutter="20" class="mb8">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">设备总数</div>
          <div class="stat-value">{{ summary.total || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">在线</div>
          <div class="stat-value" style="color:#67C23A">{{ summary.onlineCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">离线</div>
          <div class="stat-value" style="color:#F56C6C">{{ summary.offlineCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">最近一次心跳（全部设备）</div>
          <div class="stat-value" style="font-size:15px">{{ summary.lastHeartbeatTms || '-' }}</div>
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
        <span class="auto-refresh-tip">每 30 秒自动刷新</span>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="devices" border :row-class-name="rowClassName">
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="设备类型" width="110" align="center">
        <template #default="{ row }">{{ f2fDeviceTypeLabel(row.channel) }}</template>
      </el-table-column>
      <el-table-column label="设备编号" prop="deviceId" width="130" align="center" />
      <el-table-column label="车站" width="180" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ formatStation(row) }}</template>
      </el-table-column>
      <el-table-column label="在线状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.onlineFlag === '1' ? 'success' : 'danger'">{{ row.onlineFlag === '1' ? '在线' : '离线' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近心跳时间" prop="lastHeartbeatTms" width="170" align="center" />
      <el-table-column label="距上次心跳" width="130" align="center">
        <template #default="{ row }">{{ formatIdle(row.idleSeconds) }}</template>
      </el-table-column>
      <el-table-column label="累计心跳次数" prop="heartbeatCount" width="120" align="center" />
      <el-table-column label="最后更新时间" prop="updateTms" width="170" align="center" />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="DeviceHeartbeat">
import { listDeviceHeartbeat } from '@/api/monitor/deviceHeartbeat'
import { F2F_DEVICE_TYPE_LABELS, f2fDeviceTypeLabel } from '@/utils/f2fChannel'

/** 与后端 F2fDeviceOfflineJob 的超时口径一致，仅用于提示文案。 */
const AUTO_REFRESH_MS = 30000

const loading = ref(false)
const total = ref(0)
const devices = ref([])
const summary = ref({})
const queryParams = reactive({ channel: '', onlineFlag: '', deviceId: '', pageNum: 1, pageSize: 10 })
let timer = null

function getList() {
  loading.value = true
  listDeviceHeartbeat(queryParams)
    .then((response) => {
      const page = response.data || {}
      devices.value = page.list || []
      total.value = Number(page.total || 0)
      summary.value = page.summary || {}
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

/** 离线行整行标红：运维打开页面第一屏就该看见掉线设备，即使他正在按设备号筛。 */
function rowClassName({ row }) {
  return row.onlineFlag === '1' ? '' : 'row-offline'
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
  color: #909399;
  font-size: 13px;
}
.stat-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 600;
}
.auto-refresh-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
/* el-table 的行由组件自身渲染，故 MUST 用 :deep() 穿透作用域，直接写 .row-offline 不会命中。 */
.app-container :deep(.row-offline) td {
  background-color: #fef0f0;
}
</style>
