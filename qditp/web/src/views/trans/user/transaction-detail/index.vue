<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="逻辑卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="第三方用户 ID" prop="thirdUserId">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入第三方用户 ID" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="支付渠道" prop="signChannelCode">
        <el-input v-model="queryParams.signChannelCode" placeholder="请输入签约渠道编码" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="票种" prop="cardType">
        <el-input v-model="queryParams.cardType" placeholder="请输入票种编码" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="交易日期" prop="dateRange">
        <el-date-picker v-model="queryParams.dateRange" type="daterange" value-format="YYYYMMDD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button v-if="route.query.cardId" icon="Back" @click="router.back()">返回</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column label="交易时间" width="180" align="center"><template #default="{ row }">{{ formatTransactionTime(row.handleDateTime) }}</template></el-table-column>
      <el-table-column label="交易类型" min-width="190" align="center"><template #default="{ row }">{{ formatTransactionType(row.trxType) }}</template></el-table-column>
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="交易站点" min-width="170" align="center"><template #default="{ row }">{{ formatStation(row.lastHandleStationCode, row.lastHandleStationName) }}</template></el-table-column>
      <el-table-column label="交易时间" width="180" align="center"><template #default="{ row }">{{ formatTransactionTime(row.lastHandleDateTime) }}</template></el-table-column>
      <el-table-column label="出站站点" min-width="170" align="center"><template #default="{ row }">{{ formatStation(row.handleStationCode, row.handleStationName) }}</template></el-table-column>
      <el-table-column label="出站时间" width="180" align="center"><template #default="{ row }">{{ formatTransactionTime(row.handleDateTime) }}</template></el-table-column>
      <el-table-column label="交易金额" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.trxAmount) }}</template></el-table-column>
      <el-table-column label="超时金额" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.overtimeAmount) }}</template></el-table-column>
      <el-table-column label="处理结果" min-width="260" align="center"><template #default="{ row }">{{ formatHandleResult(row.handleResultCode) }}</template></el-table-column>
      <el-table-column label="交易流水号" prop="ticketTransSeq" min-width="180" show-overflow-tooltip />
      <el-table-column label="设备编号" prop="deviceId" min-width="130" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="UserTransactionDetail">
import { listQRCodeTxnDetails } from '@/api/trans/userSearch'
import { defaultTodayRange } from '@/utils/dateRange'
import { formatCodeLabel, formatCodeName } from '@/utils/codeLabel'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYYMMDD'

const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const total = ref(0)
const records = ref([])
const queryParams = reactive({ cardId: route.query.cardId || '', thirdUserId: '', signChannelCode: '', cardType: '', dateRange: defaultTodayRange(DATE_FORMAT), pageNum: 1, pageSize: 10 })

/** QRCODE_TXN_DETAIL.TRX_TYPE 码值翻译。 */
const transactionTypes = Object.freeze({
  '01': '进站',
  '02': '出站',
  '03': '超时出站'
})

const handleResults = Object.freeze({
  '000': '读写器返回成功', '005': '进出站不匹配（进站端）- 免费进站更新',
  '006': '进出站不匹配（进站端）- 付费进站更新', '020': '票卡状态位不正确',
  '082': '服务器数据签名验签错误', '083': '用户端数据签名验签错误', '084': '服务器指定的公钥PUB_KEY_IDX没找到',
  '085': '用户公钥已经过期', '086': '平台公钥已经过期', '087': '用户端时间戳过期（小于SC服务器时间超过5s以上）',
  '088': '用户端时间戳错误（大于SC服务器时间超过5s以上）', '089': '二维码车票已使用（已进站、已出站）',
  '090': '二维码车票已在本机使用过，拒绝再次使用', '091': 'ACC地铁行业数据签名验证错误',
  '101': '二维码读头初始化失败', '102': '蓝牙初始化失败', '103': '无二维码交易数据',
  '104': '黑名单二维码', '168': '二维码数据验证失败'
})

function buildQuery() {
  const { dateRange, ...params } = queryParams
  return { ...params, startDate: dateRange?.[0], endDate: dateRange?.[1] }
}

function getList() {
  if (!hasSearchScope()) {
    records.value = []
    total.value = 0
    return
  }
  loading.value = true
  listQRCodeTxnDetails({ ...buildQuery(), cardId: queryParams.cardId.trim() }).then((response) => {
    const page = response.data || {}
    records.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) {
    proxy.$modal.msgWarning('请填写逻辑卡号、第三方用户 ID，或完整的交易日期范围')
    return
  }
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.cardId = route.query.cardId || ''
  queryParams.thirdUserId = ''
  queryParams.signChannelCode = ''
  queryParams.cardType = ''
  queryParams.dateRange = defaultTodayRange(DATE_FORMAT)
  handleQuery()
}

function formatAmount(value) { return value == null ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }

function formatTransactionTime(value) {
  if (value == null || value === '') return '-'
  const rawValue = String(value).trim()
  if (/^\d{14}$/.test(rawValue)) {
    return `${rawValue.slice(0, 4)}-${rawValue.slice(4, 6)}-${rawValue.slice(6, 8)} ${rawValue.slice(8, 10)}:${rawValue.slice(10, 12)}:${rawValue.slice(12, 14)}`
  }
  return rawValue.replace('T', ' ').replace(/\.\d+$/, '')
}

function formatTransactionType(value) {
  return formatCodeLabel(normalizeCode(value), transactionTypes)
}

function formatHandleResult(value) {
  const code = String(value == null ? '' : value).trim().padStart(3, '0')
  return formatCodeLabel(code, handleResults)
}

function formatStation(code, name) {
  return formatCodeName(code, name)
}

function normalizeCode(value) {
  return String(value == null ? '' : value).trim().replace(/^0x/i, '').toUpperCase()
}

function hasSearchScope() {
  return Boolean(queryParams.cardId?.trim() || queryParams.thirdUserId?.trim() || (queryParams.dateRange?.[0] && queryParams.dateRange?.[1]))
}

if (queryParams.cardId) getList()
</script>
