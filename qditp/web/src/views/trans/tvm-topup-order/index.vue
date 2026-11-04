<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="下单时间" prop="dateRange">
        <query-time-range v-model="queryParams.dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入充值订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付中心订单号" prop="payCenterOrderNo">
        <el-input v-model="queryParams.payCenterOrderNo" placeholder="请输入支付中心订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="票卡逻辑号" prop="ticketLogicNum">
        <el-input v-model="queryParams.ticketLogicNum" placeholder="请输入票卡逻辑号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="设备编号" prop="deviceId">
        <el-input v-model="queryParams.deviceId" placeholder="请输入 TVM 设备编号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付状态" prop="status">
        <el-input v-model="queryParams.status" placeholder="1=支付成功" clearable style="width: 130px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="充值回执" prop="topupStatus">
        <el-select v-model="queryParams.topupStatus" clearable placeholder="全部" style="width: 150px">
          <el-option label="成功 (00)" value="00" />
          <el-option label="失败 (01)" value="01" />
          <el-option label="存疑 (02)" value="02" />
          <el-option label="取消 (03)" value="03" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="充值回执为最新一条回执状态；未选「充值回执」时含尚未充值（无回执）订单。" />

    <el-table v-loading="loading" :data="orders" border>
      <template #empty>
        <empty-state
          :error="queryError"
          :notice="filterNotice"
          :filters="queryParams"
          :range="queryParams.dateRange"
          :value-format="DATE_FORMAT"
          @update:range="queryParams.dateRange = $event"
          @relax="handleQuery"
          @clear="resetQuery"
          @retry="getList"
        />
      </template>
      <el-table-column label="充值订单号" prop="orderNo" min-width="200" show-overflow-tooltip />
      <el-table-column label="票卡逻辑号" prop="ticketLogicNum" min-width="180" show-overflow-tooltip />
      <el-table-column label="充值金额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.transAmount) }}</template></el-table-column>
      <el-table-column label="充值前余额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.beforeAmount) }}</template></el-table-column>
      <el-table-column label="充值后余额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.afterAmount) }}</template></el-table-column>
      <el-table-column label="支付状态" width="90" align="center"><template #default="{ row }">{{ row.status === '1' ? '支付成功' : (row.status || '-') }}</template></el-table-column>
      <el-table-column label="充值回执" width="90" align="center"><template #default="{ row }"><el-tag :type="topupStatusTagType(row.topupStatus)">{{ formatTopupStatus(row.topupStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="渠道" prop="channel" width="90" align="center" />
      <el-table-column label="支付方式" prop="payType" width="90" align="center" />
      <el-table-column label="支付中心订单号" prop="payCenterOrderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="渠道订单号" prop="payCenterChannelOrderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="设备编号" prop="deviceId" min-width="130" show-overflow-tooltip />
      <el-table-column label="退款单号" prop="refundNo" min-width="150" show-overflow-tooltip />
      <el-table-column label="下单时间" prop="createTime" width="170" align="center" />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="TvmTopupOrder">
import { listTvmTopupOrders } from '@/api/trans/tvmTopupOrder'
import { defaultTodayRange } from '@/utils/dateRange'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const orders = ref([])
const queryError = ref('')
const filterNotice = ref('')
const queryParams = reactive({
  dateRange: defaultTodayRange(DATE_FORMAT), orderNo: '', payCenterOrderNo: '', ticketLogicNum: '',
  deviceId: '', status: '', topupStatus: '', pageNum: 1, pageSize: 10
})

function buildQuery() {
  const { dateRange, ...params } = queryParams
  return { ...params, beginTime: dateRange?.[0], endTime: dateRange?.[1] }
}

function hasSearchScope() {
  return Boolean(queryParams.orderNo?.trim() || queryParams.payCenterOrderNo?.trim()
    || queryParams.ticketLogicNum?.trim() || (queryParams.dateRange?.[0] && queryParams.dateRange?.[1]))
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    orders.value = []
    total.value = 0
    filterNotice.value = '请至少填写订单号 / 支付中心订单号 / 票卡逻辑号，或完整的下单时间范围'
    return
  }
  loading.value = true
  listTvmTopupOrders(buildQuery()).then((response) => {
    const page = response.data || {}
    orders.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) {
    proxy.$modal.msgWarning('请填写订单标识，或完整的下单时间范围')
    return
  }
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.dateRange = defaultTodayRange(DATE_FORMAT)
  orders.value = []
  total.value = 0
}

function formatAmount(value) { return value == null || value === '' ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
function formatTopupStatus(value) {
  return ({ '00': '成功', '01': '失败', '02': '存疑', '03': '取消' })[value] || '待充值'
}
function topupStatusTagType(value) {
  return ({ '00': 'success', '01': 'danger', '02': 'warning', '03': 'info' })[value] || 'info'
}
</script>
