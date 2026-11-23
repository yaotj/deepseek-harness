<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="交易日期" prop="dateRange">
        <query-time-range v-model="dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入扣费订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="逻辑卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="用户ID" prop="thirdUserId">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入第三方用户ID" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="异常类型" prop="orderExpType">
        <el-select v-model="queryParams.orderExpType" clearable placeholder="单边入+出（1、2）" style="width: 190px">
          <el-option label="单边入 (1)" value="1" />
          <el-option label="单边出 (2)" value="2" />
          <el-option label="单边入站人工 (3)" value="3" />
          <el-option label="单边出站人工 (4)" value="4" />
          <el-option label="双段计费超时 (5)" value="5" />
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
      <el-table-column label="订单号" prop="orderNo" min-width="200" show-overflow-tooltip />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="用户ID" prop="thirdUserId" min-width="160" show-overflow-tooltip />
      <el-table-column label="异常类型" width="110" align="center"><template #default="{ row }">{{ formatExpType(row.orderExpType) }}</template></el-table-column>
      <el-table-column label="进站" width="110" align="center"><template #default="{ row }">{{ row.entryStationName || row.inStation || '-' }}</template></el-table-column>
      <el-table-column label="出站" width="110" align="center"><template #default="{ row }">{{ row.exitStationName || row.outStation || '-' }}</template></el-table-column>
      <el-table-column label="进站时间" prop="inTime" width="170" align="center" />
      <el-table-column label="出站时间" prop="outTime" width="170" align="center" />
      <el-table-column label="票种" prop="trxType" width="80" align="center" />
      <el-table-column label="原价" width="90" align="right"><template #default="{ row }">{{ formatAmount(row.originalFare) }}</template></el-table-column>
      <el-table-column label="扣费金额" width="95" align="right"><template #default="{ row }">{{ formatAmount(row.trxAmount) }}</template></el-table-column>
      <el-table-column label="超时费" width="95" align="right"><template #default="{ row }">{{ (row.overtimeAmount ?? 0) > 0 ? formatAmount(row.overtimeAmount) : '-' }}</template></el-table-column>
      <el-table-column label="扣费状态" width="110" align="center"><template #default="{ row }"><el-tag :type="row.debitStatus === 'SUCCESS' ? 'success' : 'warning'">{{ debitStatusLabel(row.debitStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="票状态" prop="ticketStatus" width="80" align="center" />
      <el-table-column label="离线" width="70" align="center"><template #default="{ row }">{{ row.offlineFlag === 'Y' ? '是' : '否' }}</template></el-table-column>
      <el-table-column label="交易日期" prop="txnDate" width="100" align="center" />
      <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="OneSideQuery">
import { listOneSide } from '@/api/trans/oneSideQuery'
import { defaultTodayRange } from '@/utils/dateRange'
import { debitStatusLabel } from '@/utils/orderStatusLabels'

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
  orderNo: '', cardId: '', thirdUserId: '', orderExpType: '', pageNum: 1, pageSize: 10
})

function buildQuery() {
  const { pageNum, pageSize, ...rest } = queryParams
  return {
    ...rest,
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1],
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
    filterNotice.value = '请至少填写订单号 / 逻辑卡号 / 用户ID，或完整的交易日期范围'
    return
  }
  loading.value = true
  listOneSide(buildQuery()).then((response) => {
    const page = response.data || {}
    orders.value = page.list || []
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
  queryParams.orderExpType = ''
  dateRange.value = defaultTodayRange(DATE_FORMAT)
  orders.value = []
  total.value = 0
}

function formatAmount(value) { return value == null || value === '' ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
function formatExpType(value) {
  return ({ '1': '单边入', '2': '单边出', '3': '单边入站人工', '4': '单边出站人工', '5': '双段计费超时', '0': '正常' })[value] || value || '-'
}
</script>
