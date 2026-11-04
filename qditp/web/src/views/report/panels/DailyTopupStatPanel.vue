<template>
  <div>
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="统计日期" prop="dateRange">
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
      <el-table-column label="统计日期" prop="statDate" width="120" align="center" />
      <el-table-column label="订单总数" prop="orderCount" width="110" align="right" />
      <el-table-column label="支付成功笔数" prop="paidCount" width="120" align="right" />
      <el-table-column label="已发起退款笔数" prop="refundCount" width="130" align="right" />
      <el-table-column label="成功金额合计" width="140" align="right">
        <template #default="{ row }">{{ formatAmount(row.paidTransAmountSum) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { listTvmTopupDaily, listNocashTopup } from '@/api/report/topupReport'
import { defaultTodayRange } from '@/utils/dateRange'

const DATE_FORMAT = 'YYYY-MM-DD'
const props = defineProps({
  /** tvm=TVM 充值日报表（TBL_TVM_ORDER_TOPUP）；nocash=非现金充值数据查询（TBL_BOM_ORDER_PAY 充值）。 */
  mode: { type: String, default: 'tvm' }
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
    filterNotice.value = '请选择完整的统计日期范围'
    return
  }
  loading.value = true
  const params = { dateStart: dateRange.value[0], dateEnd: dateRange.value[1] }
  const api = props.mode === 'nocash' ? listNocashTopup : listTvmTopupDaily
  api(params).then((response) => {
    rows.value = response.data?.list || []
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) { proxy.$modal.msgWarning('请选择完整的统计日期范围'); return }
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  dateRange.value = defaultTodayRange(DATE_FORMAT)
  rows.value = []
}

function formatAmount(value) { return value == null || value === '' ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
</script>
