<template>
  <div>
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="账单日期" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="支付渠道" prop="paymentChannel">
        <el-input v-model="queryParams.paymentChannel" placeholder="如 支付宝 / 微信 / 银联" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付商户" prop="paymentVendor">
        <el-input v-model="queryParams.paymentVendor" placeholder="请输入商户编码" clearable style="width: 180px" @keyup.enter="handleQuery" />
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
      <el-table-column label="渠道" prop="paymentChannel" width="100" align="center" />
      <el-table-column label="商户" prop="paymentVendor" min-width="140" show-overflow-tooltip />
      <el-table-column label="渠道名" prop="channelName" min-width="140" show-overflow-tooltip />
      <el-table-column label="交易笔数" prop="txnCount" width="110" align="right" />
      <el-table-column label="订单金额" prop="orderAmount" width="130" align="right" />
      <el-table-column label="交易金额" prop="transactionAmount" width="130" align="right" />
      <el-table-column label="手续费" prop="feeAmount" width="120" align="right" />
      <el-table-column label="结算金额" prop="settlementAmount" width="130" align="right" />
      <el-table-column label="优惠金额" prop="discountAmount" width="130" align="right" />
    </el-table>
  </div>
</template>

<script setup>
import { listReconClearing } from '@/api/report/reconReport'
import { defaultTodayRange } from '@/utils/dateRange'

const DATE_FORMAT = 'YYYY-MM-DD'
const { proxy } = getCurrentInstance()
const loading = ref(false)
const rows = ref([])
const queryError = ref('')
const filterNotice = ref('')
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const queryParams = reactive({ paymentChannel: '', paymentVendor: '' })

function hasSearchScope() {
  return Boolean(dateRange.value?.[0] && dateRange.value?.[1])
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    rows.value = []
    filterNotice.value = '请选择完整的账单日期范围'
    return
  }
  loading.value = true
  listReconClearing({
    billDateStart: dateRange.value[0],
    billDateEnd: dateRange.value[1],
    ...queryParams
  }).then((response) => {
    rows.value = response.data?.list || []
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) { proxy.$modal.msgWarning('请选择完整的账单日期范围'); return }
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  dateRange.value = defaultTodayRange(DATE_FORMAT)
  rows.value = []
}
</script>
