<template>
  <div class="app-container">
    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="灰名单为风控规则命中台账：记录各卡号触发各风险规则的发生次数与最后发生时间，不参与乘车拦截、不渠道同步。">
    </el-alert>
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="风险规则" prop="ruleName">
        <el-select v-model="queryParams.ruleName" placeholder="全部" clearable style="width: 200px">
          <el-option v-for="r in ruleOptions" :key="r.riskRuleId" :label="r.riskRuleName" :value="r.riskRuleName" />
        </el-select>
      </el-form-item>
      <el-form-item label="更新时间" prop="dateRange">
        <query-time-range v-model="queryParams.dateRange" :value-format="DATE_FORMAT" :allow-all="true" @change="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border>
      <template #empty>
        <empty-state
          :error="queryError"
          :filters="queryParams"
          :range="queryParams.dateRange"
          :value-format="DATE_FORMAT"
          @update:range="queryParams.dateRange = $event"
          @clear="resetQuery"
          @retry="getList"
        />
      </template>
      <el-table-column label="卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="风险规则" prop="ruleName" min-width="200" show-overflow-tooltip />
      <el-table-column label="风险发生次数" prop="riskTimes" width="120" align="center" />
      <el-table-column label="风险发生最后时间" prop="riskLastTime" width="170" align="center">
        <template #default="{ row }">{{ parseTime(row.riskLastTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="管理编码" prop="managerId" width="120" align="center">
        <template #default="{ row }">{{ row.managerId || '-' }}</template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="170" align="center">
        <template #default="{ row }">{{ parseTime(row.updateTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="入库时间" prop="regTime" width="170" align="center">
        <template #default="{ row }">{{ parseTime(row.regTime) || '-' }}</template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="GraylistManagement">
import { listGraylist } from '@/api/para/risk'
import { listRiskRules } from '@/api/para/risk'
import { defaultTodayRange } from '@/utils/dateRange'

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

const loading = ref(false)
const total = ref(0)
const rows = ref([])
const queryError = ref('')
const ruleOptions = ref([])
const queryParams = reactive({
  cardId: '', ruleName: '', dateRange: defaultTodayRange(DATE_FORMAT), pageNum: 1, pageSize: 10
})

function getList() {
  loading.value = true
  queryError.value = ''
  const [updateTimeBegin, updateTimeEnd] = queryParams.dateRange || []
  listGraylist({
    cardId: queryParams.cardId || undefined,
    ruleName: queryParams.ruleName || undefined,
    updateTimeBegin: updateTimeBegin || undefined,
    updateTimeEnd: updateTimeEnd || undefined,
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize
  }).then((response) => {
    const page = response.data || {}
    rows.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function getRuleOptions() {
  listRiskRules({ pageNum: 1, pageSize: 100 }).then((response) => {
    ruleOptions.value = response.data?.list || []
  }).catch(() => { ruleOptions.value = [] })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.cardId = ''
  queryParams.ruleName = ''
  queryParams.dateRange = defaultTodayRange(DATE_FORMAT)
  rows.value = []
  total.value = 0
  getList()
}

onMounted(() => { getRuleOptions(); getList() })
</script>
