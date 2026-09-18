<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px">
      <el-form-item label="交易日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="20" class="mb8">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">离线码交易总笔数</div>
          <div class="stat-value">{{ summary.txnCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">涉及独立卡数</div>
          <div class="stat-value">{{ summary.cardCount || 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="14">
        <el-table v-loading="loading" :data="statsList" border>
          <el-table-column label="序号" type="index" width="70" align="center" />
          <el-table-column label="车站编码" prop="stationCode" width="110" align="center" />
          <el-table-column label="车站名称" prop="stationName" min-width="160" align="center">
            <template #default="{ row }">{{ row.stationName || row.stationCode }}</template>
          </el-table-column>
          <el-table-column label="交易笔数" prop="txnCount" width="120" align="center" />
          <el-table-column label="独立卡数" prop="cardCount" width="120" align="center" />
          <el-table-column label="占比" width="100" align="center">
            <template #default="{ row }">{{ ratio(row.txnCount) }}</template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="10">
        <div ref="barRef" style="height: 420px" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="OfflineCodeStats">
import * as echarts from 'echarts'
import { listOfflineCodeStats } from '@/api/trans/offlineCodeStats'
import { ElMessage } from 'element-plus'
import { defaultTodayRange } from '@/utils/dateRange'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD'

const loading = ref(false)
const statsList = ref([])
const summary = ref({})
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const barRef = ref(null)
let barChart = null

const totalCount = computed(() => Number(summary.value.txnCount || 0))

function ratio(count) {
  const total = totalCount.value
  if (!total) return '0.00%'
  return ((Number(count || 0) / total) * 100).toFixed(2) + '%'
}

function renderBar() {
  if (!barRef.value) return
  if (!barChart) {
    barChart = echarts.init(barRef.value, 'macarons')
  }
  const rows = statsList.value.slice(0, 15)
  barChart.setOption({
    title: { text: '各车站离线码交易笔数（前 15）', left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { left: 10, right: 20, bottom: 10, containLabel: true },
    xAxis: { type: 'category', data: rows.map(r => r.stationName || r.stationCode), axisLabel: { rotate: 40 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: rows.map(r => r.txnCount), itemStyle: { color: '#409EFF' } }]
  })
}

function getList() {
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请选择交易日期范围')
    return
  }
  loading.value = true
  listOfflineCodeStats({ startDate: dateRange.value[0], endDate: dateRange.value[1] })
    .then((response) => {
      statsList.value = (response.data && response.data.list) || []
      summary.value = (response.data && response.data.summary) || {}
      nextTick(renderBar)
    })
    .finally(() => { loading.value = false })
}

function handleQuery() {
  getList()
}

function resetQuery() {
  dateRange.value = defaultTodayRange(DATE_FORMAT)
  statsList.value = []
  summary.value = {}
}

onBeforeUnmount(() => { barChart && barChart.dispose() })
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
</style>
