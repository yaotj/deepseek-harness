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
      <el-form-item label="支付渠道" prop="channel">
        <el-input v-model="queryParams.channel" placeholder="请输入支付渠道编码" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 130px">
          <el-option label="支付中" value="0" />
          <el-option label="支付成功" value="1" />
          <el-option label="支付失败" value="2" />
          <el-option label="未支付" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="购票类型" prop="ticketType">
        <el-select v-model="queryParams.ticketType" clearable placeholder="全部" style="width: 150px">
          <el-option label="按站点购票" value="0" />
          <el-option label="固定票价购票" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert title="当面付订单数据来源：collect-pay-server 的 TBL_TVM_ORDER_PAY。请输入订单标识，或完整的下单时间范围后查询；仅支付成功且未发起退款的订单可操作退款。" type="info" :closable="false" show-icon class="mb8" />

    <el-table v-loading="loading" :data="orders" border>
      <el-table-column label="业务订单号" prop="orderNo" min-width="200" show-overflow-tooltip />
      <el-table-column label="支付状态" width="105" align="center"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ formatStatus(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="支付渠道" prop="channel" width="115" align="center" />
      <el-table-column label="支付中心订单号" prop="payCenterOrderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="渠道订单号" prop="payCenterChannelOrderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="起点站" prop="inStationCode" width="100" align="center" />
      <el-table-column label="终点站" prop="outStationCode" width="100" align="center" />
      <el-table-column label="票价" width="100" align="right"><template #default="{ row }">{{ formatAmount(row.ticketPrice) }}</template></el-table-column>
      <el-table-column label="数量" prop="ticketNum" width="80" align="center" />
      <el-table-column label="订单总额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.totalPrice) }}</template></el-table-column>
      <el-table-column label="购票类型" width="110" align="center"><template #default="{ row }">{{ formatTicketType(row.ticketType) }}</template></el-table-column>
      <el-table-column label="设备编号" prop="deviceId" min-width="130" show-overflow-tooltip />
      <el-table-column label="下单时间" prop="createTime" width="170" align="center" />
      <el-table-column label="状态说明" prop="msg" min-width="150" show-overflow-tooltip />
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
        <el-descriptions-item label="退款金额">{{ formatAmount(refundOrder.totalPrice) }}</el-descriptions-item>
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
const refundOrder = reactive({ orderNo: '', totalPrice: '' })
const refundForm = reactive({ refundReason: '' })
const refundRules = { refundReason: [{ required: true, message: '请输入退款原因', trigger: 'blur' }] }
const queryParams = reactive({
  dateRange: [], orderNo: '', payCenterOrderNo: '', payCenterChannelOrderNo: '', deviceId: '', channel: '', status: '', ticketType: '', pageNum: 1, pageSize: 10
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
function formatStatus(value) { return ({ '0': '支付中', '1': '支付成功', '2': '支付失败', '3': '未支付' })[value] || value || '-' }
function statusTagType(value) { return ({ '0': 'warning', '1': 'success', '2': 'danger', '3': 'info' })[value] || 'info' }
function formatTicketType(value) { return ({ '0': '按站点购票', '1': '固定票价购票' })[value] || value || '-' }
// 后端会再次核验状态与退款单号，前端禁用仅用于避免无效操作。
function canRefund(row) { return row.status === '1' && !row.refundNo }
function handleRefund(row) {
  Object.assign(refundOrder, { orderNo: row.orderNo, totalPrice: row.totalPrice })
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
