<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" label-width="96px">
      <el-form-item label="扣费订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入扣费订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="逻辑卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="第三方用户 ID" prop="thirdUserId">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入第三方用户 ID" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="签约渠道" prop="signChannelCode">
        <el-input v-model="queryParams.signChannelCode" placeholder="请输入签约渠道编码" clearable style="width: 170px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="票种" prop="cardType">
        <el-input v-model="queryParams.cardType" placeholder="请输入票种编码" clearable style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="扣费状态" prop="debitStatus">
        <el-select v-model="queryParams.debitStatus" clearable placeholder="全部" style="width: 190px">
          <el-option v-for="opt in debitStatusOptions()" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="交易日期" prop="dateRange">
        <query-time-range v-model="queryParams.dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="warning" icon="Download" :disabled="exporting || orders.length === 0" :loading="exporting" @click="handleExport">导出 Excel</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      class="mb8"
      type="info"
      :closable="false"
      show-icon
      title="本行是扣费订单；点行首箭头展开该笔对应的闸机交易明细。退款与补款按当页订单号批量回填，展开行按需取该卡当日的交易。"
    />

    <el-table ref="tableRef" v-loading="loading" :data="orders" border row-key="orderNo" @expand-change="handleExpand">
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

      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="txn-panel">
            <div v-if="txnState[row.orderNo] === 'loading'" class="txn-hint">正在取该笔对应的闸机交易…</div>
            <div v-else-if="txnState[row.orderNo] === 'error'" class="txn-hint">闸机交易取数失败，可重新展开重试</div>
            <div v-else-if="!(txnRows[row.orderNo] || []).length" class="txn-hint">未取到与该扣费订单对应的闸机交易行</div>
            <el-table v-else :data="txnRows[row.orderNo]" border size="small">
              <el-table-column label="交易类型" min-width="180"><template #default="{ row: t }">{{ formatTransactionType(t.trxType) }}</template></el-table-column>
              <el-table-column label="处理结果" min-width="240"><template #default="{ row: t }">{{ formatHandleResult(t.handleResultCode) }}</template></el-table-column>
              <el-table-column label="交易流水号" prop="ticketTransSeq" min-width="180" show-overflow-tooltip />
              <el-table-column label="设备编号" prop="deviceId" min-width="130" show-overflow-tooltip />
              <el-table-column label="票卡状态" prop="lastTicketStatus" width="110" align="center"><template #default="{ row: t }">{{ t.lastTicketStatus || '-' }}</template></el-table-column>
              <el-table-column label="交易时间" width="180" align="center"><template #default="{ row: t }">{{ formatTransactionTime(t.handleDateTime) }}</template></el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="扣费订单号" prop="orderNo" min-width="210" show-overflow-tooltip />
      <el-table-column label="扣费状态" width="150" align="center">
        <template #default="{ row }"><el-tag :type="statusType(row.debitStatus)">{{ debitStatusLabel(row.debitStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="逻辑卡号" prop="cardId" min-width="170" show-overflow-tooltip />
      <el-table-column label="进站站点" width="110" align="center"><template #default="{ row }">{{ row.entryStationName || row.inStation || '-' }}</template></el-table-column>
      <el-table-column label="出站站点" width="110" align="center"><template #default="{ row }">{{ row.exitStationName || row.outStation || '-' }}</template></el-table-column>
      <el-table-column label="出站时间" prop="outTime" width="170" align="center"><template #default="{ row }">{{ row.outTime || '-' }}</template></el-table-column>
      <el-table-column label="基础金额" width="100" align="right"><template #default="{ row }">{{ formatAmount(row.trxAmount) }}</template></el-table-column>
      <el-table-column label="超时金额" width="100" align="right"><template #default="{ row }">{{ formatAmount(row.overtimeAmount) }}</template></el-table-column>
      <el-table-column label="扣费总额" width="100" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
      <el-table-column label="签约渠道" width="140" align="center"><template #default="{ row }">{{ signChannelLabel(row.signChannelCode) }}</template></el-table-column>

      <el-table-column label="退款状态" width="190" align="center">
        <template #default="{ row }">
          <el-tag :type="refundTagType(row)">{{ refundSummary(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已退金额" width="100" align="right">
        <template #default="{ row }">{{ formatAmount(refundSummary(row).refundedAmount) }}</template>
      </el-table-column>

      <el-table-column label="补款金额" width="100" align="right">
        <template #default="{ row }">{{ formatAmount(supplementSummary(row).totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="补款渠道" width="140" align="center">
        <template #default="{ row }">{{ supplementSummary(row).channelLabel }}</template>
      </el-table-column>
      <el-table-column label="补款订单号" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ supplementSummary(row).orderNoLabel }}</template>
      </el-table-column>

      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-hasPermi="['trans:user:debit-detail:query']" link type="danger" icon="Money" :disabled="!canRefund(row)" @click="openRefund(row)">退款</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="refundOpen" title="发起扣费退款" width="460px" append-to-body destroy-on-close>
      <el-descriptions :column="1" border class="mb8">
        <el-descriptions-item label="扣费订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单扣费总额">{{ formatAmount(currentOrder.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已退金额">{{ formatAmount(refundSummary(currentOrder).refundedAmount) }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="refundRef" :model="refundForm" :rules="refundRules" label-width="100px">
        <el-form-item label="退款金额" prop="amountYuan">
          <el-input-number v-model="refundForm.amountYuan" :min="0.01" :max="maxRefundAmount" :precision="2" :step="0.01" controls-position="right" />
          <span class="unit-label">元</span>
        </el-form-item>
        <el-form-item label="退款原因" prop="refundReason">
          <el-input v-model="refundForm.refundReason" type="textarea" :rows="3" maxlength="100" show-word-limit placeholder="请输入退款原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundOpen = false">取消</el-button>
        <el-button type="danger" :loading="refunding" @click="submitRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="UserOrderDetail">
import { listGateTxnPays, listQRCodeTxnDetails, requestGateTxnPayRefund } from '@/api/trans/userSearch'
import { listRefundsByOrders } from '@/api/trans/payRefund'
import { listSupplementsByOrig } from '@/api/trans/supplementOrder'
import { defaultTodayRange } from '@/utils/dateRange'
import { signChannelLabel } from '@/utils/payChannel'
import { cardTypeLabel } from '@/utils/cardType'
import { debitStatusLabel, debitStatusOptions } from '@/utils/orderStatusLabels'
import { exportToExcel } from '@/utils/excelExport'

/** 必须与模板里区间的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYYMMDD'

/** 支付宝出行发卡渠道：该渠道的退款不落 PAY_REFUND_DETAIL，另走 ALIPAY_REFUND_TXN_DETAIL。 */
const ALIPAY_ISSUE_CHANNEL = '07'

const { proxy } = getCurrentInstance()
const route = useRoute()
const loading = ref(false)
const exporting = ref(false)
const refunding = ref(false)
const total = ref(0)
const orders = ref([])
const queryError = ref('')
const filterNotice = ref('')
const refundOpen = ref(false)
const currentOrder = ref({})
const refundForm = reactive({ amountYuan: undefined, refundReason: '' })

/** 当页订单号 → 退款明细数组；补款同构。批量回填后整体替换，避免逐行 setState。 */
const refundsByOrder = ref({})
const supplementsByOrder = ref({})
/** 展开行缓存：orderNo → 闸机交易数组 / 取数状态。 */
const txnRows = reactive({})
const txnState = reactive({})

/** 与 debit-detail 同口径：卡号/订单号本身就是强定位键，不需要再叠时间范围。 */
function initialDateRange() {
  return route.query.cardId ? [] : defaultTodayRange(DATE_FORMAT)
}

const queryParams = reactive({
  orderNo: '', cardId: route.query.cardId || '', thirdUserId: '', signChannelCode: '', cardType: '',
  debitStatus: '', dateRange: initialDateRange(), pageNum: 1, pageSize: 10
})

const maxRefundAmount = computed(() => Number(currentOrder.value.totalAmount || 0) / 100)
const refundRules = {
  amountYuan: [{ required: true, message: '请输入退款金额', trigger: 'change' }],
  refundReason: [{ required: true, message: '请输入退款原因', trigger: 'blur' }]
}

function buildQuery() {
  const { dateRange, pageNum, pageSize, ...rest } = queryParams
  const params = Object.fromEntries(Object.entries(rest).filter(([, v]) => String(v ?? '').trim()))
  return { ...params, startDate: dateRange?.[0], endDate: dateRange?.[1], pageNum, pageSize }
}

function hasSearchScope() {
  return Boolean(queryParams.orderNo.trim() || queryParams.cardId.trim() || queryParams.thirdUserId.trim()
    || (queryParams.dateRange?.[0] && queryParams.dateRange?.[1]))
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    orders.value = []
    total.value = 0
    filterNotice.value = '请填写扣费订单号、逻辑卡号、第三方用户 ID，或完整的交易日期范围'
    return
  }
  loading.value = true
  listGateTxnPays(buildQuery()).then((response) => {
    const page = response.data || {}
    orders.value = page.list || []
    total.value = Number(page.total || 0)
    clearExpandCache()
    return fillRefundAndSupplement()
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

/**
 * 按当页订单号批量回填退款与补款。
 *
 * <p>两路都 MUST 各自 catch：退款侧或补款侧单独挂掉时，另一侧照常显示，
 * NEVER 让一路失败把整个列表拖成「查询失败」——主表数据已经拿到了。
 */
function fillRefundAndSupplement() {
  const orderNos = orders.value.map((row) => row.orderNo).filter(Boolean)
  if (!orderNos.length) {
    refundsByOrder.value = {}
    supplementsByOrder.value = {}
    return Promise.resolve()
  }
  const refunds = listRefundsByOrders(orderNos)
    .then((response) => groupBy(response.data, (item) => item.orderNo))
    .catch(() => ({}))
  const supplements = listSupplementsByOrig(orderNos)
    .then((response) => groupBy(response.data, (item) => item.origOrderNo))
    .catch(() => ({}))
  return Promise.all([refunds, supplements]).then(([refundMap, supplementMap]) => {
    refundsByOrder.value = refundMap
    supplementsByOrder.value = supplementMap
  })
}

function groupBy(list, keyOf) {
  const map = {}
  for (const item of list || []) {
    const key = keyOf(item)
    if (!key) continue
    ;(map[key] ||= []).push(item)
  }
  return map
}

/**
 * 退款口径：只报事实，不替业务下结论。
 *
 * <p>「已退金额」只累加 SUCCESS；CLOSED 是「回查超龄、结果未知」，
 * NEVER 计入已退、也 NEVER 翻成「退款失败」。
 */
function refundSummary(row) {
  if (row.issueChannelCode === ALIPAY_ISSUE_CHANNEL) {
    return { label: '渠道侧未覆盖', refundedAmount: 0, tagType: 'info' }
  }
  const list = refundsByOrder.value[row.orderNo] || []
  if (!list.length) {
    return { label: '未发起退款', refundedAmount: 0, tagType: 'info' }
  }
  const refunded = list.filter((r) => r.refundStatus === 'SUCCESS').reduce((sum, r) => sum + Number(r.refundAmount || 0), 0)
  const latest = list[0]
  const closed = list.filter((r) => r.refundStatus === 'CLOSED').length
  const label = `${latest.refundStatus}-${latest.refundStatus === 'SUCCESS' ? '退款成功' : latest.refundStatus === 'CLOSED' ? '结果未知(超龄收口,需人工)' : '详见明细'} · ${list.length} 笔`
  return {
    label,
    refundedAmount: refunded,
    tagType: latest.refundStatus === 'SUCCESS' ? 'success' : closed ? 'warning' : 'danger'
  }
}

/** 补款口径：金额取合计、渠道与单号取最近一笔（SQL 已按原单 + ID 排序，末元素即最新）。 */
function supplementSummary(row) {
  const list = supplementsByOrder.value[row.orderNo] || []
  if (!list.length) {
    return { totalAmount: 0, channelLabel: '-', orderNoLabel: '-' }
  }
  const totalAmount = list.reduce((sum, item) => sum + Number(item.totalAmount || 0), 0)
  const latest = list[list.length - 1]
  const orderNoLabel = list.length > 1 ? `${latest.supplementOrderNo} 等 ${list.length} 笔` : latest.supplementOrderNo
  return { totalAmount, channelLabel: signChannelLabel(latest.signChannelCode), orderNoLabel }
}

function clearExpandCache() {
  for (const key of Object.keys(txnRows)) delete txnRows[key]
  for (const key of Object.keys(txnState)) delete txnState[key]
}

/**
 * 展开行：取该卡当日的闸机交易，再按扣费侧的业务键收窄。
 *
 * <p>收窄用 `CARD_ID + TRX_TYPE + HANDLE_DATE_TIME(=OUT_TIME) + TICKET_TRANS_SEQ` 四条，
 * 与 `QRCodeTxnDetailMapper.selectExcessFareDetail` 已验证的口径一致；
 * 现网实测该四元组在 166.97 万扣费行上命中 1,669,712 行。
 * NEVER 用「进出站序列号相等」配对进/出站 —— 闸机上送的两个序列号天然不同，那条路恒 0 行。
 */
function handleExpand(row, expandedRows) {
  const expanded = Array.isArray(expandedRows) ? expandedRows.some((r) => r.orderNo === row.orderNo) : Boolean(expandedRows)
  if (!expanded || txnState[row.orderNo] === 'loading' || txnRows[row.orderNo]) return
  txnState[row.orderNo] = 'loading'
  const day = row.txnDate || row.outTime?.slice(0, 8)
  listQRCodeTxnDetails({ cardId: row.cardId, startDate: day, endDate: day, pageNum: 1, pageSize: 100 })
    .then((response) => {
      const rows = (response.data?.list || []).filter((t) =>
        t.trxType === row.trxType
        && (!row.outTime || t.handleDateTime === row.outTime)
        && (!row.ticketTransSeq || t.ticketTransSeq === row.ticketTransSeq))
      txnRows[row.orderNo] = rows
      txnState[row.orderNo] = 'done'
    }).catch(() => {
      txnRows[row.orderNo] = []
      txnState[row.orderNo] = 'error'
    })
}

function statusType(status) {
  return { SUCCESS: 'success', PROCESSING: 'warning', RETRY: 'warning', FAIL: 'danger', CLOSED: 'info' }[status] || 'info'
}

function canRefund(row) {
  return ['SUCCESS', 'PROCESSING'].includes(row.debitStatus) && Number(row.totalAmount) > 0 && !['0445', '0446', '0447', '0448'].includes(row.cardType)
}

function openRefund(row) {
  currentOrder.value = row
  refundForm.amountYuan = Number(row.totalAmount) / 100
  refundForm.refundReason = ''
  refundOpen.value = true
}

function submitRefund() {
  proxy.$refs.refundRef.validate((valid) => {
    if (!valid) return
    const refundAmount = Math.round(Number(refundForm.amountYuan) * 100)
    if (refundAmount <= 0 || refundAmount > Number(currentOrder.value.totalAmount)) {
      proxy.$modal.msgWarning('退款金额须大于 0 且不超过订单扣费总额')
      return
    }
    proxy.$modal.confirm(`确认对扣费订单“${currentOrder.value.orderNo}”发起 ¥ ${(refundAmount / 100).toFixed(2)} 的退款？`).then(() => {
      refunding.value = true
      return requestGateTxnPayRefund(currentOrder.value.orderNo, { refundAmount, refundReason: refundForm.refundReason.trim() })
    }).then(() => {
      proxy.$modal.msgSuccess('退款请求已提交，请稍后刷新查看退款状态')
      refundOpen.value = false
      getList()
    }).catch(() => { }).finally(() => { refunding.value = false })
  })
}

function handleQuery() {
  if (!hasSearchScope()) {
    proxy.$modal.msgWarning('请填写扣费订单号、逻辑卡号、第三方用户 ID，或完整的交易日期范围')
    return
  }
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.orderNo = ''
  queryParams.cardId = route.query.cardId || ''
  queryParams.thirdUserId = ''
  queryParams.signChannelCode = ''
  queryParams.cardType = ''
  queryParams.debitStatus = ''
  queryParams.dateRange = initialDateRange()
  orders.value = []
  total.value = 0
  refundsByOrder.value = {}
  supplementsByOrder.value = {}
  clearExpandCache()
}

function formatAmount(value) {
  return value == null || value === '' ? '-' : (Number(value) / 100).toFixed(2)
}

function formatTransactionTime(value) {
  if (!value) return '-'
  const text = String(value)
  if (/^\d{14}$/.test(text)) return `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)} ${text.slice(8, 10)}:${text.slice(10, 12)}:${text.slice(12, 14)}`
  return proxy.parseTime(value) || text
}

function formatTransactionType(trxType) {
  return { '01': '01-进站', '02': '02-出站', '03': '03-超时出站' }[trxType] || trxType || '-'
}

function formatHandleResult(code) {
  return code || '-'
}

/** 导出当前页已加载行（含回填后的退款/补款列），口径与页面表格一一对应。 */
function handleExport() {
  if (!orders.value.length) {
    proxy.$modal.msgWarning('暂无可导出的数据，请先查询')
    return
  }
  exporting.value = true
  try {
    const rows = orders.value.map((row) => ({
      orderNo: row.orderNo,
      debitStatus: debitStatusLabel(row.debitStatus),
      cardId: row.cardId,
      entryStation: row.entryStationName || row.inStation || '-',
      exitStation: row.exitStationName || row.outStation || '-',
      outTime: row.outTime || '-',
      trxAmount: formatAmount(row.trxAmount),
      overtimeAmount: formatAmount(row.overtimeAmount),
      totalAmount: formatAmount(row.totalAmount),
      signChannel: signChannelLabel(row.signChannelCode),
      refund: refundSummary(row).label,
      refundedAmount: formatAmount(refundSummary(row).refundedAmount),
      supplementAmount: formatAmount(supplementSummary(row).totalAmount),
      supplementChannel: supplementSummary(row).channelLabel,
      supplementOrderNo: supplementSummary(row).orderNoLabel,
      cardType: cardTypeLabel(row.cardType)
    }))
    const headers = [
      { key: 'orderNo', header: '扣费订单号' },
      { key: 'debitStatus', header: '扣费状态' },
      { key: 'cardId', header: '逻辑卡号' },
      { key: 'entryStation', header: '进站站点' },
      { key: 'exitStation', header: '出站站点' },
      { key: 'outTime', header: '出站时间' },
      { key: 'trxAmount', header: '基础金额(元)' },
      { key: 'overtimeAmount', header: '超时金额(元)' },
      { key: 'totalAmount', header: '扣费总额(元)' },
      { key: 'signChannel', header: '签约渠道' },
      { key: 'refund', header: '退款状态' },
      { key: 'refundedAmount', header: '已退金额(元)' },
      { key: 'supplementAmount', header: '补款金额(元)' },
      { key: 'supplementChannel', header: '补款支付渠道' },
      { key: 'supplementOrderNo', header: '补款订单号' },
      { key: 'cardType', header: '票种' }
    ]
    exportToExcel(rows, headers, `扣费明细_${new Date().toISOString().slice(0, 10)}`)
    proxy.$modal.msgSuccess(`已导出当前页 ${rows.length} 条`)
  } catch (error) {
    proxy.$modal.msgError(error.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  if (hasSearchScope()) getList()
})
</script>

<style scoped>
.txn-panel {
  padding: 8px 16px 12px 48px;
  background: var(--el-fill-color-light);
}
.txn-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.unit-label {
  margin-left: 6px;
}
</style>
