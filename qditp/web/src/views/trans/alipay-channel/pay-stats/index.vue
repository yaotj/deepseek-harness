<template>
  <div class="app-container">
    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="支付宝出行渠道支付统计：扣费权威表 GATE_TXN_PAY 按发行渠道 07（支付宝出行）圈定，金额单位分；用户维度明细可下钻「交易明细」页。" />
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="交易日期" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入扣费订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="扣费状态" prop="debitStatus">
        <el-select v-model="queryParams.debitStatus" clearable placeholder="全部" style="width: 140px">
          <el-option label="成功 (SUCCESS)" value="SUCCESS" />
          <el-option label="处理中 (PROCESSING)" value="PROCESSING" />
          <el-option label="待补扣 (RETRY)" value="RETRY" />
          <el-option label="失败 (FAIL)" value="FAIL" />
          <el-option label="已关闭 (CLOSED)" value="CLOSED" />
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
      <el-table-column label="订单号" prop="orderNo" min-width="200" show-overflow-tooltip />
      <el-table-column label="卡号" prop="cardId" min-width="170" show-overflow-tooltip />
      <el-table-column label="进站" width="100" align="center"><template #default="{ row }">{{ row.entryStationName || row.inStation || '-' }}</template></el-table-column>
      <el-table-column label="出站" width="100" align="center"><template #default="{ row }">{{ row.exitStationName || row.outStation || '-' }}</template></el-table-column>
      <el-table-column label="票款" width="95" align="right"><template #default="{ row }">{{ formatAmount(row.trxAmount) }}</template></el-table-column>
      <el-table-column label="超时费" width="90" align="right"><template #default="{ row }">{{ (row.overtimeAmount ?? 0) > 0 ? formatAmount(row.overtimeAmount) : '-' }}</template></el-table-column>
      <el-table-column label="合计" width="95" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
      <el-table-column label="扣费状态" width="100" align="center"><template #default="{ row }"><el-tag :type="row.debitStatus === 'SUCCESS' ? 'success' : 'warning'">{{ row.debitStatus }}</el-tag></template></el-table-column>
      <el-table-column label="发行渠道" width="90" align="center"><template #default="{ row }">{{ row.issueChannelCode === '07' ? '支付宝出行' : (row.issueChannelCode || '-') }}</template></el-table-column>
      <el-table-column label="交易日期" prop="txnDate" width="100" align="center" />
      <el-table-column label="出站时间" prop="outTime" width="170" align="center" />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="AlipayPayStats">
import { listGateTxnPays } from '@/api/trans/userSearch'
import { defaultTodayRange } from '@/utils/dateRange'

const DATE_FORMAT = 'YYYY-MM-DD'
const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const rows = ref([])
const queryError = ref('')
const filterNotice = ref('')
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const queryParams = reactive({ orderNo: '', cardId: '', debitStatus: '', pageNum: 1, pageSize: 10 })

function buildQuery() {
  // 支付宝出行渠道口径：ISSUE_CHANNEL_CODE 固定 07，NEVER 由查询区改动。
  return {
    ...queryParams,
    issueChannelCode: '07',
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1]
  }
}

function hasSearchScope() {
  return Boolean(queryParams.orderNo?.trim() || queryParams.cardId?.trim()
    || (dateRange.value?.[0] && dateRange.value?.[1]))
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    rows.value = []
    total.value = 0
    filterNotice.value = '请至少填写订单号 / 卡号，或完整的交易日期范围'
    return
  }
  loading.value = true
  listGateTxnPays(buildQuery()).then((response) => {
    const page = response.data || {}
    rows.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) {
    proxy.$modal.msgWarning('请填写订单标识，或完整的交易日期范围')
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

function formatAmount(value) { return value == null || value === '' ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
</script>
