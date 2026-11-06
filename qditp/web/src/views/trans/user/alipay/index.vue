<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="手机号" prop="msisdn">
        <el-input v-model="queryParams.msisdn" placeholder="请输入手机号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="逻辑卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="第三方用户 ID" prop="thirdUserId">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入第三方用户 ID" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="渠道签约流水号" prop="reqContractNo">
        <el-input v-model="queryParams.reqContractNo" placeholder="请输入渠道签约流水号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="warning" icon="Download" :disabled="users.length === 0" @click="handleExport">导出 Excel</el-button>
        <el-button type="primary" icon="Clock" @click="handleAsyncExport">后台全量导出</el-button>
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
      <el-table-column label="渠道签约流水号" prop="reqContractNo" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.reqContractNo || '-' }}</template></el-table-column>
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
import { cardTypeLabel } from '@/utils/cardType'
import { exportToExcel } from '@/utils/excelExport'
import { parseTime } from '@/utils/points'
import { submitAsyncExportTask } from '@/api/monitor/asyncExport'

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const total = ref(0)
const users = ref([])
const queryParams = reactive({ msisdn: '', cardId: '', thirdUserId: '', reqContractNo: '', pageNum: 1, pageSize: 10 })

/**
 * ALIPAY_USER_INFO.CHANNEL 是**发卡渠道**（`IssueChannelCodeEnum`，开户时固定写 ALIPAY），
 * NEVER 套用 `@/utils/payChannel` 的 PAY_CHANNEL_LABELS —— 那张表是支付渠道（03 支付宝 / 04 微信 …），
 * 两者码值空间不同，混用会把 07 翻成错的名字。本表取值只能跟着 IssueChannelCodeEnum 走。
 */
const issueChannelLabels = {
  '01': '正常渠道',
  '07': '支付宝'
}

function issueChannelLabel(value) {
  return formatCodeLabel(value, issueChannelLabels)
}

/**
 * 收集已填写的筛选条件：条件之间是 AND，空白框不下发。
 *
 * @returns 至少填了一个条件时返回参数对象；全空返回 null（后端会拒绝，避免整表扫描）
 */
function collectFilters() {
  const fields = [
    ['msisdn', queryParams.msisdn],
    ['cardId', queryParams.cardId],
    ['thirdUserId', queryParams.thirdUserId],
    ['reqContractNo', queryParams.reqContractNo]
  ]
  const filled = fields.map(([key, value]) => [key, (value || '').trim()]).filter(([, value]) => value)
  return filled.length ? Object.fromEntries(filled) : null
}

/** 翻页与查询共用；返回 promise 供调用方在拿到结果后判空。无筛选条件时不发请求（后端会判成整表扫描）。 */
function getList() {
  const filters = collectFilters()
  if (!filters) {
    users.value = []
    total.value = 0
    return Promise.resolve()
  }
  loading.value = true
  return searchAlipayUsers({ ...filters, pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }).then((response) => {
    const page = response.data || {}
    users.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!collectFilters()) {
    proxy.$modal.msgWarning('请至少填写一个查询条件')
    return
  }
  queryParams.pageNum = 1
  getList().then(() => {
    if (!users.value.length) proxy.$modal.msgInfo('未查询到支付宝注册用户')
  })
}

function resetQuery() {
  proxy.resetForm('queryRef')
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

/** 提交后台异步导出任务（按当前筛选条件全量取数，每日凌晨 2 点统一执行）。 */
function handleAsyncExport() {
  const filters = collectFilters()
  if (!filters) {
    proxy.$modal.msgWarning('后台导出按当前筛选条件全量取数，请至少填写一个查询条件')
    return
  }
  const data = {
    taskCode: 'ALIPAY_USER',
    taskName: '支付宝用户全量导出',
    paramJson: JSON.stringify(filters)
  }
  submitAsyncExportTask(data).then((response) => {
    proxy.$modal.msgSuccess(response.msg || '提交成功，请前往「系统监控-异步导出任务」查看')
  }).catch((error) => {
    proxy.$modal.msgError(error.message || '提交失败')
  })
}

function handleExport() {
  const rows = users.value.map((row) => ({
    thirdUserId: row.thirdUserId,
    cardId: row.cardId,
    cardType: cardTypeLabel(row.cardType),
    msisdn: row.msisdn,
    issueChannel: issueChannelLabel(row.channel),
    thirdPayId: row.thirdPayId,
    reqContractNo: row.reqContractNo || '-',
    status: row.status || '-',
    createTime: parseTime(row.createTime) || '-'
  }))
  const headers = [
    { key: 'thirdUserId', header: '第三方用户 ID' },
    { key: 'cardId', header: '逻辑卡号' },
    { key: 'cardType', header: '卡类型' },
    { key: 'msisdn', header: '手机号' },
    { key: 'issueChannel', header: '开户渠道' },
    { key: 'thirdPayId', header: '支付用户标识' },
    { key: 'reqContractNo', header: '渠道签约流水号' },
    { key: 'status', header: '用户状态' },
    { key: 'createTime', header: '注册时间' }
  ]
  const dateStr = new Date().toISOString().slice(0, 10)
  exportToExcel(rows, headers, `支付宝用户_${dateStr}`)
  proxy.$modal.msgSuccess(`已导出当前页 ${rows.length} 条支付宝用户`)
}
</script>
