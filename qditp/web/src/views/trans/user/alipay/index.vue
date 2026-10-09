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

    <el-alert title="当前页面仅查询支付宝注册表 ALIPAY_USER_INFO；可按逻辑卡号进入交易明细、扣费信息或乘车状态维护。" type="info" :closable="false" show-icon class="mb8" />

    <el-table v-loading="loading" :data="users" border>
      <el-table-column label="第三方用户 ID" prop="thirdUserId" min-width="220" show-overflow-tooltip />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="卡类型" width="150" align="center"><template #default="{ row }">{{ cardTypeLabel(row.cardType) }}</template></el-table-column>
      <el-table-column label="手机号" prop="msisdn" width="140" align="center" />
      <el-table-column label="开户渠道" width="140" align="center"><template #default="{ row }">{{ issueChannelLabel(row.channel) }}</template></el-table-column>
      <el-table-column label="支付用户标识" prop="thirdPayId" min-width="160" show-overflow-tooltip />
      <el-table-column label="用户状态" prop="status" width="110" align="center">
        <template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status || '-' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="注册时间" width="170" align="center"><template #default="{ row }">{{ parseTime(row.createTime) || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="300" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-hasPermi="['trans:user:txn-detail:query']" link type="primary" icon="Document" @click="openTransactionDetail(row)">交易明细</el-button>
          <el-button v-hasPermi="['trans:user:debit-detail:query']" link type="primary" icon="Tickets" @click="openDebitDetail(row)">扣费信息</el-button>
          <el-button v-hasPermi="['trans:user:ride-status:edit']" link type="warning" icon="EditPen" @click="openRideStatus(row)">乘车状态</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="AlipayUserSearch">
import { searchAlipayUsers } from '@/api/trans/userSearch'
import { formatCodeLabel } from '@/utils/codeLabel'

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const total = ref(0)
const users = ref([])
const queryParams = reactive({ queryType: 'MSISDN', keyword: '', pageNum: 1, pageSize: 10 })
const keywordPlaceholder = computed(() => ({ MSISDN: '请输入手机号', CARD_ID: '请输入逻辑卡号', THIRD_USER_ID: '请输入第三方用户 ID' })[queryParams.queryType])

/** ALIPAY_USER_INFO.CARD_TYPE 原样落开户请求上送值，两位与四位写法并存，与 trans/user/itp 页保持同一套码表。 */
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

/**
 * ALIPAY_USER_INFO.CHANNEL 是**发卡渠道**（`IssueChannelCodeEnum`，开户时固定写 ALIPAY），
 * NEVER 套用 trans/user/itp 页的 channelLabels —— 那张表是支付渠道（03 支付宝 / 04 微信 …），
 * 两者码值空间不同，混用会把 07 翻成错的名字。本表取值只能跟着 IssueChannelCodeEnum 走。
 */
const issueChannelLabels = {
  '01': '正常渠道',
  '07': '支付宝'
}

function cardTypeLabel(value) {
  return formatCodeLabel(value, cardTypeLabels)
}

function issueChannelLabel(value) {
  return formatCodeLabel(value, issueChannelLabels)
}

/** 翻页与查询共用；返回 promise 供调用方在拿到结果后判空。 */
function getList() {
  loading.value = true
  return searchAlipayUsers({ ...queryParams, keyword: queryParams.keyword.trim() }).then((response) => {
    const page = response.data || {}
    users.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!queryParams.keyword?.trim()) {
    proxy.$modal.msgWarning('请输入查询关键字')
    return
  }
  queryParams.pageNum = 1
  getList().then(() => {
    if (!users.value.length) proxy.$modal.msgInfo('未查询到支付宝注册用户')
  })
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.queryType = 'MSISDN'
  queryParams.pageNum = 1
  users.value = []
  total.value = 0
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
