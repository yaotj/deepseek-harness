<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="进站线路">
        <el-select v-model="queryParams.entryLineCode" filterable clearable placeholder="请选择进站线路" style="width: 220px" @change="handleEntryLineChange">
          <el-option v-for="line in lines" :key="line.lineCode" :label="lineLabel(line)" :value="line.lineCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="出站线路">
        <el-select v-model="queryParams.exitLineCode" filterable clearable placeholder="请选择出站线路" style="width: 220px" @change="handleExitLineChange">
          <el-option v-for="line in lines" :key="line.lineCode" :label="lineLabel(line)" :value="line.lineCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="进站车站">
        <el-select v-model="queryParams.entryStationCode" filterable clearable placeholder="请选择进站车站" style="width: 250px">
          <el-option v-for="station in entryStations" :key="station.stationCode" :label="stationLabel(station)" :value="station.stationCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="出站车站">
        <el-select v-model="queryParams.exitStationCode" filterable clearable placeholder="请选择出站车站" style="width: 250px">
          <el-option v-for="station in exitStations" :key="station.stationCode" :label="stationLabel(station)" :value="station.stationCode" />
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
import { listBaseFares, listBaseFareStations, listBaseFareLines } from '@/api/para/baseFare'
import { formatCodeName } from '@/utils/codeLabel'

const loading = ref(false)
const lines = ref([])
const entryStations = ref([])
const exitStations = ref([])
const fares = ref([])
const total = ref(0)
const queryParams = reactive({
  entryLineCode: '',
  exitLineCode: '',
  entryStationCode: '',
  exitStationCode: '',
  pageNum: 1,
  pageSize: 10
})

function getList() {
  loading.value = true
  listBaseFares({ ...queryParams }).then((response) => {
    const page = response.data || {}
    fares.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

/** 拉取车站选项；lineCode 为空时返回全部线路车站。 */
function fetchStations(lineCode) {
  const params = lineCode ? { lineCode } : {}
  return listBaseFareStations(params).then((response) => response.data || [])
}

function getEntryStations() {
  fetchStations(queryParams.entryLineCode).then((data) => { entryStations.value = data })
}

function getExitStations() {
  fetchStations(queryParams.exitLineCode).then((data) => { exitStations.value = data })
}

function getLines() {
  listBaseFareLines().then((response) => {
    lines.value = response.data || []
  })
}

function handleEntryLineChange() {
  queryParams.entryStationCode = ''
  getEntryStations()
  handleQuery()
}

function handleExitLineChange() {
  queryParams.exitStationCode = ''
  getExitStations()
  handleQuery()
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.entryLineCode = ''
  queryParams.exitLineCode = ''
  queryParams.entryStationCode = ''
  queryParams.exitStationCode = ''
  getEntryStations()
  getExitStations()
  handleQuery()
}

function lineLabel(line) { return formatCodeName(line?.lineCode, line?.lineName) }
function stationLabel(station) { return formatCodeName(station?.stationCode, station?.stationName) }
function stationText(name, code) { return formatCodeName(code, name) }
function formatAmount(value) { return value == null ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }

getLines()
getEntryStations()
getExitStations()
getList()
</script>
