<template>
  <div class="app-container">
    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="渠道退款记录（ALIPAY_REFUND_TXN_DETAIL）：用户/卡维度按原订单号到「交易明细」页回查；金额单位分。" />
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="交易日期" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="退款单号" prop="refundOrderNo">
        <el-input v-model="queryParams.refundOrderNo" placeholder="请输入退款单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="原订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入原订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="退款状态" prop="refundStatus">
        <el-select v-model="queryParams.refundStatus" clearable placeholder="全部" style="width: 140px">
          <el-option label="待处理 (INIT)" value="INIT" />
          <el-option label="处理中 (PROCESSING)" value="PROCESSING" />
          <el-option label="成功 (SUCCESS)" value="SUCCESS" />
          <el-option label="失败 (FAIL)" value="FAIL" />
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
      <el-table-column label="退款单号" prop="refundOrderNo" min-width="200" show-overflow-tooltip />
      <el-table-column label="原订单号" prop="orderNo" min-width="200" show-overflow-tooltip />
      <el-table-column label="退款状态" width="110" align="center"><template #default="{ row }"><el-tag :type="statusTagType(row.refundStatus)">{{ formatStatus(row.refundStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="退款金额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.refundAmount) }}</template></el-table-column>
      <el-table-column label="退款原因" prop="refundReason" min-width="150" show-overflow-tooltip />
      <el-table-column label="渠道退款号" prop="channelRefundNo" min-width="170" show-overflow-tooltip />
      <el-table-column label="重试次数" prop="requestCount" width="90" align="center" />
      <el-table-column label="操作者" prop="operator" width="110" align="center" />
      <el-table-column label="退款时间" prop="refundTime" width="170" align="center" />
      <el-table-column label="交易日期" prop="txnDate" width="100" align="center" />
      <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="AlipayRefundRecords">
import { listAlipayRefundRecords } from '@/api/trans/alipayChannel'
import { defaultTodayRange } from '@/utils/dateRange'

const DATE_FORMAT = 'YYYY-MM-DD'
const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const rows = ref([])
const queryError = ref('')
const filterNotice = ref('')
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const queryParams = reactive({ refundOrderNo: '', orderNo: '', refundStatus: '', pageNum: 1, pageSize: 10 })

function buildQuery() {
  return {
    ...queryParams,
    txnDateBegin: dateRange.value?.[0],
    txnDateEnd: dateRange.value?.[1]
  }
}

function hasSearchScope() {
  return Boolean(queryParams.refundOrderNo?.trim() || queryParams.orderNo?.trim()
    || (dateRange.value?.[0] && dateRange.value?.[1]))
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    rows.value = []
    total.value = 0
    filterNotice.value = '请至少填写退款单号 / 原订单号，或完整的交易日期范围'
    return
  }
  loading.value = true
  listAlipayRefundRecords(buildQuery()).then((response) => {
    const page = response.data || {}
    rows.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) {
    proxy.$modal.msgWarning('请填写单号标识，或完整的交易日期范围')
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
function formatStatus(value) {
  return ({ INIT: '待处理', PROCESSING: '处理中', SUCCESS: '成功', FAIL: '失败' })[value] || value || '-'
}
function statusTagType(value) {
  return ({ INIT: 'info', PROCESSING: 'warning', SUCCESS: 'success', FAIL: 'danger' })[value] || 'info'
}
</script>
