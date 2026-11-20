<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="线路代码">
        <el-input v-model="queryParams.lineCode" clearable placeholder="请输入线路代码" style="width: 160px"
                  @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="中文线路名">
        <el-input v-model="queryParams.lineNm" clearable placeholder="请输入中文线路名" style="width: 200px"
                  @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="lineList" border>
      <el-table-column label="序号" type="index" width="70" align="center">
        <template #default="scope">
          {{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column label="线路代码" prop="lineCode" min-width="140" align="center" />
      <el-table-column label="中文线路名称" prop="lineNm" min-width="220" align="center" show-overflow-tooltip />
      <el-table-column label="英文线路名称" prop="lineENm" min-width="260" align="center" show-overflow-tooltip />
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

<script setup name="LineInfo">
import { listLineInfo } from '@/api/para/line'

const loading = ref(false)
const total = ref(0)
const lineList = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  lineCode: '',
  lineNm: ''
})

/** 查询当前生效路网版本的线路信息。 */
function getList() {
  loading.value = true
  listLineInfo(queryParams)
    .then((response) => {
      const page = response.data || {}
      lineList.value = page.list || []
      total.value = Number(page.total || 0)
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.pageNum = 1
  queryParams.lineCode = ''
  queryParams.lineNm = ''
  getList()
}

onMounted(getList)
</script>
