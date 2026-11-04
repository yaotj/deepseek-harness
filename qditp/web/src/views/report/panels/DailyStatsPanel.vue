<template>
  <div>
    <el-alert v-if="mode === 'HOUR'" class="mb8" type="info" :closable="false" show-icon
      title="按当天交易统计：只支持单日范围，按小时桶汇总（PAY_TIME 未知小时归入 '--'）。" />
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="交易日期" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
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
      <el-table-column :label="mode === 'HOUR' ? '小时桶' : '交易日期'" prop="groupKey" width="110" align="center" />
      <el-table-column v-if="mode === 'VENDOR'" label="支付账户/商户" prop="paymentVendor" min-width="160" show-overflow-tooltip />
      <el-table-column label="交易笔数" prop="txnCount" width="110" align="right" />
      <el-table-column label="金额合计" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.amountSum) }}</template></el-table-column>
      <el-table-column label="现金金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.cashAmountSum) }}</template></el-table-column>
      <el-table-column label="优惠金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.couponAmountSum) }}</template></el-table-column>
      <el-table-column label="退款金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.refundAmountSum) }}</template></el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { listReconDailyStats } from '@/api/report/reconReport'
import { defaultTodayRange } from '@/utils/dateRange'

const DATE_FORMAT = 'YYYY-MM-DD'
const props = defineProps({
  /** VENDOR=按支付账户汇总；HOUR=当天交易分时。 */
  mode: { type: String, default: 'VENDOR' }
})

const { proxy } = getCurrentInstance()
const loading = ref(false)
const rows = ref([])
const queryError = ref('')
const filterNotice = ref('')
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const queryParams = reactive({})

function hasSearchScope() {
  return Boolean(dateRange.value?.[0] && dateRange.value?.[1])
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    rows.value = []
    filterNotice.value = '请选择完整的交易日期范围'
    return
  }
  loading.value = true
  listReconDailyStats({
    groupBy: props.mode,
    dateStart: dateRange.value[0],
    dateEnd: dateRange.value[1]
  }).then((response) => {
    rows.value = response.data?.list || []
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) { proxy.$modal.msgWarning('请选择完整的交易日期范围'); return }
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  dateRange.value = defaultTodayRange(DATE_FORMAT)
  rows.value = []
}

function formatAmount(value) { return value == null || value === '' ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
</script>
