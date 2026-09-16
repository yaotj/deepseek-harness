<template>
  <div class="app-container">
    <el-row :gutter="20" class="mb8">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">服务总数</div>
          <div class="stat-value">{{ summary.total || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">正常（UP）</div>
          <div class="stat-value" style="color:#67C23A">{{ summary.upCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">异常（DOWN/UNKNOWN）</div>
          <div class="stat-value" style="color:#F56C6C">{{ summary.downCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">最近检查时间</div>
          <div class="stat-value" style="font-size:16px">{{ summary.checkedAt || '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row class="mb8">
      <el-button type="primary" icon="Refresh" :loading="loading" @click="getList">手动刷新</el-button>
      <span class="auto-refresh-tip">每 30 秒自动刷新</span>
    </el-row>

    <el-table v-loading="loading" :data="serviceList" border>
      <el-table-column label="序号" type="index" width="70" align="center" />
      <el-table-column label="服务" prop="displayName" min-width="160" align="center" />
      <el-table-column label="服务标识" prop="serviceName" width="150" align="center" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="响应时间" width="110" align="center">
        <template #default="{ row }">{{ row.responseTimeMs >= 0 ? row.responseTimeMs + ' ms' : '-' }}</template>
      </el-table-column>
      <el-table-column label="探活地址" prop="healthUrl" min-width="240" align="center" show-overflow-tooltip />
      <el-table-column label="备注" prop="message" min-width="160" align="center">
        <template #default="{ row }">{{ row.message || '-' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup name="ServiceStatus">
import { listServiceStatus } from '@/api/monitor/serviceStatus'

const loading = ref(false)
const serviceList = ref([])
const summary = ref({})
let timer = null

function tagType(status) {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'danger'
  return 'info'
}

function getList() {
  loading.value = true
  listServiceStatus()
    .then((response) => {
      serviceList.value = response.list || []
      summary.value = response || {}
    })
    .finally(() => { loading.value = false })
}

onMounted(() => {
  getList()
  timer = setInterval(getList, 30000)
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
  line-height: 32px;
  color: #909399;
  font-size: 13px;
}
</style>
