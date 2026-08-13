<template>
  <div class="app-container">
    <el-table v-loading="loading" :data="stationList" border>
      <el-table-column label="序号" width="70" align="center">
        <template #default="scope">
          {{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column label="线路代码" prop="ownerLineId" min-width="140" align="center" />
      <el-table-column label="车站代码" prop="stationCode" min-width="140" align="center" />
      <el-table-column label="车站代码中文" prop="stationNm" min-width="220" align="center" show-overflow-tooltip />
      <el-table-column label="车站代码英文" prop="stationENm" min-width="220" align="center" show-overflow-tooltip />
      <el-table-column label="是否换乘" min-width="120" align="center">
        <template #default="scope">
          {{ scope.row.transferYn === 'Y' ? '是' : '否' }}
        </template>
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

<script setup name="StationInfo">
import { listStationInfo } from '@/api/para/station'

const loading = ref(false)
const total = ref(0)
const stationList = ref([])
const queryParams = reactive({ pageNum: 1, pageSize: 20 })

function getList() {
  loading.value = true
  listStationInfo(queryParams)
    .then((response) => {
      const page = response.data || {}
      stationList.value = page.list || []
      total.value = Number(page.total || 0)
    })
    .finally(() => { loading.value = false })
}

onMounted(getList)
</script>
