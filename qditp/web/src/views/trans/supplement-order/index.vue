<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="交易日期" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="补款单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入补款单号（SP 开头）" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="逻辑卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="用户ID" prop="thirdUserId">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入第三方用户ID" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付状态" prop="payStatus">
        <el-select v-model="queryParams.payStatus" clearable placeholder="全部" style="width: 150px">
          <el-option label="待支付 (INIT)" value="INIT" />
          <el-option label="支付中 (PROCESSING)" value="PROCESSING" />
          <el-option label="已支付 (SUCCESS)" value="SUCCESS" />
          <el-option label="已关单 (CLOSED)" value="CLOSED" />
          <el-option label="支付失败 (FAIL)" value="FAIL" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="orders" border>
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
      <el-table-column label="补款单号" prop="orderNo" min-width="220" show-overflow-tooltip />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="用户ID" prop="thirdUserId" min-width="160" show-overflow-tooltip />
      <el-table-column label="覆盖笔数" prop="orderCount" width="90" align="center" />
      <el-table-column label="补款总额" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
      <el-table-column label="支付状态" width="110" align="center"><template #default="{ row }"><el-tag :type="payStatusTagType(row.payStatus)">{{ formatPayStatus(row.payStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="签约渠道" prop="signChannelCode" width="100" align="center" />
      <el-table-column label="支付商户" prop="paymentVendor" width="110" align="center" />
      <el-table-column label="商户订单号" prop="merchantOrderNo" min-width="170" show-overflow-tooltip />
      <el-table-column label="交易日期" prop="txnDate" width="100" align="center" />
      <el-table-column label="支付时间" prop="payTime" width="170" align="center" />
      <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
      <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="SupplementOrderPage">
import { listSupplementOrders } from '@/api/trans/supplementOrder'
import { defaultTodayRange } from '@/utils/dateRange'

/** 交易日期口径（TXN_DATE 存储格式 yyyyMMdd，后端兼容 yyyy-MM-dd 输入）。 */
const DATE_FORMAT = 'YYYY-MM-DD'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const orders = ref([])
const queryError = ref('')
const filterNotice = ref('')
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const queryParams = reactive({
  orderNo: '', cardId: '', thirdUserId: '', payStatus: '', pageNum: 1, pageSize: 10
})

function buildQuery() {
  const { pageNum, pageSize, ...rest } = queryParams
  return {
    ...rest,
    txnDateStart: dateRange.value?.[0],
    txnDateEnd: dateRange.value?.[1],
    pageNum, pageSize
  }
}

function hasSearchScope() {
  return Boolean(queryParams.orderNo?.trim() || queryParams.cardId?.trim()
    || queryParams.thirdUserId?.trim() || (dateRange.value?.[0] && dateRange.value?.[1]))
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    orders.value = []
    total.value = 0
    filterNotice.value = '请至少填写补款单号 / 逻辑卡号 / 用户ID，或完整的交易日期范围'
    return
  }
  loading.value = true
  listSupplementOrders(buildQuery()).then((response) => {
    const page = response.data || {}
    orders.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) {
    proxy.$modal.msgWarning('请填写补款单标识，或完整的交易日期范围')
    return
  }
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  dateRange.value = defaultTodayRange(DATE_FORMAT)
  orders.value = []
  total.value = 0
}

function formatAmount(value) { return value == null || value === '' ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
function formatPayStatus(value) {
  return ({ INIT: '待支付', PROCESSING: '支付中', SUCCESS: '已支付', CLOSED: '已关单', FAIL: '支付失败' })[value] || value || '-'
}
function payStatusTagType(value) {
  return ({ INIT: 'info', PROCESSING: 'warning', SUCCESS: 'success', CLOSED: 'info', FAIL: 'danger' })[value] || 'info'
}
</script>
