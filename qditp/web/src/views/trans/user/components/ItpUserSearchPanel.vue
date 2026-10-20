<template>
  <div class="app-container">
    <el-alert v-if="isActive" :title="alertTitle" :type="alertType" :closable="false" show-icon class="mb8" />

    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="查询字段" prop="queryType">
        <el-select v-model="queryParams.queryType" style="width: 150px">
          <el-option label="手机号" value="MSISDN" />
          <el-option label="逻辑卡号" value="CARD_ID" />
          <el-option label="第三方用户 ID" value="THIRD_USER_ID" />
        </el-select>
      </el-form-item>
      <el-form-item label="查询关键字" prop="keyword">
        <el-input v-model="queryParams.keyword" :placeholder="keywordPlaceholder" clearable style="width: 280px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 有效用户列 -->
    <el-table v-if="isActive" ref="tableRef" v-loading="loading" :data="users" border>
      <el-table-column label="第三方用户 ID" prop="thirdUserId" min-width="220" show-overflow-tooltip />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="真实卡类型" width="140" align="center"><template #default="{ row }">{{ cardTypeLabel(row.cardType) }}</template></el-table-column>
      <el-table-column label="申请卡类型" width="140" align="center"><template #default="{ row }">{{ cardTypeLabel(row.itpCardType) }}</template></el-table-column>
      <el-table-column label="手机号" prop="msisdn" width="140" align="center" />
      <el-table-column label="姓名" prop="userName" width="100" align="center" />
      <el-table-column label="发卡机构" width="180" align="center"><template #default="{ row }">{{ issueOrgLabel(row.issueOrgCode) }}</template></el-table-column>
      <el-table-column label="默认渠道" width="150" align="center"><template #default="{ row }">{{ payChannelLabel(row.channel) }}</template></el-table-column>
      <el-table-column label="二维码类型" width="140" align="center"><template #default="{ row }"><el-tag :type="qrCodeTypeTag(row.companionFlag)">{{ qrCodeTypeLabel(row.companionFlag) }}</el-tag></template></el-table-column>
      <el-table-column label="注册状态" prop="status" width="100" align="center"><template #default="{ row }"><el-tag :type="row.status === '有效' ? 'success' : row.status === '已注销' ? 'info' : 'danger'">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column label="申卡时间" width="170" align="center"><template #default="{ row }">{{ formatRegistrationTime(row.regTms) }}</template></el-table-column>
      <el-table-column label="申请解绑日期" width="170" align="center"><template #default="{ row }">{{ formatRegistrationTime(row.terminationRequestTime) }}</template></el-table-column>
      <el-table-column label="解绑成功日期" width="170" align="center"><template #default="{ row }">{{ formatRegistrationTime(row.terminationCompleteTime) }}</template></el-table-column>
      <el-table-column label="操作" width="390" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="Wallet" @click="openPayChannels(row)">签约渠道</el-button>
          <el-button link type="primary" icon="Document" @click="openTransactionDetail(row)">交易明细</el-button>
          <el-button link type="primary" icon="Tickets" @click="openDebitDetail(row)">扣费信息</el-button>
          <el-button link type="warning" icon="EditPen" @click="openRideStatus(row)">乘车状态</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 已注销用户列（数据源为 USER_ITP_REG_LOG） -->
    <el-table v-else ref="tableRef" v-loading="loading" :data="users" border>
      <el-table-column label="第三方用户 ID" prop="thirdUserId" min-width="220" show-overflow-tooltip />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="卡类型" width="140" align="center"><template #default="{ row }">{{ cardTypeLabel(row.cardType) }}</template></el-table-column>
      <el-table-column label="手机号" prop="msisdn" width="140" align="center" />
      <el-table-column label="销户时间" width="170" align="center"><template #default="{ row }">{{ formatRegistrationTime(row.cancelTime) }}</template></el-table-column>
      <el-table-column label="日志类型" width="120" align="center"><template #default="{ row }"><el-tag type="info">{{ operTypeLabel(row.operType) }}</el-tag></template></el-table-column>
    </el-table>
  </div>
</template>

<script setup name="ItpUserSearchPanel">
import { searchItpUsers, searchItpCanceledUsers } from '@/api/trans/userSearch'
import { formatCodeLabel } from '@/utils/codeLabel'
import { cardTypeLabel } from '@/utils/cardType'
import { payChannelLabel } from '@/utils/payChannel'

const props = defineProps({
  fixedStatus: { type: String, required: true, validator: (value) => ['ACTIVE', 'CANCELED'].includes(value) },
  showOperations: { type: Boolean, default: true }
})

const { proxy } = getCurrentInstance()
const router = useRouter()
const tableRef = ref()
const loading = ref(false)
const users = ref([])
const queryParams = reactive({ queryType: 'MSISDN', keyword: '' })
const keywordPlaceholder = computed(() => ({ MSISDN: '请输入手机号', CARD_ID: '请输入逻辑卡号', THIRD_USER_ID: '请输入第三方用户 ID' })[queryParams.queryType])

const isActive = computed(() => props.fixedStatus === 'ACTIVE')
const alertType = computed(() => (isActive.value ? 'info' : 'warning'))
const alertTitle = computed(() =>
  isActive.value
    ? '当前页面只查询 USER_ITP_REG_INFO 的「有效」记录；已注销记录请在「已注销用户查询」页查看。'
    : '当前页面查询 USER_ITP_REG_LOG 的「销户」记录（OPER_TYPE=2），覆盖已被物理归档的已注销用户。'
)

const issueOrgLabels = {
  '0004': '海上巴士',
  '0007': '支付宝出行',
  '0008': '成都地铁',
  '0020': '青岛地铁（早期）',
  '5412': '青岛地铁',
  '5413': '畅行U惠小程序'
}

function issueOrgLabel(value) {
  return formatCodeLabel(value, issueOrgLabels)
}

function qrCodeTypeLabel(companionFlag) {
  const flag = companionFlag?.trim()
  if (flag === 'Y') return '同行码'
  if (flag === 'C') return '第三方码'
  return '后付费二维码'
}

function qrCodeTypeTag(companionFlag) {
  const flag = companionFlag?.trim()
  if (flag === 'Y') return 'warning'
  if (flag === 'C') return 'info'
  return 'success'
}

function operTypeLabel(operType) {
  if (operType === 2) return '销户'
  if (operType === 3) return '归档'
  return '未知'
}

function formatRegistrationTime(value) {
  if (!value || /^0[-/]0[-/]0(?:\s|T)0:0:0(?:\.0+)?$/.test(String(value))) return '-'
  return proxy.parseTime(value) || '-'
}

function handleQuery() {
  if (!queryParams.keyword?.trim()) {
    proxy.$modal.msgWarning('请输入查询关键字')
    return
  }
  loading.value = true
  const params = {
    queryType: queryParams.queryType,
    keyword: queryParams.keyword.trim()
  }
  const request = isActive.value
    ? searchItpUsers({ ...params, status: props.fixedStatus })
    : searchItpCanceledUsers(params)
  request.then((response) => {
    users.value = normalizeRows(response)
    nextTick(() => tableRef.value?.doLayout())
    if (!users.value.length) proxy.$modal.msgInfo('未查询到记录')
  }).finally(() => { loading.value = false })
}

function normalizeRows(response) {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.data?.data)) return response.data.data
  return []
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.queryType = 'MSISDN'
  users.value = []
}

function openPayChannels(row) {
  router.push({
    path: '/trans/user-detail/pay-channel',
    query: { thirdUserId: row.thirdUserId, cardId: row.cardId, cardType: row.cardType }
  })
}

function openTransactionDetail(row) {
  router.push({
    path: '/trans/user-detail/transaction-detail',
    query: { cardId: row.cardId }
  })
}

function openDebitDetail(row) {
  router.push({
    path: '/trans/user-detail/debit-detail',
    query: { cardId: row.cardId }
  })
}

function openRideStatus(row) {
  router.push({
    path: '/trans/user-detail/ride-status',
    query: { thirdUserId: row.thirdUserId, cardId: row.cardId, cardType: row.cardType }
  })
}
</script>
