<template>
  <div class="app-container">
    <el-form :model="query" :inline="true">
      <el-form-item label="关键字"><el-input v-model="query.keyword" clearable placeholder="设备编号/设备名称" @keyup.enter="search" /></el-form-item>
      <el-form-item label="线路编码"><el-input v-model="query.lineId" clearable placeholder="如 01" @keyup.enter="search" /></el-form-item>
      <el-form-item label="车站编码"><el-input v-model="query.stationId" clearable placeholder="如 0121" @keyup.enter="search" /></el-form-item>
      <el-form-item label="设备类型"><el-input v-model="query.deviceType" clearable placeholder="如 04 进站AGM" @keyup.enter="search" /></el-form-item>
      <el-form-item label="管理模式"><el-select v-model="query.manageMode" clearable style="width: 140px"><el-option v-for="item in deviceManageModeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="含ACC已删除"><el-switch v-model="query.includeDeleted" /></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="search">查询</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row class="mb8">
      <el-col :span="1.5"><el-button v-hasPermi="['para:device:edit']" type="success" plain icon="Open" :disabled="!selection.length" :loading="modeSaving" @click="batchSetMode('01')">设为正常使用</el-button></el-col>
      <el-col :span="1.5"><el-button v-hasPermi="['para:device:edit']" type="warning" plain icon="TurnOff" :disabled="!selection.length" :loading="modeSaving" @click="batchSetMode('02')">设为节能关机</el-button></el-col>
      <el-col :span="1.5"><el-button v-hasPermi="['para:device:sync']" type="primary" plain icon="Refresh" :loading="syncing" @click="syncNow">立即同步</el-button></el-col>
    </el-row>
    <el-table v-loading="loading" :data="rows" border @selection-change="onSelectionChange">
      <el-table-column type="selection" width="45" align="center" />
      <el-table-column label="序号" width="70" align="center"><template #default="s">{{ (query.pageNum - 1) * query.pageSize + s.$index + 1 }}</template></el-table-column>
      <el-table-column label="设备编号" prop="deviceId" width="110" align="center" />
      <el-table-column label="设备名称" prop="deviceName" width="110" align="center" />
      <el-table-column label="线路" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ row.lineId }}-{{ row.lineName || '-' }}</template></el-table-column>
      <el-table-column label="车站" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ row.stationId }}-{{ row.stationName || '-' }}</template></el-table-column>
      <el-table-column label="设备类型" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ row.deviceType }}-{{ row.deviceTypeName || '-' }}</template></el-table-column>
      <el-table-column label="IP:端口" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.ipAddr || '-' }}<template v-if="row.port">:{{ row.port }}</template></template></el-table-column>
      <el-table-column label="管理模式" width="110" align="center"><template #default="{ row }"><el-tag :type="deviceManageModeMap[row.manageMode]?.tag || 'info'">{{ deviceManageModeLabel(row.manageMode) }}</el-tag></template></el-table-column>
      <el-table-column label="ACC管理级别" prop="accManageLevel" width="110" align="center" />
      <el-table-column label="虚拟设备" width="90" align="center"><template #default="{ row }">{{ row.isVirtual === '1' ? '是' : '否' }}</template></el-table-column>
      <el-table-column label="ACC状态" width="100" align="center"><template #default="{ row }"><el-tag :type="row.accIsDeleted === '1' ? 'danger' : 'info'" size="small">{{ row.accIsDeleted === '1' ? 'ACC已删除' : '正常' }}</el-tag></template></el-table-column>
      <el-table-column label="同步时间" width="170" align="center"><template #default="{ row }">{{ parseTime(row.syncTime) || '-' }}</template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
  </div>
</template>
<script setup name="DeviceInfo">
import { listDevices, syncDevices, updateDeviceManageMode } from '@/api/para/device'
import { deviceManageModeOptions, deviceManageModeMap, deviceManageModeLabel } from '@/utils/deviceManageMode'
// 台账数据 ACC 同步只读；页面只维护业主自定义管理模式（01 正常使用 / 02 节能关机）。
const { proxy } = getCurrentInstance(); const loading = ref(false), modeSaving = ref(false), syncing = ref(false)
const rows = ref([]), total = ref(0), selection = ref([])
const query = reactive({ keyword: '', lineId: '', stationId: '', deviceType: '', manageMode: '', includeDeleted: false, pageNum: 1, pageSize: 10 })
function load() {
  loading.value = true
  listDevices({ ...query }).then((r) => {
    rows.value = r.data?.list || []
    total.value = Number(r.data?.total || 0)
  }).finally(() => (loading.value = false))
}
function search() { query.pageNum = 1; load() }
function resetQuery() { Object.assign(query, { keyword: '', lineId: '', stationId: '', deviceType: '', manageMode: '', includeDeleted: false, pageNum: 1 }); load() }
function onSelectionChange(val) { selection.value = val }
function batchSetMode(manageMode) {
  const label = deviceManageModeLabel(manageMode)
  proxy.$modal.confirm(`确认把选中的 ${selection.value.length} 台设备设为「${label}」吗？`).then(() => {
    modeSaving.value = true
    const deviceIds = selection.value.map((row) => row.deviceId)
    updateDeviceManageMode({ deviceIds, manageMode }).then((r) => {
      proxy.$modal.msgSuccess(`已更新 ${Number(r.data || 0)} 台设备`)
      load()
    }).finally(() => (modeSaving.value = false))
  }).catch(() => {})
}
function syncNow() {
  proxy.$modal.confirm('立即从 ACC 全量同步设备台账？同步约需数十秒，期间请勿重复点击。').then(() => {
    syncing.value = true
    syncDevices().then((r) => {
      const d = r.data || {}
      proxy.$modal.msgSuccess(`同步完成：拉取 ${d.fetched ?? '-'} 条，落库 ${d.merged ?? '-'} 条，耗时 ${Math.round((d.elapsedMillis || 0) / 1000)} 秒`)
      load()
    }).finally(() => (syncing.value = false))
  }).catch(() => {})
}
onMounted(load)
</script>
