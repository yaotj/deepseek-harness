<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="卡ID">
        <el-input v-model="queryParams.cardId" placeholder="请输入卡ID" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="三方用户ID">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入三方用户ID" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="渠道同步">
        <el-select v-model="queryParams.channelSyncStatus" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="item in SYNC_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker v-model="queryParams.dateRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" style="width: 340px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['trans:blacklist:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip content="重推所有「通知未推达」的加黑记录，与后台定时补偿走同一入口" placement="top">
          <el-button v-hasPermi="['trans:blacklist:remove']" plain icon="Refresh" :loading="compensating" @click="handleCompensate('add')">补推加黑通知</el-button>
        </el-tooltip>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip content="重推所有「解除中且通知未推达」的记录，推成功后该行才会真正解除" placement="top">
          <el-button v-hasPermi="['trans:blacklist:remove']" plain icon="Refresh" :loading="compensating" @click="handleCompensate('release')">补推解除通知</el-button>
        </el-tooltip>
      </el-col>
    </el-row>

    <el-alert type="info" :closable="false" show-icon class="mb8">
      <template #title>
        解除黑名单是两阶段：点「解除」后该行先变为<b>解除中</b>且仍算黑名单，解除通知推达支付宝后才真正放行并移入历史。通知推不动时可点上方「补推解除通知」。
      </template>
    </el-alert>

    <el-table v-loading="loading" :data="blacklist" border>
      <el-table-column label="序号" width="60" align="center">
        <template #default="scope">{{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}</template>
      </el-table-column>
      <el-table-column label="卡ID" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="三方用户ID" prop="thirdUserId" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.thirdUserId || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="渠道同步" width="180" align="center">
        <template #default="{ row }">
          <el-tag :type="syncTagType(row.channelSyncStatus)">{{ syncLabel(row.channelSyncStatus) }}</el-tag>
          <el-tooltip v-if="row.channelSyncFailReason" :content="row.channelSyncFailReason" placement="top">
            <el-button link type="danger" icon="WarningFilled" @click="showFailReason(row)" />
          </el-tooltip>
          <span v-if="row.channelSyncRetry > 0" class="retry-times">重试 {{ row.channelSyncRetry }} 次</span>
        </template>
      </el-table-column>
      <el-table-column label="拉黑原因" prop="reason" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '-' }}</template>
      </el-table-column>
      <el-table-column label="解除原因" prop="releaseReason" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.releaseReason || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170" align="center">
        <template #default="{ row }">{{ parseTime(row.createTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-tooltip :content="row.status === STATUS_RELEASING ? '该卡已在解除中，等通知推达即自动放行' : '解除黑名单'" placement="top">
            <el-button
              v-hasPermi="['trans:blacklist:remove']"
              link
              type="danger"
              icon="Delete"
              :disabled="row.status === STATUS_RELEASING"
              @click="handleRelease(row)"
            />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="open" title="新增黑名单" width="600px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="卡ID" prop="cardId">
          <el-input v-model="form.cardId" maxlength="32" placeholder="请输入卡ID" />
        </el-form-item>
        <el-form-item label="三方用户ID" prop="thirdUserId">
          <el-input v-model="form.thirdUserId" maxlength="16" placeholder="请输入三方用户ID" />
        </el-form-item>
        <el-form-item label="卡类型" prop="cardType">
          <el-input v-model="form.cardType" maxlength="8" placeholder="用于渠道同步，可不填" />
        </el-form-item>
        <el-form-item label="渠道" prop="channelCode">
          <el-select v-model="form.channelCode" placeholder="不选按「其他」处理" clearable style="width: 100%">
            <el-option v-for="item in CHANNEL_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="拉黑分类" prop="blackCause">
          <el-select v-model="form.blackCause" placeholder="不选按「其他」处理" clearable style="width: 100%">
            <el-option v-for="item in CAUSE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联业务号" prop="bizNo">
          <el-input v-model="form.bizNo" maxlength="128" placeholder="欠费订单号等，可不填" />
        </el-form-item>
        <el-form-item label="拉黑原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="请输入拉黑原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="releaseOpen" title="解除黑名单" width="560px" append-to-body destroy-on-close>
      <el-alert type="warning" :closable="false" show-icon class="mb8">
        <template #title>
          确认后该卡先置为<b>解除中</b>、仍算黑名单；解除通知推达支付宝后才真正放行。
        </template>
      </el-alert>
      <el-form ref="releaseFormRef" :model="releaseForm" :rules="releaseRules" label-width="100px">
        <el-form-item label="卡ID">
          <span>{{ releaseForm.cardId }}</span>
        </el-form-item>
        <el-form-item label="解除原因" prop="releaseReason">
          <el-input v-model="releaseForm.releaseReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入解除原因，会随记录进入历史表" />
        </el-form-item>
        <el-form-item label="操作人" prop="releaseBy">
          <el-input v-model="releaseForm.releaseBy" maxlength="32" placeholder="默认当前登录账号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="releaseOpen = false">取消</el-button>
        <el-button type="danger" :loading="releasing" @click="submitRelease">确认解除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="BlacklistManagement">
import {
  addBlacklist,
  compensateAddNotify,
  compensateReleaseNotify,
  listBlacklists,
  releaseBlacklist
} from '@/api/trans/blacklist'
import { defaultTodayRange } from '@/utils/dateRange'
import useUserStore from '@/store/modules/user'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

// 行状态：RELEASING 表示解除通知尚未推达渠道，该行仍算黑名单、判黑照样命中。
// 历史行（两阶段改造之前拉黑的）STATUS 可能为空，按「生效中」展示。
const STATUS_ACTIVE = 'ACTIVE'
const STATUS_RELEASING = 'RELEASING'
const STATUS_OPTIONS = [
  { value: STATUS_ACTIVE, label: '生效中' },
  { value: STATUS_RELEASING, label: '解除中' }
]

// 渠道同步状态取值与后端 outbox 四列一致；REJECTED 是终态（渠道明确业务拒绝，重推无用）。
const SYNC_OPTIONS = [
  { value: 'PENDING', label: '待推送' },
  { value: 'SUCCESS', label: '已推达' },
  { value: 'FAILED', label: '推送失败' },
  { value: 'REJECTED', label: '渠道拒绝' }
]
const SYNC_LABELS = SYNC_OPTIONS.reduce((acc, item) => ({ ...acc, [item.value]: item.label }), {})

// CHANNEL_CODE / BLACK_CAUSE 都是库里的两位码且 NOT NULL，不选时由后端兜默认值。
const CHANNEL_OPTIONS = [
  { value: '01', label: '01 支付宝' },
  { value: '99', label: '99 其他' }
]
const CAUSE_OPTIONS = [
  { value: '01', label: '01 欠费' },
  { value: '02', label: '02 风控' },
  { value: '09', label: '09 其他' }
]

const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const releasing = ref(false)
const compensating = ref(false)
const open = ref(false)
const releaseOpen = ref(false)
const blacklist = ref([])
const total = ref(0)
const queryParams = reactive({
  cardId: '',
  thirdUserId: '',
  status: '',
  channelSyncStatus: '',
  dateRange: defaultTodayRange(DATE_FORMAT),
  pageNum: 1,
  pageSize: 10
})
const form = reactive({ cardId: '', thirdUserId: '', cardType: '', channelCode: '', blackCause: '', bizNo: '', reason: '' })
const rules = {
  cardId: [
    { required: true, message: '请输入卡ID', trigger: 'blur' },
    { max: 32, message: '卡ID不能超过32个字符', trigger: 'blur' }
  ],
  thirdUserId: [{ max: 16, message: '三方用户ID不能超过16个字符', trigger: 'blur' }],
  // 长度按库里列宽写死：CARD_TYPE 是 VARCHAR2(8)，超了会在落库时报 ORA-12899。
  cardType: [{ max: 8, message: '卡类型不能超过8个字符', trigger: 'blur' }],
  bizNo: [{ max: 128, message: '关联业务号不能超过128个字符', trigger: 'blur' }],
  reason: [{ max: 1000, message: '拉黑原因不能超过1000个字符', trigger: 'blur' }]
}
const releaseForm = reactive({ cardId: '', releaseReason: '', releaseBy: '' })
const releaseRules = {
  releaseReason: [
    { required: true, message: '请输入解除原因', trigger: 'blur' },
    { max: 500, message: '解除原因不能超过500个字符', trigger: 'blur' }
  ],
  releaseBy: [{ max: 32, message: '操作人不能超过32个字符', trigger: 'blur' }]
}

function statusLabel(status) {
  return status === STATUS_RELEASING ? '解除中' : '生效中'
}

function statusTagType(status) {
  return status === STATUS_RELEASING ? 'warning' : 'danger'
}

function syncLabel(status) {
  return SYNC_LABELS[status] || '未纳管'
}

function syncTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'REJECTED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'info'
}

function getList() {
  loading.value = true
  const [createTimeBegin, createTimeEnd] = queryParams.dateRange || []
  listBlacklists({
    cardId: queryParams.cardId,
    thirdUserId: queryParams.thirdUserId,
    status: queryParams.status,
    channelSyncStatus: queryParams.channelSyncStatus,
    createTimeBegin,
    createTimeEnd,
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize
  }).then((response) => {
    const page = response.data || {}
    blacklist.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.cardId = ''
  queryParams.thirdUserId = ''
  queryParams.status = ''
  queryParams.channelSyncStatus = ''
  queryParams.dateRange = defaultTodayRange(DATE_FORMAT)
  handleQuery()
}

function resetForm() {
  form.cardId = ''
  form.thirdUserId = ''
  form.cardType = ''
  form.channelCode = ''
  form.blackCause = ''
  form.bizNo = ''
  form.reason = ''
  proxy.resetForm('formRef')
}

function handleAdd() {
  resetForm()
  open.value = true
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    submitting.value = true
    addBlacklist({
      cardId: form.cardId.trim(),
      thirdUserId: form.thirdUserId.trim() || null,
      cardType: form.cardType.trim() || null,
      channelCode: form.channelCode || null,
      blackCause: form.blackCause || null,
      bizNo: form.bizNo.trim() || null,
      reason: form.reason.trim() || null
    }).then(() => {
      proxy.$modal.msgSuccess('新增成功')
      open.value = false
      getList()
    }).finally(() => { submitting.value = false })
  })
}

function handleRelease(row) {
  releaseForm.cardId = row.cardId
  releaseForm.releaseReason = ''
  releaseForm.releaseBy = userStore.name || ''
  releaseOpen.value = true
}

function submitRelease() {
  proxy.$refs.releaseFormRef.validate((valid) => {
    if (!valid) return
    releasing.value = true
    releaseBlacklist(releaseForm.cardId, {
      releaseReason: releaseForm.releaseReason.trim(),
      releaseBy: releaseForm.releaseBy.trim() || null
    }).then(() => {
      // 刻意不说「移除成功」：此刻只是置为解除中，通知推达后才真正放行。
      proxy.$modal.msgSuccess('已发起解除，通知推达渠道后该卡才会放行')
      releaseOpen.value = false
      getList()
    }).finally(() => { releasing.value = false })
  })
}

function handleCompensate(direction) {
  const label = direction === 'add' ? '加黑' : '解除'
  const invoke = direction === 'add' ? compensateAddNotify : compensateReleaseNotify
  compensating.value = true
  invoke(200).then((response) => {
    const result = response || {}
    proxy.$modal.msgSuccess(`${label}通知补偿完成：扫描 ${result.scanned || 0} 条，成功 ${result.success || 0} 条，失败 ${result.failed || 0} 条`)
    getList()
  }).finally(() => { compensating.value = false })
}

function showFailReason(row) {
  proxy.$modal.msgError(`卡ID ${row.cardId} 最近一次通知失败原因：${row.channelSyncFailReason}`)
}

onMounted(getList)
</script>

<style scoped>
.retry-times {
  margin-left: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
