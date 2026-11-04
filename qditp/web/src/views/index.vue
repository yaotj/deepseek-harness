<template>
  <div class="app-container dashboard">
    <!-- 系统健康条：一屏之内先回答「现在有没有在坏的东西」，异常时整条变红并可直接跳进对应监控页。 -->
    <div class="health-bar" :class="{ 'is-bad': healthBad }">
      <span class="health-title">系统健康</span>
      <button type="button" class="health-item" @click="go('/monitor/service-status')">
        服务 {{ health.services.up }}/{{ health.services.total }} UP
      </button>
      <button type="button" class="health-item" @click="go('/monitor/device-heartbeat')">
        设备 {{ health.devices.online }}/{{ health.devices.total }} 在线
      </button>
      <button type="button" class="health-item" @click="go('/monitor/job')">
        定时任务 {{ health.jobs.total }}
      </button>
      <button v-if="health.blacklist > 0" type="button" class="health-item" @click="go('/risk/blacklist')">
        黑名单 {{ health.blacklist }}
      </button>
      <span class="health-time">数据取自各列表接口的 total 计数 · {{ loadedAt || '加载中' }}</span>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <div class="zone-title">今日动态</div>
        <el-row :gutter="12">
          <el-col v-for="metric in todayMetrics" :key="metric.key" :xs="12" :sm="8">
            <el-card shadow="hover" class="metric-card" @click="go(metric.route)">
              <div class="metric-label">{{ metric.label }}</div>
              <div class="metric-value tabular">{{ valueOf(metric.key) }}</div>
              <div class="metric-delta" :class="deltaClass(metric)">
                <template v-if="deltaOf(metric) === null">昨日 -</template>
                <template v-else>
                  {{ deltaOf(metric) > 0 ? '↑' : deltaOf(metric) < 0 ? '↓' : '→' }} 昨日 {{ deltaOf(metric) > 0 ? '+' : '' }}{{ deltaOf(metric) }}
                </template>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <div class="zone-title">卡池余量水位</div>
        <el-card shadow="never" class="pool-card">
          <div v-if="!poolRows.length" class="pool-empty">卡池概览未取得（无权限或服务未响应）</div>
          <div v-for="row in poolRows" :key="row.cardType" class="pool-row" @click="go('/monitor/card-pool')">
            <span class="pool-name">{{ row.name }}</span>
            <el-progress
              class="pool-bar"
              :percentage="row.rate"
              :status="row.rate < 10 ? 'exception' : row.rate < 30 ? 'warning' : 'success'"
              :stroke-width="10"
            />
            <span class="pool-count tabular">{{ row.available }}</span>
          </div>
          <div class="pool-foot">
            水位分母＝该票种已发出卡号总量（可用+预占+已分配）；<b>不等于</b>容量上限。点击行进入卡池管理。
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <div class="zone-title">业务规模</div>
        <el-card shadow="never" class="scale-card">
          <button v-for="item in scaleItems" :key="item.label" type="button" class="scale-row" @click="go(item.route)">
            <span class="scale-label">{{ item.label }}</span>
            <span class="scale-value tabular">{{ valueOf(item.key) }}</span>
          </button>
        </el-card>

        <div class="zone-title">快捷入口</div>
        <el-card shadow="never" class="scale-card">
          <button v-for="item in quickLinks" :key="item.label" type="button" class="scale-row" @click="go(item.route)">
            <span class="scale-label">{{ item.label }}</span>
            <el-icon class="scale-arrow"><Right /></el-icon>
          </button>
        </el-card>
      </el-col>
    </el-row>

    <el-alert v-if="failed.length" type="warning" :closable="false" show-icon class="mt8">
      <template #title>
        {{ failed.length }} 个指标未取得数据：{{ failed.join('、') }}。多为该接口要求筛选条件、或当前账号没有对应菜单权限，
        点进对应页面按提示查询即可，不要把「-」当成 0。
      </template>
    </el-alert>
  </div>
</template>

<script setup name="Index">
import { listServiceStatus } from '@/api/monitor/serviceStatus'
import { listJob } from '@/api/monitor/job'
import { listDeviceHeartbeat } from '@/api/monitor/deviceHeartbeat'
import { listLineInfo } from '@/api/para/line'
import { listStationInfo } from '@/api/para/station'
import { listLineStationVersion } from '@/api/para/lineStationVersion'
import { listBaseFares } from '@/api/para/baseFare'
import { listBlacklists } from '@/api/trans/blacklist'
import { listKeyVersions } from '@/api/trans/keyVersion'
import { getCardPoolSummary } from '@/api/trans/cardPool'
import { listFacePayOrders } from '@/api/trans/facePayOrder'
import { listDailyTicketRefundOrders, listDailyTicketRefundRecords } from '@/api/trans/dailyTicketRefund'
import { listQRCodeTxnDetails } from '@/api/trans/userSearch'
import { listRegStats } from '@/api/trans/regStats'
import { cardTypeLabel } from '@/utils/cardType'

/**
 * 运营驾驶舱。
 *
 * 每个指标都落到一个**已实测可用**的列表接口上，取它的 total（或后端已有的 summary），
 * 首页不新增任何聚合端点、也不自己算业务口径：
 *  - 时间参数名各接口不统一（face-pay/daily-ticket 用 beginTime+endTime 到秒，
 *    gate-txn-pay 与 reg-stats 用 startDate+endDate 到日），照各页现状传，别统一。
 *  - 「签约笔数」这类没有全局计数端点的指标一律不上首页（2026-11-04 实测 /page/pay-sign/channels
 *    返回的是 HTTP 200 + UUID 形态错误码，端点不存在），NEVER 用相近接口凑数。
 */
const router = useRouter()

const values = reactive({})
const failed = ref([])
const poolRows = ref([])
const health = reactive({
  services: { up: '-', total: '-' },
  devices: { online: '-', total: '-' },
  jobs: { total: '-' },
  blacklist: 0
})
const loadedAt = ref('')

const todayMetrics = [
  { key: 'facePayToday', yKey: 'facePayYesterday', label: '当面付订单', route: '/trans-operation/face-pay-order' },
  { key: 'dailyOrderToday', yKey: 'dailyOrderYesterday', label: '日票订单', route: '/trans-operation/daily-ticket-refund' },
  { key: 'txnToday', yKey: 'txnYesterday', label: '票卡交易笔数', route: '/user-operation/txn-detail' },
  { key: 'regToday', yKey: 'regYesterday', label: '新注册（按票种合计）', route: '/stats/reg-stats' },
  { key: 'dailyRefundToday', yKey: 'dailyRefundYesterday', label: '日票退款记录', route: '/trans-operation/refund-record' }
]

const scaleItems = [
  { key: 'lineCount', label: '线路', route: '/para/line' },
  { key: 'stationCount', label: '车站', route: '/para/station' },
  { key: 'paraVersionCount', label: '生效参数版本', route: '/para/lineStationVersion' },
  { key: 'baseFareCount', label: '基础票价条目', route: '/para/base-fare' },
  { key: 'keyVersionCount', label: '密钥域版本', route: '/key-version' },
  { key: 'blacklistCount', label: '黑名单（生效中）', route: '/risk/blacklist' }
]

const quickLinks = [
  { label: '用户查询', route: '/user-operation/itp-user' },
  { label: '卡池管理', route: '/monitor/card-pool' },
  { label: '服务状态监控', route: '/monitor/service-status' },
  { label: '设备心跳状态', route: '/monitor/device-heartbeat' },
  { label: '异步导出任务', route: '/monitor/async-export' },
  { label: '菜单与权限', route: '/system/menu' }
]

function pad(value) {
  return String(value).padStart(2, '0')
}

function dayStart(offset) {
  const now = new Date()
  const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() - offset)
  return `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())}`
}

/** 到秒的区间（face-pay / daily-ticket 的 beginTime/endTime 口径）。 */
function dateTimeRange(offset) {
  const day = dayStart(offset)
  return [`${day} 00:00:00`, `${day} 23:59:59`]
}

function valueOf(key) {
  return values[key] === undefined ? '-' : values[key]
}

function deltaOf(metric) {
  const today = values[metric.key]
  const yesterday = values[metric.yKey]
  if (typeof today !== 'number' || typeof yesterday !== 'number') return null
  return today - yesterday
}

function deltaClass(metric) {
  const delta = deltaOf(metric)
  if (delta === null) return 'is-unknown'
  if (delta > 0) return 'is-up'
  if (delta < 0) return 'is-down'
  return ''
}

const healthBad = computed(() => {
  const servicesDown = typeof health.services.up === 'number' && health.services.up !== health.services.total
  const devicesOffline = typeof health.devices.online === 'number' && health.devices.online !== health.devices.total
  return servicesDown || devicesOffline
})

/** 单个指标失败只影响自己：卡片显示「-」，页面底部汇总原因，NEVER 让一个接口拖挂整屏。 */
function track(key, promise, extract) {
  return promise
    .then((response) => {
      const next = extract(response)
      if (next === null || next === undefined) {
        failed.value.push(labelOf(key))
        values[key] = '-'
      } else {
        values[key] = next
      }
    })
    .catch(() => {
      failed.value.push(labelOf(key))
      values[key] = '-'
    })
}

/** 失败提示给人看，NEVER 直接吐内部 key。 */
function labelOf(key) {
  const metric = [...todayMetrics, ...scaleItems].find((item) => item.key === key)
  if (metric) return metric.label
  return { deviceTotal: '设备心跳', jobTotal: '定时任务', serviceTotal: '服务探活' }[key] || key
}

const totalOf = (response) => Number(response.data?.total ?? 0)

function load() {
  failed.value = []
  const [faceBegin, faceEnd] = dateTimeRange(0)
  const [faceBeginY, faceEndY] = dateTimeRange(1)
  const todayDay = dayStart(0)
  const yesterdayDay = dayStart(1)

  const tasks = [
    track('facePayToday', listFacePayOrders({ beginTime: faceBegin, endTime: faceEnd, pageNum: 1, pageSize: 1 }), totalOf),
    track('facePayYesterday', listFacePayOrders({ beginTime: faceBeginY, endTime: faceEndY, pageNum: 1, pageSize: 1 }), totalOf),
    track('dailyOrderToday', listDailyTicketRefundOrders({ beginTime: faceBegin, endTime: faceEnd, pageNum: 1, pageSize: 1 }), totalOf),
    track('dailyOrderYesterday', listDailyTicketRefundOrders({ beginTime: faceBeginY, endTime: faceEndY, pageNum: 1, pageSize: 1 }), totalOf),
    track('dailyRefundToday', listDailyTicketRefundRecords({ beginTime: faceBegin, endTime: faceEnd, pageNum: 1, pageSize: 1 }), totalOf),
    track('dailyRefundYesterday', listDailyTicketRefundRecords({ beginTime: faceBeginY, endTime: faceEndY, pageNum: 1, pageSize: 1 }), totalOf),
    track('txnToday', listQRCodeTxnDetails({ startDate: todayDay, endDate: todayDay, pageNum: 1, pageSize: 1 }), totalOf),
    track('txnYesterday', listQRCodeTxnDetails({ startDate: yesterdayDay, endDate: yesterdayDay, pageNum: 1, pageSize: 1 }), totalOf),
    track('regToday', listRegStats({ startDate: todayDay, endDate: todayDay }), sumRegCount),
    track('regYesterday', listRegStats({ startDate: yesterdayDay, endDate: yesterdayDay }), sumRegCount),
    track('lineCount', listLineInfo({ pageNum: 1, pageSize: 1 }), totalOf),
    track('stationCount', listStationInfo({ pageNum: 1, pageSize: 1 }), totalOf),
    track('paraVersionCount', listLineStationVersion({ pageNum: 1, pageSize: 1 }), totalOf),
    track('baseFareCount', listBaseFares({ pageNum: 1, pageSize: 1 }), totalOf),
    track('keyVersionCount', listKeyVersions(), (r) => (Array.isArray(r.data) ? r.data.length : null)),
    track('blacklistCount', listBlacklists({ pageNum: 1, pageSize: 1 }), totalOf),
    track('deviceTotal', listDeviceHeartbeat({ pageNum: 1, pageSize: 1 }), (r) => {
      const summary = r.data?.summary || {}
      health.devices = { online: summary.onlineCount ?? '-', total: summary.total ?? '-' }
      return summary.total ?? null
    }),
    track('jobTotal', listJob({ pageNum: 1, pageSize: 1 }), (r) => {
      health.jobs = { total: Number(r.total ?? 0) }
      return Number(r.total ?? 0)
    }),
    track('serviceTotal', listServiceStatus(), (r) => {
      health.services = { up: r.upCount ?? '-', total: r.total ?? '-' }
      return r.total ?? null
    })
  ]

  Promise.allSettled(tasks).then(() => {
    health.blacklist = Number(values.blacklistCount || 0)
    loadedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  })

  getCardPoolSummary()
    .then((response) => {
      poolRows.value = (response.data || [])
        .filter((row) => row.poolEnabled)
        .map((row) => {
          const available = Number(row.availableCount || 0)
          const issued = available + Number(row.reservedCount || 0) + Number(row.assignedCount || 0)
          return {
            cardType: row.cardType,
            name: cardTypeLabel(row.cardType),
            available,
            rate: issued ? Math.round((available / issued) * 100) : 0
          }
        })
        .sort((a, b) => a.rate - b.rate)
    })
    .catch(() => {
      failed.value.push('卡池余量')
    })
}

/** reg-stats 是按票种分组的数组，首页只关心合计；口径与统计页同源，NEVER 在此另算一套。 */
function sumRegCount(response) {
  const list = response.data
  if (!Array.isArray(list)) return null
  return list.reduce((sum, item) => sum + Number(item.regCount || 0), 0)
}

function go(path) {
  router.push(path)
}

load()
</script>

<style scoped>
.dashboard {
  font-size: 13px;
}
.health-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 16px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  border-left: 4px solid var(--el-color-success);
}
.health-bar.is-bad {
  border-left-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
.health-title {
  font-weight: 700;
}
.health-item {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-regular);
  padding: 0 6px;
}
.health-item:hover {
  color: var(--el-color-primary);
}
.health-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.zone-title {
  margin: 8px 0;
  font-size: 14px;
  font-weight: 700;
}
.metric-card {
  margin-bottom: 12px;
  cursor: pointer;
}
.metric-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.metric-value {
  margin: 6px 0 2px;
  font-size: 24px;
  font-weight: 600;
}
.metric-delta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.metric-delta.is-up {
  color: var(--el-color-success);
}
.metric-delta.is-down {
  color: var(--el-color-danger);
}
.pool-card,
.scale-card {
  margin-bottom: 12px;
}
.pool-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  cursor: pointer;
}
.pool-name {
  width: 170px;
  flex: none;
}
.pool-bar {
  flex: 1;
}
.pool-count {
  width: 90px;
  text-align: right;
  flex: none;
}
.pool-empty {
  color: var(--el-text-color-secondary);
  padding: 8px 0;
}
.pool-foot {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.scale-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 4px;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  cursor: pointer;
  font-size: 13px;
}
.scale-row:last-child {
  border-bottom: none;
}
.scale-row:hover .scale-label {
  color: var(--el-color-primary);
}
.scale-value {
  font-weight: 600;
  font-size: 16px;
}
.scale-arrow {
  color: var(--el-text-color-placeholder);
}
.mt8 {
  margin-top: 8px;
}
</style>
