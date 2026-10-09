<template>
  <div class="app-container">
    <el-table v-loading="loading" :data="versionList" border>
      <el-table-column label="序号" width="70" align="center">
        <template #default="scope">
          {{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column label="参数类型" min-width="160" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.paraTypeName === '费率' ? 'success' : 'primary'" disable-transitions>
            {{ scope.row.paraTypeName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本号" min-width="180" align="center">
        <template #default="scope">
          {{ scope.row.versionNo }}
          <span v-if="scope.row.paraTypeName === '路网拓扑'" class="version-note">（线路 / 车站代码共用）</span>
        </template>
      </el-table-column>
      <el-table-column label="参数文件名" prop="fileName" min-width="300" align="center" show-overflow-tooltip />
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

<style scoped>
.version-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
