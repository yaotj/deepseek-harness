<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="签约时间" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="用户ID" prop="thirdUserId">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入第三方用户ID" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="签约状态" prop="signStatus">
        <el-select v-model="queryParams.signStatus" clearable placeholder="全部" style="width: 140px">
          <el-option label="已签约 (SIGNED)" value="SIGNED" />
          <el-option label="已解约 (TERMINATED)" value="TERMINATED" />
        </el-select>
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
          :notice="filterNotice"
          :filters="queryParams"
          :range="dateRange"
          :value-format="DATE_FORMAT"
          @update:range="dateRange = $event"
          @relax="handleQuery"
          @clear="resetQuery"
          @retry="getList"
        />
      </template>
      <el-table-column label="用户ID" prop="thirdUserId" min-width="170" show-overflow-tooltip />
      <el-table-column label="卡号" prop="cardId" min-width="170" show-overflow-tooltip />
      <el-table-column label="卡类型" prop="cardType" width="90" align="center" />
      <el-table-column label="签约状态" width="110" align="center"><template #default="{ row }"><el-tag :type="row.signStatus === 'SIGNED' ? 'success' : 'info'">{{ row.signStatus === 'SIGNED' ? '已签约' : '已解约' }}</el-tag></template></el-table-column>
      <el-table-column label="我方协议号" prop="agreementCode" min-width="180" show-overflow-tooltip />
      <el-table-column label="渠道协议号" prop="channelAgreementCode" min-width="180" show-overflow-tooltip />
      <el-table-column label="渠道账号" prop="channelUserAccount" min-width="150" show-overflow-tooltip />
      <el-table-column label="签约时间" prop="signTime" width="170" align="center" />
      <el-table-column label="解约时间" prop="terminationTime" width="170" align="center" />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="AlipaySignedUsers">
import { listAlipaySignedUsers } from '@/api/trans/alipayChannel'
import { defaultTodayRange } from '@/utils/dateRange'

const DATE_FORMAT = 'YYYY-MM-DD'
const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const rows = ref([])
const queryError = ref('')
const filterNotice = ref('')
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const queryParams = reactive({ thirdUserId: '', cardId: '', signStatus: '', pageNum: 1, pageSize: 10 })

function buildQuery() {
  return {
    ...queryParams,
    signTimeBegin: dateRange.value?.[0],
    signTimeEnd: dateRange.value?.[1]
  }
}

function hasSearchScope() {
  return Boolean(queryParams.thirdUserId?.trim() || queryParams.cardId?.trim()
    || (dateRange.value?.[0] && dateRange.value?.[1]))
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    rows.value = []
    total.value = 0
    filterNotice.value = '请至少填写用户ID / 卡号，或完整的签约时间范围'
    return
  }
  loading.value = true
  listAlipaySignedUsers(buildQuery()).then((response) => {
    const page = response.data || {}
    rows.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) {
    proxy.$modal.msgWarning('请填写用户标识，或完整的签约时间范围')
    return
  }
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  dateRange.value = defaultTodayRange(DATE_FORMAT)
  rows.value = []
  total.value = 0
}
</script>
