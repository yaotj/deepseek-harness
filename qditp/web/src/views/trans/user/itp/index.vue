<template>
  <div class="app-container">
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

    <el-alert title="当前页面查询其他渠道注册表 USER_ITP_REG_INFO 的有效与已注销记录；已销户归档的用户记录已被物理删除，不在查询范围。" type="info" :closable="false" show-icon class="mb8" />

    <el-table ref="tableRef" v-loading="loading" :data="users" border>
      <el-table-column label="第三方用户 ID" prop="thirdUserId" min-width="220" show-overflow-tooltip />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="真实卡类型" width="140" align="center"><template #default="{ row }">{{ cardTypeLabel(row.cardType) }}</template></el-table-column>
      <el-table-column label="申请卡类型" width="140" align="center"><template #default="{ row }">{{ cardTypeLabel(row.itpCardType) }}</template></el-table-column>
      <el-table-column label="手机号" prop="msisdn" width="140" align="center" />
      <el-table-column label="姓名" prop="userName" width="100" align="center" />
      <el-table-column label="发卡机构" width="180" align="center"><template #default="{ row }">{{ issueOrgLabel(row.issueOrgCode) }}</template></el-table-column>
      <el-table-column label="默认渠道" width="150" align="center"><template #default="{ row }">{{ channelLabel(row.channel) }}</template></el-table-column>
      <el-table-column label="二维码类型" width="140" align="center"><template #default="{ row }"><el-tag :type="qrCodeTypeTag(row.companionFlag)">{{ qrCodeTypeLabel(row.companionFlag) }}</el-tag></template></el-table-column>
      <el-table-column label="注册状态" prop="status" width="100" align="center"><template #default="{ row }"><el-tag :type="row.status === '有效' ? 'success' : row.status === '已注销' ? 'info' : 'danger'">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column label="申卡时间" width="170" align="center"><template #default="{ row }">{{ formatRegistrationTime(row.regTms) }}</template></el-table-column>
      <el-table-column label="申请解绑日期" width="170" align="center"><template #default="{ row }">{{ formatRegistrationTime(row.terminationRequestTime) }}</template></el-table-column>
      <el-table-column label="解绑成功日期" width="170" align="center"><template #default="{ row }">{{ formatRegistrationTime(row.terminationCompleteTime) }}</template></el-table-column>
      <el-table-column label="操作" width="390" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="Wallet" :disabled="row.status !== '有效'" @click="openPayChannels(row)">签约渠道</el-button>
          <el-button link type="primary" icon="Document" @click="openTransactionDetail(row)">交易明细</el-button>
          <el-button link type="primary" icon="Tickets" @click="openDebitDetail(row)">扣费信息</el-button>
          <el-button link type="warning" icon="EditPen" @click="openRideStatus(row)">乘车状态</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup name="ItpUserSearch">
import { searchItpUsers } from '@/api/trans/userSearch'
import { formatCodeLabel } from '@/utils/codeLabel'

const { proxy } = getCurrentInstance()
const router = useRouter()
const tableRef = ref()
const loading = ref(false)
const users = ref([])
const queryParams = reactive({ queryType: 'MSISDN', keyword: '' })
const keywordPlaceholder = computed(() => ({ MSISDN: '请输入手机号', CARD_ID: '请输入逻辑卡号', THIRD_USER_ID: '请输入第三方用户 ID' })[queryParams.queryType])

const cardTypeLabels = {
  '02': '后付费二维码',
  '03': 'HCE卡',
  '04': '新HCE卡',
  '11': '员工码',
  '12': '一日票',
  '13': '三日票',
  '14': '七日票',
  '15': '多日计次票',
  '0441': '后付费二维码',
  '0442': 'HCE卡',
  '0443': '新HCE卡',
  '0444': '员工码',
  '0445': '一日票',
  '0446': '三日票',
  '0447': '七日票',
  '0448': '多日计次票'
}

/** 发卡机构：取 USER_ITP_REG_INFO.ISSUE_ORG_CODE 原值。 */
const issueOrgLabels = {
  '0004': '海上巴士',
  '0007': '支付宝出行',
  '0008': '成都地铁',
  '0020': '青岛地铁（早期）',
  '5412': '青岛地铁',
  '5413': '畅行U惠小程序'
}

/** 支付渠道：保持上送原值（含 0B/0C 这类 16 进制写法）做码值匹配，不做进制转换。 */
const channelLabels = {
  '03': '支付宝',
  '04': '微信',
  '05': '支付宝出行',
  '06': '龙支付',
  '0601': '招商银行',
  '0602': '中国银行',
  '08': '建行数币',
  '0801': '中行数币',
  '0802': '邮储数币',
  '0803': '交行数币',
  '0B': '钱包',
  '0C': '数币APP'
}

function cardTypeLabel(value) {
  return formatCodeLabel(value, cardTypeLabels)
}

function issueOrgLabel(value) {
  return formatCodeLabel(value, issueOrgLabels)
}

function channelLabel(value) {
  return formatCodeLabel(value, channelLabels)
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
  searchItpUsers({ ...queryParams, keyword: queryParams.keyword.trim() }).then((response) => {
    users.value = normalizeRows(response)
    // 异步数据渲染后重算布局，避免右侧固定列与主体行错位
    nextTick(() => tableRef.value?.doLayout())
    if (!users.value.length) proxy.$modal.msgInfo('未查询到其他渠道注册记录')
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

function openTransactionDetail(row) {
  router.push({ path: '/trans/user-detail/transaction-detail', query: { cardId: row.cardId } })
}

function openPayChannels(row) {
  router.push({
    path: '/trans/user-detail/pay-channel',
    query: {
      thirdUserId: row.thirdUserId,
      cardId: row.cardId,
      cardType: row.cardType,
      itpCardType: row.itpCardType,
      channel: row.channel
    }
  })
}

function openDebitDetail(row) {
  router.push({ path: '/trans/user-detail/debit-detail', query: { cardId: row.cardId } })
}

function openRideStatus(row) {
  router.push({ path: '/trans/user-detail/ride-status', query: { cardId: row.cardId, thirdUserId: row.thirdUserId } })
}
</script>

<style scoped>
/* 单元格不换行，保证右侧固定列与主体列行高一致（操作列按钮不堆叠） */
:deep(.el-table .cell) {
  white-space: nowrap;
}
</style>
