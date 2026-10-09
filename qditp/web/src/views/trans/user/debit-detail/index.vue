<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="逻辑卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="第三方用户 ID" prop="thirdUserId">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入第三方用户 ID" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="扣费订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入扣费订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付渠道" prop="signChannelCode">
        <el-input v-model="queryParams.signChannelCode" placeholder="请输入签约渠道编码" clearable style="width: 170px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="票种" prop="cardType">
        <el-input v-model="queryParams.cardType" placeholder="请输入票种编码" clearable style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="扣费状态" prop="debitStatus">
        <el-select v-model="queryParams.debitStatus" clearable placeholder="全部" style="width: 130px">
          <el-option label="初始" value="INIT" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAIL" />
          <el-option label="待重试" value="RETRY" />
          <el-option label="关闭" value="CLOSED" />
        </el-select>
      </el-form-item>
      <el-form-item label="交易日期" prop="dateRange">
        <el-date-picker v-model="queryParams.dateRange" type="daterange" value-format="YYYYMMDD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button icon="Back" @click="router.back()">返回</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="orders" border>
      <el-table-column label="扣费订单号" prop="orderNo" min-width="210" show-overflow-tooltip />
      <el-table-column label="扣费状态" prop="debitStatus" width="110" align="center"><template #default="{ row }"><el-tag :type="statusType(row.debitStatus)">{{ row.debitStatus }}</el-tag></template></el-table-column>
      <el-table-column label="逻辑卡号" prop="cardId" min-width="170" show-overflow-tooltip />
      <el-table-column label="进站站点" width="110" align="center"><template #default="{ row }">{{ row.entryStationName || row.inStation || '-' }}</template></el-table-column>
      <el-table-column label="出站站点" width="110" align="center"><template #default="{ row }">{{ row.exitStationName || row.outStation || '-' }}</template></el-table-column>
      <el-table-column label="出站时间" prop="outTime" width="160" align="center" />
      <el-table-column label="基础金额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.trxAmount) }}</template></el-table-column>
      <el-table-column label="超时金额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.overtimeAmount) }}</template></el-table-column>
      <el-table-column label="扣费总额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
      <el-table-column label="签约渠道" prop="signChannelCode" width="110" align="center" />
      <el-table-column label="操作" width="100" fixed="right" align="center">
        <template #default="{ row }">
          <el-tooltip content="发起退款" placement="top">
            <el-button link type="danger" icon="Money" :disabled="!canRefund(row)" @click="openRefund(row)">退款</el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="refundOpen" title="发起扣费退款" width="460px" append-to-body destroy-on-close>
      <el-descriptions :column="1" border class="mb8">
        <el-descriptions-item label="扣费订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单扣费总额">{{ formatAmount(currentOrder.totalAmount) }}</el-descriptions-item>
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

<script setup name="UserDebitDetail">
import { listGateTxnPays, requestGateTxnPayRefund } from '@/api/trans/userSearch'
import { defaultTodayRange } from '@/utils/dateRange'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYYMMDD'

const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const refunding = ref(false)
const total = ref(0)
const orders = ref([])
const refundOpen = ref(false)
const currentOrder = ref({})
const refundForm = reactive({ amountYuan: undefined, refundReason: '' })
/**
 * 带 cardId 跳进来时**不预设日期窗**。
 *
 * 原实现无条件给 dateRange 填 [今天, 今天]，于是从「用户查询」页点「扣费信息」跳过来时，
 * 实际查的是「该卡今天的扣费」——只要用户今天没坐车，页面就空，看着像功能坏了
 * （2026-09-21 实测：该卡库里 11 行，分布在 20260918 / 20260920，当天 0 行）。
 *
 * cardId / orderNo 本身就是强定位键，hasSearchScope() 只要其一非空即放行，
 * 不需要再叠一个时间范围。NEVER 改回无条件填今天。
 */
function initialDateRange() {
  return route.query.cardId ? [] : defaultTodayRange(DATE_FORMAT)
}

const queryParams = reactive({
  orderNo: '', cardId: route.query.cardId || '', thirdUserId: '', signChannelCode: '', cardType: '', debitStatus: '', dateRange: initialDateRange(), pageNum: 1, pageSize: 10
})
const maxRefundAmount = computed(() => Number(currentOrder.value.totalAmount || 0) / 100)
const refundRules = {
  amountYuan: [{ required: true, message: '请输入退款金额', trigger: 'change' }],
  refundReason: [{ required: true, message: '请输入退款原因', trigger: 'blur' }]
}

function buildQuery() {
  const { dateRange, ...params } = queryParams
  return { ...params, startDate: dateRange?.[0], endDate: dateRange?.[1] }
}

function getList() {
  if (!hasSearchScope()) {
    orders.value = []
    total.value = 0
    return
  }
  loading.value = true
  listGateTxnPays({ ...buildQuery(), cardId: queryParams.cardId.trim() }).then((response) => {
    const page = response.data || {}
    orders.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
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
  queryParams.cardId = route.query.cardId || ''
  queryParams.orderNo = ''
  queryParams.thirdUserId = ''
  queryParams.signChannelCode = ''
  queryParams.cardType = ''
  queryParams.debitStatus = ''
  queryParams.dateRange = initialDateRange()
  handleQuery()
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
    }).then((response) => {
      const result = response.data || {}
      proxy.$modal.msgSuccess(`退款申请成功${result.refundOrderNo ? `，退款单号：${result.refundOrderNo}` : ''}`)
      refundOpen.value = false
      getList()
    }).catch(() => {}).finally(() => { refunding.value = false })
  })
}

function formatAmount(value) { return value == null ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
function statusType(value) { return ({ SUCCESS: 'success', PROCESSING: 'warning', RETRY: 'danger', FAIL: 'danger', CLOSED: 'info' })[value] || 'info' }
function hasSearchScope() {
  return Boolean(queryParams.orderNo?.trim() || queryParams.cardId?.trim() || queryParams.thirdUserId?.trim() || (queryParams.dateRange?.[0] && queryParams.dateRange?.[1]))
}

if (queryParams.cardId) getList()
</script>

<style scoped>
.unit-label {
  margin-left: 8px;
  color: var(--el-text-color-regular);
}
</style>
