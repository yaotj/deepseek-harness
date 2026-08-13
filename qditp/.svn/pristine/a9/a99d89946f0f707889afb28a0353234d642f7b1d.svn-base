<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="下单时间" prop="dateRange">
        <el-date-picker
          v-model="queryParams.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 340px"
        />
      </el-form-item>
      <el-form-item label="日票订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入日票订单号" clearable style="width: 230px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付平台订单号" prop="paymentOrderNo">
        <el-input v-model="queryParams.paymentOrderNo" placeholder="请输入支付平台订单号" clearable style="width: 230px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button icon="Document" @click="toRefundRecords">退款记录</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      title="仅支付成功、未使用且未存在退款申请的订单可发起退款；已激活未使用的订单将进入核验退款流程。"
      type="info"
      :closable="false"
      show-icon
      class="mb8"
    />

    <el-table v-loading="loading" :data="orderList" border>
      <el-table-column label="日票订单号" prop="orderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="支付平台订单号" prop="paymentOrderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="订单金额" width="110" align="right">
        <template #default="{ row }">{{ formatAmount(row.payAmount ?? row.ticketPrice) }}</template>
      </el-table-column>
      <el-table-column label="订单状态" width="110" align="center">
        <template #default="{ row }"><el-tag :type="orderStatusType(row.orderStatus)">{{ formatOrderStatus(row.orderStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="支付状态" width="100" align="center">
        <template #default="{ row }"><el-tag :type="row.payStatus === 'PAID' ? 'success' : 'info'">{{ row.payStatus || '-' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="车票状态" width="120" align="center">
        <template #default="{ row }">{{ formatTicketStatus(row.ticketStatus) }}</template>
      </el-table-column>
      <el-table-column label="退款状态" width="120" align="center">
        <template #default="{ row }"><el-tag v-if="row.refundStatus" :type="refundStatusType(row.refundStatus)">{{ formatRefundStatus(row.refundStatus) }}</el-tag><span v-else>-</span></template>
      </el-table-column>
      <el-table-column label="下单时间" width="170" align="center">
        <template #default="{ row }">{{ parseTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="330" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="View" @click="showDetail(row)">详情</el-button>
          <el-button v-if="canQueryPay(row)" link type="primary" icon="Search" @click="queryPay(row)">查询支付</el-button>
          <el-button link type="danger" icon="Money" :disabled="!canRefund(row)" @click="confirmRefund(row)">退款</el-button>
          <el-button v-if="canOperatePlatformRefund(row)" link type="primary" icon="Search" @click="queryRefund(row)">查询结果</el-button>
          <el-button v-if="canRetryPlatformRefund(row)" link type="warning" icon="RefreshRight" @click="retryRefund(row)">退款重试</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="detailOpen" title="日票订单详情" width="760px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="日票订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="支付平台订单号">{{ currentOrder.paymentOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="第三方交易号">{{ currentOrder.tradeNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付渠道">{{ currentOrder.payChannelCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ formatAmount(currentOrder.payAmount ?? currentOrder.ticketPrice) }}</el-descriptions-item>
        <el-descriptions-item label="票实例状态">{{ formatTicketStatus(currentOrder.ticketStatus) }}</el-descriptions-item>
        <el-descriptions-item label="商户退款单号">{{ currentOrder.refundOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="平台退款单号">{{ currentOrder.platformRefundNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退款状态">{{ formatRefundStatus(currentOrder.refundStatus) }}</el-descriptions-item>
        <el-descriptions-item label="核验时间" :span="2">{{ parseTime(currentOrder.verifyAfterTime) || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup name="DailyTicketRefund">
import { listDailyTicketRefundOrders, queryDailyTicketPay, queryDailyTicketRefund, requestDailyTicketRefund, retryDailyTicketRefund } from '@/api/trans/dailyTicketRefund'

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const total = ref(0)
const orderList = ref([])
const detailOpen = ref(false)
const currentOrder = ref({})
const queryParams = reactive({ pageNum: 1, pageSize: 10, dateRange: [], orderNo: undefined, paymentOrderNo: undefined })

function buildQuery() {
  // 日期控件值转换为后端约定的 beginTime/endTime 参数。
  const { dateRange, ...params } = queryParams
  return { ...params, beginTime: dateRange?.[0], endTime: dateRange?.[1] }
}

function getList() {
  loading.value = true
  listDailyTicketRefundOrders(buildQuery()).then((response) => {
    const page = response.data || {}
    orderList.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.dateRange = []
  handleQuery()
}

function showDetail(row) {
  currentOrder.value = row
  detailOpen.value = true
}

function canRefund(row) {
  // 前端仅作操作提示，服务端会再次校验支付、票使用和退款单状态。
  return row.orderStatus === 'PAID' && row.payStatus === 'PAID' && row.ticketStatus !== 'USED' && !row.refundStatus
}

function canQueryPay(row) {
  // 支付中状态可人工拉取支付平台结果，成功后页面会刷新并开放退款操作。
  return row.orderStatus === 'PAYING' || row.payStatus === 'PAYING'
}

function queryPay(row) {
  queryDailyTicketPay(row.orderNo).then((response) => {
    const result = response.data || {}
    const message = ({ success: '支付成功，订单状态已同步', failed: '支付失败，订单状态已同步', processing: '支付仍在处理中' })[result.payResult] || '支付结果查询完成'
    proxy.$modal.msgSuccess(message)
    getList()
  })
}

function confirmRefund(row) {
  // 退款为资金操作，先确认再调用接口；接口已保证重复申请幂等。
  proxy.$modal.confirm(`确认对日票订单“${row.orderNo}”发起退款？`).then(() => requestDailyTicketRefund(row.orderNo)).then((response) => {
    const result = response.data || {}
    proxy.$modal.msgSuccess(result.refundResultDesc || '退款申请已提交')
    getList()
  }).catch(() => {})
}

function canOperatePlatformRefund(row) {
  // 核验退款不调用支付平台，直接退款的处理中和失败状态可主动查询。
  return row.refundType === '00' && ['REFUNDING', 'FAILED'].includes(row.refundStatus)
}

function canRetryPlatformRefund(row) {
  // 重试使用既有退款单号，避免页面产生第二笔退款请求。
  return canOperatePlatformRefund(row)
}

function queryRefund(row) {
  queryDailyTicketRefund(row.orderNo).then((response) => {
    const result = response.data || {}
    proxy.$modal.msgSuccess(result.refundResultDesc || '退款结果查询完成')
    getList()
  })
}

function retryRefund(row) {
  // 后端会先向支付平台查询，因此重试仅在尚未完成时真正发起。
  proxy.$modal.confirm(`确认使用原商户退款单号“${row.refundOrderNo}”重试退款？`).then(() => retryDailyTicketRefund(row.orderNo)).then((response) => {
    const result = response.data || {}
    proxy.$modal.msgSuccess(result.refundResultDesc || '退款重试已提交')
    getList()
  }).catch(() => {})
}

function toRefundRecords() {
  // 带上当前订单条件，减少从订单处理页进入退款记录页后的二次输入。
  router.push({ path: '/trans/daily-ticket/refund-record', query: { orderNo: queryParams.orderNo || undefined, paymentOrderNo: queryParams.paymentOrderNo || undefined } })
}

// 数据库存储金额单位为分，页面统一转换为元展示。
function formatAmount(value) { return value == null ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
function formatOrderStatus(value) { return ({ PAID: '已支付', REFUNDING: '退款中', REFUNDED: '已退款', CREATED: '待支付', PAYING: '支付中', CANCELED: '已取消' })[value] || value || '-' }
function formatTicketStatus(value) { return ({ ACTIVATED: '已激活', USED: '已使用', REFUNDED: '已退款', INIT: '待激活' })[value] || value || '未激活' }
function formatRefundStatus(value) { return ({ REFUNDING: '退款处理中', REFUNDED: '退款完成', FAILED: '退款失败', WAIT_VERIFY: '待核验' })[value] || value || '-' }
function orderStatusType(value) { return ({ PAID: 'success', REFUNDING: 'warning', REFUNDED: 'info', CANCELED: 'info' })[value] || '' }
function refundStatusType(value) { return ({ REFUNDED: 'success', FAILED: 'danger', WAIT_VERIFY: 'warning', REFUNDING: 'warning' })[value] || 'info' }

getList()
</script>
