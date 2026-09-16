<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="下单时间" prop="dateRange">
        <el-date-picker v-model="queryParams.dateRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" style="width: 340px" />
      </el-form-item>
      <el-form-item label="业务订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入业务订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付中心订单号" prop="payCenterOrderNo">
        <el-input v-model="queryParams.payCenterOrderNo" placeholder="请输入支付中心订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="渠道订单号" prop="payCenterChannelOrderNo">
        <el-input v-model="queryParams.payCenterChannelOrderNo" placeholder="请输入渠道订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="设备编号" prop="deviceId">
        <el-input v-model="queryParams.deviceId" placeholder="请输入 TVM 设备编号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="受理渠道" prop="channel">
        <el-select v-model="queryParams.channel" clearable placeholder="全部" style="width: 130px">
          <el-option label="APP (01)" value="01" />
          <el-option label="TVM (02)" value="02" />
          <el-option label="BOM (03)" value="03" />
        </el-select>
      </el-form-item>
      <el-form-item label="订单状态" prop="orderStatus">
        <el-select v-model="queryParams.orderStatus" clearable placeholder="全部" style="width: 140px">
          <el-option label="已下单" value="CREATED" />
          <el-option label="支付中" value="PAYING" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已履约" value="FULFILLED" />
          <el-option label="履约失败" value="FULFILL_FAILED" />
          <el-option label="支付失败" value="PAY_FAILED" />
          <el-option label="已过期" value="EXPIRED" />
          <el-option label="退款中" value="REFUNDING" />
          <el-option label="已退款" value="REFUNDED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="orders" border>
      <el-table-column label="业务订单号" prop="orderNo" min-width="200" show-overflow-tooltip />
      <el-table-column label="订单状态" width="100" align="center"><template #default="{ row }"><el-tag :type="orderStatusTagType(row.orderStatus)">{{ formatOrderStatus(row.orderStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="受理渠道" width="90" align="center"><template #default="{ row }">{{ formatChannel(row.channel) }}</template></el-table-column>
      <el-table-column label="支付渠道编码" prop="payChannelCode" width="130" align="center" />
      <el-table-column label="支付中心订单号" prop="payCenterOrderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="渠道订单号" prop="payCenterChannelOrderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="起点站" width="100" align="center"><template #default="{ row }">{{ row.inStationName || row.inStationCode || '-' }}</template></el-table-column>
      <el-table-column label="终点站" width="100" align="center"><template #default="{ row }">{{ row.outStationName || row.outStationCode || '-' }}</template></el-table-column>
      <el-table-column label="票价" width="100" align="right"><template #default="{ row }">{{ formatAmount(row.ticketPrice) }}</template></el-table-column>
      <el-table-column label="数量" prop="ticketNum" width="80" align="center" />
      <el-table-column label="订单总额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.orderAmount) }}</template></el-table-column>
      <el-table-column label="购票类型" width="110" align="center"><template #default="{ row }">{{ formatTicketType(row.singleTicketType) }}</template></el-table-column>
      <el-table-column label="设备编号" prop="deviceId" min-width="130" show-overflow-tooltip />
      <el-table-column label="退款状态" width="105" align="center"><template #default="{ row }"><el-tag :type="refundStatusTagType(row.refundStatus)">{{ formatRefundStatus(row.refundStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="已退金额" width="105" align="right"><template #default="{ row }">{{ (row.refundAmount ?? 0) > 0 ? formatAmount(row.refundAmount) : '-' }}</template></el-table-column>
      <el-table-column label="最后退款时间" prop="lastRefundTime" width="170" align="center" />
      <el-table-column label="下单时间" prop="createTime" width="170" align="center" />
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-tooltip content="退款" placement="top">
            <el-button v-hasPermi="['trans:face-pay:refund']" link type="danger" icon="RefreshLeft" :disabled="!canRefund(row)" @click="handleRefund(row)" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="refundOpen" title="当面付退款" width="500px" append-to-body destroy-on-close>
      <el-descriptions :column="1" border size="small" class="mb8">
        <el-descriptions-item label="业务订单号">{{ refundOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">{{ formatAmount(refundOrder.orderAmount) }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="refundFormRef" :model="refundForm" :rules="refundRules" label-width="90px">
        <el-form-item label="退款原因" prop="refundReason">
          <el-input v-model="refundForm.refundReason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请输入退款原因" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="refundOpen = false">取消</el-button><el-button type="danger" :loading="refunding" @click="submitRefund">确认退款</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="FacePayOrder">
import { listFacePayOrders, requestFacePayRefund } from '@/api/trans/facePayOrder'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const orders = ref([])
const refundOpen = ref(false)
const refunding = ref(false)
const refundOrder = reactive({ orderNo: '', orderAmount: null })
const refundForm = reactive({ refundReason: '' })
const refundRules = { refundReason: [{ required: true, message: '请输入退款原因', trigger: 'blur' }] }
const queryParams = reactive({
  dateRange: [], orderNo: '', payCenterOrderNo: '', payCenterChannelOrderNo: '', deviceId: '', channel: '', orderStatus: '', pageNum: 1, pageSize: 10
})

function buildQuery() {
  const { dateRange, ...params } = queryParams
  return { ...params, beginTime: dateRange?.[0], endTime: dateRange?.[1] }
}

function hasSearchScope() {
  return Boolean(queryParams.orderNo?.trim() || queryParams.payCenterOrderNo?.trim() || queryParams.payCenterChannelOrderNo?.trim() || (queryParams.dateRange?.[0] && queryParams.dateRange?.[1]))
}

function getList() {
  if (!hasSearchScope()) {
    orders.value = []
    total.value = 0
    return
  }
  loading.value = true
  listFacePayOrders(buildQuery()).then((response) => {
    const page = response.data || {}
    orders.value = page.list || []
    total.value = Number(page.total || 0)
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
  queryParams.dateRange = []
  orders.value = []
  total.value = 0
}

function formatAmount(value) { return value == null || value === '' ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
function formatOrderStatus(value) {
  return ({
    CREATED: '已下单', PAYING: '支付中', PAID: '已支付', FULFILLED: '已履约',
    FULFILL_FAILED: '履约失败', TOPUP_SUSPECT: '充值可疑', PAY_FAILED: '支付失败',
    EXPIRED: '已过期', REFUNDING: '退款中', REFUNDED: '已退款', CANCELED: '已取消'
  })[value] || value || '-'
}
function orderStatusTagType(value) {
  return ({
    CREATED: 'info', PAYING: 'warning', PAID: 'success', FULFILLED: 'success',
    FULFILL_FAILED: 'danger', TOPUP_SUSPECT: 'warning', PAY_FAILED: 'danger',
    EXPIRED: 'info', REFUNDING: 'warning', REFUNDED: 'danger', CANCELED: 'info'
  })[value] || 'info'
}
function formatChannel(value) { return ({ '01': 'APP', '02': 'TVM', '03': 'BOM' })[value] || value || '-' }
function formatTicketType(value) { return ({ '0': '按站点购票', '1': '固定票价购票' })[value] || value || '-' }
function formatRefundStatus(value) { return ({ NONE: '未退款', PARTIAL: '部分退款', SUCCESS: '已退款' })[value] || value || '-' }
function refundStatusTagType(value) { return ({ NONE: 'info', PARTIAL: 'warning', SUCCESS: 'danger' })[value] || 'info' }
function canRefund(row) {
  const refundableOrderStatuses = ['PAID', 'FULFILLED', 'FULFILL_FAILED']
  return refundableOrderStatuses.includes(row.orderStatus) && row.refundStatus !== 'SUCCESS' && row.refundStatus !== 'PARTIAL'
}
function handleRefund(row) {
  Object.assign(refundOrder, { orderNo: row.orderNo, orderAmount: row.orderAmount })
  refundForm.refundReason = ''
  proxy.resetForm('refundFormRef')
  refundOpen.value = true
}
function submitRefund() {
  proxy.$refs.refundFormRef.validate((valid) => {
    if (!valid) return
    refunding.value = true
    requestFacePayRefund(refundOrder.orderNo, { refundReason: refundForm.refundReason.trim() }).then((response) => {
      proxy.$modal.msgSuccess(response.data?.refundResultDesc || '退款请求已受理')
      refundOpen.value = false
      getList()
    }).finally(() => { refunding.value = false })
  })
}
</script>
