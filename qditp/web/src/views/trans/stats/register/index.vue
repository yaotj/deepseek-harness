<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="90px">
      <el-form-item label="注册日期">
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

    <el-row :gutter="20">
      <el-col :span="14">
        <el-table v-loading="loading" :data="statsList" border>
          <el-table-column label="序号" type="index" width="70" align="center" />
          <el-table-column label="票种编码" prop="cardType" width="130" align="center" />
          <el-table-column label="票种名称" prop="cardTypeName" min-width="200" align="center" />
          <el-table-column label="注册量" prop="regCount" width="120" align="center" />
          <el-table-column label="占比" width="120" align="center">
            <template #default="{ row }">{{ ratio(row.regCount) }}</template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="10">
        <div ref="pieRef" style="height: 380px" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="RegStats">
import * as echarts from 'echarts'
import { listRegStats } from '@/api/trans/regStats'

const loading = ref(false)
const statsList = ref([])
const dateRange = ref([])
const pieRef = ref(null)
let pieChart = null

const totalCount = computed(() => statsList.value.reduce((sum, row) => sum + Number(row.regCount || 0), 0))

function ratio(count) {
  const total = totalCount.value
  if (!total) return '0.00%'
  return ((Number(count || 0) / total) * 100).toFixed(2) + '%'
}

function renderPie() {
  if (!pieRef.value) return
  if (!pieChart) {
    pieChart = echarts.init(pieRef.value, 'macarons')
  }
  pieChart.setOption({
    title: { text: '各票种注册量占比', left: 'center' },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: '55%',
      data: statsList.value.map(row => ({ name: row.cardTypeName || row.cardType, value: row.regCount }))
    }]
  })
}

function getList() {
  loading.value = true
  const params = {}
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0]
    params.endDate = dateRange.value[1]
  }
  listRegStats(params)
    .then((response) => {
      statsList.value = response.data || []
      nextTick(renderPie)
    })
    .finally(() => { loading.value = false })
}

function handleQuery() {
  getList()
}

function resetQuery() {
  dateRange.value = []
  getList()
}

onMounted(getList)
onBeforeUnmount(() => { pieChart && pieChart.dispose() })
</script>
