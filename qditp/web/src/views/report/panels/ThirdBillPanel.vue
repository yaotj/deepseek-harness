<template>
  <div>
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="账单日期" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="支付渠道" prop="paymentChannel">
        <el-input v-model="queryParams.paymentChannel" placeholder="如 支付宝 / 微信 / 银联" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商户订单号" prop="merchantOrderNo">
        <el-input v-model="queryParams.merchantOrderNo" placeholder="请输入商户订单号" clearable style="width: 200px" @keyup.enter="handleQuery" />
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
      <el-table-column label="账单日" prop="billDate" width="100" align="center" />
      <el-table-column label="渠道" prop="paymentChannel" width="90" align="center" />
      <el-table-column label="商户" prop="paymentVendor" min-width="120" show-overflow-tooltip />
      <el-table-column label="渠道名" prop="channelName" min-width="120" show-overflow-tooltip />
      <el-table-column label="业务类型" prop="businessType" width="90" align="center" />
      <el-table-column label="交易状态" prop="tradeStatus" width="90" align="center" />
      <el-table-column label="交易时间" prop="tradeTime" width="110" align="center" />
      <el-table-column label="清分日" prop="clearingDate" width="100" align="center" />
      <el-table-column label="商户订单号" prop="merchantOrderNo" min-width="180" show-overflow-tooltip />
      <el-table-column label="渠道流水号" prop="channelTradeNo" min-width="180" show-overflow-tooltip />
      <el-table-column label="订单金额" prop="orderAmount" width="110" align="right" />
      <el-table-column label="手续费" prop="feeAmount" width="95" align="right" />
      <el-table-column label="结算金额" prop="settlementAmount" width="110" align="right" />
      <el-table-column label="币种" prop="currency" width="70" align="center" />
      <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup>
import { listReconThirdBill } from '@/api/report/reconReport'
import { defaultTodayRange } from '@/utils/dateRange'

const DATE_FORMAT = 'YYYY-MM-DD'
const props = defineProps({
  /** 菜单级渠道预设（如「支付宝」），仅作输入框默认值，运营可改。 */
  defaultChannel: { type: String, default: '' }
})
const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const rows = ref([])
const queryError = ref('')
const filterNotice = ref('')
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const queryParams = reactive({ paymentChannel: props.defaultChannel, merchantOrderNo: '', pageNum: 1, pageSize: 10 })

function buildQuery() {
  return {
    billDateStart: dateRange.value?.[0],
    billDateEnd: dateRange.value?.[1],
    ...queryParams
  }
}

function hasSearchScope() {
  return Boolean(dateRange.value?.[0] && dateRange.value?.[1])
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    rows.value = []
    total.value = 0
    filterNotice.value = '请选择完整的账单日期范围'
    return
  }
  loading.value = true
  listReconThirdBill(buildQuery()).then((response) => {
    const page = response.data || {}
    rows.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) { proxy.$modal.msgWarning('请选择完整的账单日期范围'); return }
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
