<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="设备编号" prop="deviceId">
        <el-input v-model="queryParams.deviceId" placeholder="支持模糊匹配" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="车站编码" prop="stationCode">
        <el-input v-model="queryParams.stationCode" placeholder="支持模糊匹配" clearable style="width: 140px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="同步状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 120px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAIL" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button icon="RefreshRight" :loading="loading" @click="getList">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="设备编号" prop="deviceId" width="130" align="center" />
      <el-table-column label="车站编码" prop="stationCode" width="110" align="center" />
      <el-table-column label="同步状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAIL' ? 'danger' : 'info'">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="响应码" prop="respRetCode" width="100" align="center">
        <template #default="{ row }">{{ row.respRetCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="响应消息" prop="respRetMsg" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.respRetMsg || '-' }}</template>
      </el-table-column>
      <el-table-column label="返回版本数" prop="respKeyVersionCount" width="110" align="center">
        <template #default="{ row }">{{ row.respKeyVersionCount ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="处理耗时(ms)" prop="processDurationMs" width="120" align="center">
        <template #default="{ row }">{{ row.processDurationMs ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="最近同步时间" prop="reqSynKeyDate" width="170" align="center" />
      <el-table-column label="首次注册时间" prop="regDate" width="170" align="center" />
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" icon="View" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="detailVisible" title="密钥版本上报明细" width="700px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="设备编号">{{ currentRow.deviceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="车站编码">{{ currentRow.stationCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="同步状态">{{ statusLabel(currentRow.status) }}</el-descriptions-item>
        <el-descriptions-item label="响应码">{{ currentRow.respRetCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="响应消息">{{ currentRow.respRetMsg || '-' }}</el-descriptions-item>
        <el-descriptions-item label="返回版本数">{{ currentRow.respKeyVersionCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理耗时(ms)">{{ currentRow.processDurationMs ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近同步时间">{{ currentRow.reqSynKeyDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="首次注册时间">{{ currentRow.regDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上报内容（AGM_KEY_CURVER_LIST）">
          <pre style="margin:0;white-space:pre-wrap;word-break:break-all;">{{ currentRow.agmKeyCurverList || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup name="DeviceKeySync">
import { listDeviceKeySync } from '@/api/monitor/deviceKeySync'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const list = ref([])
const detailVisible = ref(false)
const currentRow = ref({})

const queryParams = reactive({
  deviceId: '',
  stationCode: '',
  status: '',
  pageNum: 1,
  pageSize: 10
})

function statusLabel(status) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAIL') return '失败'
  return status || '-'
}

function getList() {
  loading.value = true
  listDeviceKeySync(queryParams)
    .then((response) => {
      const data = response.data || {}
      list.value = data.list || []
      total.value = Number(data.total || 0)
    })
    .finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  getList()
}

function openDetail(row) {
  currentRow.value = row
  detailVisible.value = true
}

onMounted(getList)
</script>
