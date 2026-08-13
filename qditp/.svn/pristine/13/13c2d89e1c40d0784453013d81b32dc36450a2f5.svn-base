<template>
  <div class="app-container">
    <el-table v-loading="loading" :data="versionList" border>
      <el-table-column label="序号" width="70" align="center">
        <template #default="scope">
          {{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column label="线路代码版本号" prop="lineCodeVersion" min-width="220" align="center" />
      <el-table-column label="车站代码版本号" prop="stationCodeVersion" min-width="220" align="center" />
      <el-table-column label="更新时间" min-width="220" align="center">
        <template #default="scope">{{ formatDateTime(scope.row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="生效日期" min-width="220" align="center">
        <template #default="scope">{{ formatEffectiveTime(scope.row.effectiveTime) }}</template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </div>
</template>

<script setup name="LineStationVersion">
import { listLineStationVersion } from '@/api/para/lineStationVersion'

const loading = ref(false)
const total = ref(0)
const versionList = ref([])
const queryParams = reactive({ pageNum: 1, pageSize: 20 })

function formatDateTime(value) {
  return value ? value.replace('T', ' ') : ''
}

function formatEffectiveTime(value) {
  if (!value || value.length !== 14) return value || ''
  return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)} ${value.slice(8, 10)}:${value.slice(10, 12)}:${value.slice(12, 14)}`
}

function getList() {
  loading.value = true
  listLineStationVersion(queryParams)
    .then((response) => {
      const page = response.data || {}
      versionList.value = page.list || []
      total.value = Number(page.total || 0)
    })
    .finally(() => { loading.value = false })
}

onMounted(getList)
</script>
