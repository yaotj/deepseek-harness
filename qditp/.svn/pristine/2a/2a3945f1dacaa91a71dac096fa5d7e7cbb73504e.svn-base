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

    <el-alert title="当前页面仅查询其他渠道注册表 USER_ITP_REG_INFO 的有效记录；同一第三方用户会展示其全部有效票卡。" type="info" :closable="false" show-icon class="mb8" />

    <el-table v-loading="loading" :data="users" border>
      <el-table-column label="第三方用户 ID" prop="thirdUserId" min-width="220" show-overflow-tooltip />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="真实卡类型" width="140" align="center"><template #default="{ row }">{{ cardTypeLabel(row.cardType) }}</template></el-table-column>
      <el-table-column label="申请卡类型" width="140" align="center"><template #default="{ row }">{{ cardTypeLabel(row.itpCardType) }}</template></el-table-column>
      <el-table-column label="手机号" prop="msisdn" width="140" align="center" />
      <el-table-column label="姓名" prop="userName" width="100" align="center" />
      <el-table-column label="发卡机构" prop="cardIssueCode" width="110" align="center" />
      <el-table-column label="默认渠道" width="120" align="center"><template #default="{ row }">{{ channelLabel(row.channel) }}</template></el-table-column>
      <el-table-column label="二维码类型" width="140" align="center"><template #default="{ row }"><el-tag :type="qrCodeTypeTag(row.companionFlag)">{{ qrCodeTypeLabel(row.companionFlag) }}</el-tag></template></el-table-column>
      <el-table-column label="注册状态" prop="status" width="100" align="center"><template #default="{ row }"><el-tag type="success">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column label="注册时间" width="170" align="center"><template #default="{ row }">{{ formatRegistrationTime(row.regTms) }}</template></el-table-column>
      <el-table-column label="操作" width="300" fixed="right" align="center">
        <template #default="{ row }">
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

const { proxy } = getCurrentInstance()
const router = useRouter()
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
  '15': '月票',
  '0441': '后付费二维码',
  '0442': 'HCE卡',
  '0443': '新HCE卡',
  '0444': '员工码',
  '0445': '一日票',
  '0446': '三日票',
  '0447': '七日票',
  '0448': '月票'
}

function cardTypeLabel(value) {
  return cardTypeLabels[value] || value || '-'
}

function channelLabel(value) {
  return value ? `渠道 ${value}` : '-'
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
  return parseTime(value) || '-'
}

function handleQuery() {
  if (!queryParams.keyword?.trim()) {
    proxy.$modal.msgWarning('请输入查询关键字')
    return
  }
  loading.value = true
  searchItpUsers({ ...queryParams, keyword: queryParams.keyword.trim() }).then((response) => {
    users.value = response.data || []
    if (!users.value.length) proxy.$modal.msgInfo('未查询到其他渠道注册用户')
  }).finally(() => { loading.value = false })
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.queryType = 'MSISDN'
  users.value = []
}

function openTransactionDetail(row) {
  router.push({ path: '/trans/user-detail/transaction-detail', query: { cardId: row.cardId } })
}

function openDebitDetail(row) {
  router.push({ path: '/trans/user-detail/debit-detail', query: { cardId: row.cardId } })
}

function openRideStatus(row) {
  router.push({ path: '/trans/user-detail/ride-status', query: { cardId: row.cardId, thirdUserId: row.thirdUserId } })
}
</script>
