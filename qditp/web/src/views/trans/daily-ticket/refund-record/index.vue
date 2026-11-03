<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="申请时间" prop="dateRange">
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
        <el-input v-model="queryParams.orderNo" placeholder="请输入日票订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付平台订单号" prop="paymentOrderNo">
        <el-input v-model="queryParams.paymentOrderNo" placeholder="请输入支付平台订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商户退款单号" prop="refundOrderNo">
        <el-input v-model="queryParams.refundOrderNo" placeholder="请输入商户退款单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="退款状态" prop="refundStatus">
        <el-select v-model="queryParams.refundStatus" placeholder="全部" clearable style="width: 130px">
          <el-option label="退款处理中" value="REFUNDING" />
          <el-option label="退款完成" value="REFUNDED" />
          <el-option label="退款失败" value="FAILED" />
          <el-option label="待核验" value="WAIT_VERIFY" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button icon="Back" @click="backToOrders">退款处理</el-button>
        <el-button type="warning" icon="Download" :disabled="!refundList.length" @click="handleExport">导出 Excel</el-button>
        <el-button type="primary" icon="Clock" @click="handleAsyncExport">后台全量导出</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="refundList" border>
      <el-table-column label="商户退款单号" prop="refundOrderNo" min-width="210" show-overflow-tooltip />
      <el-table-column label="平台退款单号" prop="platformRefundNo" min-width="210" show-overflow-tooltip />
      <el-table-column label="日票订单号" prop="orderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="支付平台订单号" prop="paymentOrderNo" min-width="190" show-overflow-tooltip />
      <el-table-column label="退款金额" width="110" align="right">
        <template #default="{ row }">{{ formatAmount(row.refundAmount) }}</template>
      </el-table-column>
      <el-table-column label="退款类型" width="120" align="center">
        <template #default="{ row }">{{ row.refundType === '01' ? '核验退款' : '直接退款' }}</template>
      </el-table-column>
      <el-table-column label="退款状态" width="130" align="center">
        <template #default="{ row }"><el-tag :type="refundStatusType(row.refundStatus)">{{ formatRefundStatus(row.refundStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="申请时间" width="170" align="center"><template #default="{ row }">{{ parseTime(row.createTime) }}</template></el-table-column>
      <el-table-column label="核验时间" width="170" align="center"><template #default="{ row }">{{ parseTime(row.verifyAfterTime) || '-' }}</template></el-table-column>
      <el-table-column label="完成时间" width="170" align="center"><template #default="{ row }">{{ parseTime(row.refundDate) || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="190" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="canOperatePlatformRefund(row)" link type="primary" icon="Search" @click="queryRefund(row)">查询结果</el-button>
          <el-button v-if="canOperatePlatformRefund(row)" link type="warning" icon="RefreshRight" @click="retryRefund(row)">退款重试</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="DailyTicketRefundRecord">
import { listDailyTicketRefundRecords, queryDailyTicketRefund, retryDailyTicketRefund } from '@/api/trans/dailyTicketRefund'
import { defaultTodayRange } from '@/utils/dateRange'
import { exportToExcel } from '@/utils/excelExport'
import { parseTime } from '@/utils/points'
import { submitAsyncExportTask } from '@/api/monitor/asyncExport'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const total = ref(0)
const refundList = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  dateRange: defaultTodayRange(DATE_FORMAT),
  orderNo: route.query.orderNo || undefined,
  paymentOrderNo: route.query.paymentOrderNo || undefined,
  refundOrderNo: undefined,
  refundStatus: undefined
})

function buildQuery() {
  // 日期控件值转换为后端约定的 beginTime/endTime 参数。
  const { dateRange, ...params } = queryParams
  return { ...params, beginTime: dateRange?.[0], endTime: dateRange?.[1] }
}

function getList() {
  loading.value = true
  listDailyTicketRefundRecords(buildQuery()).then((response) => {
    const page = response.data || {}
    refundList.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.dateRange = defaultTodayRange(DATE_FORMAT)
  handleQuery()
}

function canOperatePlatformRefund(row) {
  // 核验退款不调用支付平台，直接退款的处理中和失败状态可主动查询和重试。
  return row.refundType === '00' && ['REFUNDING', 'FAILED'].includes(row.refundStatus)
}

function queryRefund(row) {
  queryDailyTicketRefund(row.orderNo).then((response) => {
    const result = response.data || {}
    proxy.$modal.msgSuccess(result.refundResultDesc || '退款结果查询完成')
    getList()
  })
}

function retryRefund(row) {
  // 后端会先向支付平台查询，并且始终使用原退款单号。
  proxy.$modal.confirm(`确认使用原商户退款单号“${row.refundOrderNo}”重试退款？`).then(() => retryDailyTicketRefund(row.orderNo)).then((response) => {
    const result = response.data || {}
    proxy.$modal.msgSuccess(result.refundResultDesc || '退款重试已提交')
    getList()
  }).catch(() => {})
}

// 两个页面作为一个操作闭环，保留明确的返回入口。
function backToOrders() { router.push('/trans/daily-ticket/refund') }
// 数据库存储金额单位为分，页面统一转换为元展示。
function formatAmount(value) { return value == null ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
function formatRefundStatus(value) { return ({ REFUNDING: '退款处理中', REFUNDED: '退款完成', FAILED: '退款失败', WAIT_VERIFY: '待核验' })[value] || value || '-' }
function refundStatusType(value) { return ({ REFUNDED: 'success', FAILED: 'danger', WAIT_VERIFY: 'warning', REFUNDING: 'warning' })[value] || 'info' }

/** 提交后台异步全量导出任务（每日凌晨 2 点统一执行）。 */
function handleAsyncExport() {
  const { dateRange, pageNum, pageSize, ...rest } = queryParams
  const data = {
    taskCode: 'DAILY_TICKET_REFUND_RECORD',
    taskName: '日票退款记录全量导出',
    paramJson: JSON.stringify({
      ...rest,
      beginTime: dateRange?.[0],
      endTime: dateRange?.[1]
    })
  }
  submitAsyncExportTask(data).then((response) => {
    proxy.$modal.msgSuccess(response.msg || '提交成功，请前往「系统监控-异步导出任务」查看')
  }).catch((error) => {
    proxy.$modal.msgError(error.message || '提交失败')
  })
}

/** 导出日票退款记录「当前页」。 */
function handleExport() {
  if (!refundList.value.length) { proxy.$modal.msgWarning('当前页没有可导出的退款记录'); return }
  try {
    const data = refundList.value.map((row) => ({
      refundOrderNo: row.refundOrderNo,
      platformRefundNo: row.platformRefundNo,
      orderNo: row.orderNo,
      paymentOrderNo: row.paymentOrderNo,
      refundAmount: formatAmount(row.refundAmount),
      refundType: row.refundType === '01' ? '核验退款' : '直接退款',
      refundStatus: formatRefundStatus(row.refundStatus),
      createTime: parseTime(row.createTime),
      verifyAfterTime: parseTime(row.verifyAfterTime) || '-',
      refundDate: parseTime(row.refundDate) || '-'
    }))
    const headers = [
      { key: 'refundOrderNo', header: '商户退款单号' },
      { key: 'platformRefundNo', header: '平台退款单号' },
      { key: 'orderNo', header: '日票订单号' },
      { key: 'paymentOrderNo', header: '支付平台订单号' },
      { key: 'refundAmount', header: '退款金额' },
      { key: 'refundType', header: '退款类型' },
      { key: 'refundStatus', header: '退款状态' },
      { key: 'createTime', header: '申请时间' },
      { key: 'verifyAfterTime', header: '核验时间' },
      { key: 'refundDate', header: '完成时间' }
    ]
    const dateStr = new Date().toISOString().slice(0, 10)
    exportToExcel(data, headers, `日票退款记录_${dateStr}`)
    proxy.$modal.msgSuccess(`已导出当前页 ${data.length} 条退款记录`)
  } catch (e) {
    proxy.$modal.msgError(e.message || '导出失败')
  }
}

getList()
</script>
