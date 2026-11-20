<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="线路代码">
        <el-select v-model="queryParams.ownerLineId" clearable placeholder="请选择线路" style="width: 140px">
          <el-option v-for="line in lineOptions" :key="line.lineCode" :label="line.lineNm" :value="line.lineCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="车站代码">
        <el-input v-model="queryParams.stationCode" clearable placeholder="请输入车站代码" style="width: 140px"
                  @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="车站中文名">
        <el-input v-model="queryParams.stationNm" clearable placeholder="请输入车站中文名" style="width: 180px"
                  @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="车站英文名">
        <el-input v-model="queryParams.stationENm" clearable placeholder="请输入车站英文名" style="width: 180px"
                  @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

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
      <el-table-column label="车站类型" prop="stationType" min-width="120" align="center" />
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
import { listLineInfo } from '@/api/para/line'

const loading = ref(false)
const total = ref(0)
const stationList = ref([])
const lineOptions = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  ownerLineId: '',
  stationCode: '',
  stationNm: '',
  stationENm: ''
})

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

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.pageNum = 1
  queryParams.ownerLineId = ''
  queryParams.stationCode = ''
  queryParams.stationNm = ''
  queryParams.stationENm = ''
  getList()
}

/** 加载线路下拉选项。 */
function loadLineOptions() {
  listLineInfo({ pageNum: 1, pageSize: 100 }).then((res) => {
    lineOptions.value = (res.data && res.data.list) || []
  })
}

onMounted(() => {
  loadLineOptions()
  getList()
})
</script>
