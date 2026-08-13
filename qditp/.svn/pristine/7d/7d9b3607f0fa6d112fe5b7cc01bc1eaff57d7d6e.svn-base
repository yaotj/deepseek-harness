<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="进站车站">
        <el-select v-model="queryParams.entryStationCode" filterable clearable placeholder="请选择进站车站" style="width: 250px">
          <el-option v-for="station in stations" :key="station.stationCode" :label="stationLabel(station)" :value="station.stationCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="出站车站">
        <el-select v-model="queryParams.exitStationCode" filterable clearable placeholder="请选择出站车站" style="width: 250px">
          <el-option v-for="station in stations" :key="station.stationCode" :label="stationLabel(station)" :value="station.stationCode" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert title="展示当前费率版本的基础票价。票价由进出站费率等级关联 FARE_TYPE=0 的基础票价计算，金额单位为元。" type="info" :closable="false" show-icon class="mb8" />

    <el-table v-loading="loading" :data="fares" border>
      <el-table-column label="进站车站" min-width="220">
        <template #default="{ row }">{{ stationText(row.entryStationName, row.entryStationCode) }}</template>
      </el-table-column>
      <el-table-column label="出站车站" min-width="220">
        <template #default="{ row }">{{ stationText(row.exitStationName, row.exitStationCode) }}</template>
      </el-table-column>
      <el-table-column label="基础票价" width="150" align="right">
        <template #default="{ row }">{{ formatAmount(row.ticketPrice) }}</template>
      </el-table-column>
      <el-table-column label="费率等级" prop="fareTier" width="120" align="center" />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="BaseFare">
import { listBaseFares, listBaseFareStations } from '@/api/para/baseFare'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const stations = ref([])
const fares = ref([])
const total = ref(0)
const queryParams = reactive({ entryStationCode: '', exitStationCode: '', pageNum: 1, pageSize: 10 })

function getList() {
  loading.value = true
  listBaseFares({ ...queryParams }).then((response) => {
    const page = response.data || {}
    fares.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function getStations() {
  listBaseFareStations().then((response) => {
    stations.value = response.data || []
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.entryStationCode = ''
  queryParams.exitStationCode = ''
  handleQuery()
}

function stationLabel(station) { return `${station.stationName || '未命名车站'} (${station.stationCode})` }
function stationText(name, code) { return name ? `${name} (${code})` : code || '-' }
function formatAmount(value) { return value == null ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }

getStations()
getList()
</script>
