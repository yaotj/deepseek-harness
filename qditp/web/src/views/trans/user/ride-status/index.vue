<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="逻辑卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 280px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button icon="Back" @click="router.back()">返回</el-button>
      </el-form-item>
    </el-form>

    <el-alert title="状态数据来源：QRCODE_STATUS。人工修改仅调整当前乘车状态，不会覆盖进出站及末次交易信息。" type="warning" :closable="false" show-icon class="mb8" />

    <el-descriptions v-if="rideStatus.cardId" v-loading="loading" title="当前乘车状态" :column="3" border>
      <el-descriptions-item label="逻辑卡号">{{ rideStatus.cardId }}</el-descriptions-item>
      <el-descriptions-item label="当前状态"><el-tag :type="statusTagType(rideStatus.codeStatus)">{{ formatStatus(rideStatus.codeStatus) }}</el-tag></el-descriptions-item>
      <el-descriptions-item label="状态编码">{{ rideStatus.codeStatus || '-' }}</el-descriptions-item>
      <el-descriptions-item label="进站车站">{{ rideStatus.gateInStation || '-' }}</el-descriptions-item>
      <el-descriptions-item label="进站时间">{{ rideStatus.gateInTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="末次交易车站">{{ rideStatus.lastTxnStation || '-' }}</el-descriptions-item>
      <el-descriptions-item label="末次交易时间">{{ rideStatus.lastTxnTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="交易流水号">{{ rideStatus.txnSeq || '-' }}</el-descriptions-item>
      <el-descriptions-item label="使用次数">{{ rideStatus.useCount ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ parseTime(rideStatus.updateTime) || '-' }}</el-descriptions-item>
      <el-descriptions-item label="操作">
        <el-button type="warning" icon="EditPen" @click="openUpdate" v-hasPermi="['trans:user:ride-status:edit']">修改乘车状态</el-button>
      </el-descriptions-item>
    </el-descriptions>

    <el-empty v-else-if="queried && !loading" description="未查询到乘车状态" :image-size="100" />

    <el-dialog v-model="updateOpen" title="修改乘车状态" width="500px" append-to-body destroy-on-close>
      <el-form ref="updateRef" :model="updateForm" :rules="updateRules" label-width="110px">
        <el-form-item label="逻辑卡号">{{ rideStatus.cardId }}</el-form-item>
        <el-form-item label="当前状态">{{ formatStatus(rideStatus.codeStatus) }}</el-form-item>
        <el-form-item label="目标状态" prop="codeStatus">
          <el-select v-model="updateForm.codeStatus" placeholder="请选择目标状态" style="width: 100%">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="修改原因" prop="changeReason">
          <el-input v-model="updateForm.changeReason" type="textarea" :rows="3" maxlength="100" show-word-limit placeholder="请输入修改原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="updateOpen = false">取消</el-button>
        <el-button type="warning" :loading="updating" @click="submitUpdate">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="UserRideStatus">
import { getRideStatus, updateRideStatus } from '@/api/trans/userSearch'

const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const updating = ref(false)
const queried = ref(false)
const updateOpen = ref(false)
const rideStatus = ref({})
const queryParams = reactive({ cardId: route.query.cardId || '' })
const updateForm = reactive({ codeStatus: '', changeReason: '' })
const statusOptions = [
  { value: '02', label: '0x02 结束行程' },
  { value: '03', label: '0x03 初始化' },
  { value: '04', label: '0x04 已进站' },
  { value: '05', label: '0x05 已出站' },
  { value: '06', label: '0x06 超时出站' },
  { value: '08', label: '0x08 20分钟内免费更新（BOM非付费区）' },
  { value: '09', label: '0x09 20分钟内付费更新（BOM非付费区）' },
  { value: '10', label: '0x10 入站码更新' },
  { value: '80', label: '0x80 APP自助补出站更新' },
  { value: '81', label: '0x81 APP自助补进站更新' }
]
const updateRules = {
  codeStatus: [{ required: true, message: '请选择目标状态', trigger: 'change' }],
  changeReason: [{ required: true, message: '请输入修改原因', trigger: 'blur' }]
}

function handleQuery() {
  if (!queryParams.cardId?.trim()) {
    proxy.$modal.msgWarning('请输入逻辑卡号')
    return
  }
  loading.value = true
  queried.value = true
  getRideStatus(queryParams.cardId.trim()).then((response) => {
    rideStatus.value = response.data || {}
  }).catch(() => { rideStatus.value = {} }).finally(() => { loading.value = false })
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.cardId = route.query.cardId || ''
  rideStatus.value = {}
  queried.value = false
  if (queryParams.cardId) handleQuery()
}

function openUpdate() {
  updateForm.codeStatus = rideStatus.value.codeStatus || ''
  updateForm.changeReason = ''
  updateOpen.value = true
}

function submitUpdate() {
  proxy.$refs.updateRef.validate((valid) => {
    if (!valid) return
    const targetLabel = formatStatus(updateForm.codeStatus)
    proxy.$modal.confirm(`确认将逻辑卡号“${rideStatus.value.cardId}”的乘车状态修改为“${targetLabel}”？`).then(() => {
      updating.value = true
      return updateRideStatus(rideStatus.value.cardId, { ...updateForm, changeReason: updateForm.changeReason.trim() })
    }).then((response) => {
      rideStatus.value = response.data || {}
      updateOpen.value = false
      proxy.$modal.msgSuccess('乘车状态修改成功')
    }).catch(() => {}).finally(() => { updating.value = false })
  })
}

function formatStatus(value) { return statusOptions.find((item) => item.value === value)?.label || (value ? `0x${value}` : '-') }
function statusTagType(value) { return ({ '04': 'warning', '05': 'success', '06': 'danger', '03': 'info' })[value] || 'info' }

if (queryParams.cardId) handleQuery()
</script>
