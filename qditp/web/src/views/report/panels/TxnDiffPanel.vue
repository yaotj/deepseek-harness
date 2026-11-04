<template>
  <div>
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="账单日期" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="支付渠道" prop="payChannel">
        <el-input v-model="queryParams.payChannel" placeholder="如 支付宝 / 微信 / 银联" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入订单号" clearable style="width: 200px" @keyup.enter="handleQuery" />
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
      <el-table-column label="交易类型" prop="txnType" width="90" align="center" />
      <el-table-column label="逻辑卡号" prop="logicCardNo" min-width="170" show-overflow-tooltip />
      <el-table-column label="交易时间" prop="txnDateTime" width="110" align="center" />
      <el-table-column label="原价" width="90" align="right"><template #default="{ row }">{{ formatAmount(row.originalFare) }}</template></el-table-column>
      <el-table-column label="车站" prop="curStationName" min-width="120" show-overflow-tooltip />
      <el-table-column label="设备号" prop="deviceNo" min-width="120" show-overflow-tooltip />
      <el-table-column label="票种" prop="ticketType" width="80" align="center" />
      <el-table-column label="渠道" prop="payChannel" width="100" align="center" />
      <el-table-column label="订单号" prop="orderNo" min-width="180" show-overflow-tooltip />
      <el-table-column label="订单金额" width="100" align="right"><template #default="{ row }">{{ formatAmount(row.orderAmount) }}</template></el-table-column>
      <el-table-column label="实付金额" width="100" align="right"><template #default="{ row }">{{ formatAmount(row.actualAmount) }}</template></el-table-column>
      <el-table-column label="超时费" width="90" align="right"><template #default="{ row }">{{ (row.overtimeAmount ?? 0) > 0 ? formatAmount(row.overtimeAmount) : '-' }}</template></el-table-column>
      <el-table-column label="差异方" prop="diffSide" width="90" align="center" />
      <el-table-column label="失败原因" prop="failReason" min-width="150" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup>
import { listReconTxnDiff } from '@/api/report/reconReport'
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
const queryParams = reactive({ payChannel: props.defaultChannel, orderNo: '', pageNum: 1, pageSize: 10 })

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
  listReconTxnDiff(buildQuery()).then((response) => {
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
