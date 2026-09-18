<template>
  <div class="app-container pay-channel-page">
    <el-page-header content="签约渠道管理" @back="goBack" />

    <el-card shadow="never" class="user-summary-card">
      <template #header>
        <div class="summary-header">
          <span>用户与票种信息</span>
          <el-button link type="primary" icon="Refresh" :loading="loading" @click="loadChannels">刷新</el-button>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="第三方用户 ID">{{ query.thirdUserId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="逻辑卡号">{{ query.cardId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="票种">{{ cardTypeLabel(query.cardType) }}</el-descriptions-item>
        <el-descriptions-item label="默认渠道">
          <el-tag v-if="defaultChannel" type="warning">{{ channelLabel(defaultChannel.channel) }}</el-tag>
          <span v-else>暂无默认渠道</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-alert
      class="channel-help"
      title="一个票种可以关联多个支付渠道，但同一时间只保留一个默认渠道。解约提交后进入后台解约流程，最终结果以刷新后的状态为准。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-table v-loading="loading" :data="channels" border empty-text="该票种暂无关联支付渠道">
      <el-table-column label="支付渠道" min-width="150">
        <template #default="{ row }">
          <span>{{ channelLabel(row.channel) }}</span>
          <el-tag v-if="row.defaultChannel" type="warning" size="small" class="default-tag">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.displayStatus === '解约中' ? 'warning' : row.displayStatus === '已解约' ? 'info' : 'success'">
            {{ row.displayStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="第三方支付账号" prop="thirdPayId" min-width="180" show-overflow-tooltip />
      <el-table-column label="支付账号" prop="payAccountId" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.payAccountId || '-' }}</template>
      </el-table-column>
      <el-table-column label="解约参数" min-width="240">
        <template #default="{ row }">
          <div>流水号：{{ row.reqContractNo || '-' }}</div>
          <div>支付方式：{{ row.channel || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.updateTms || row.createTms) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-if="row.displayStatus !== '已解约'"
            link
            type="danger"
            icon="CircleClose"
            :disabled="!row.terminationReady || row.displayStatus === '解约中'"
            @click="confirmTermination(row)"
          >
            {{ row.displayStatus === '解约中' ? '解约中' : '解约' }}
          </el-button>
          <span v-else class="muted-text">无需操作</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="terminationDialog.visible" title="确认解约" width="560px" destroy-on-close>
      <el-alert
        :title="terminationDialog.channel?.defaultChannel ? '当前渠道是该票种默认支付渠道，解约成功后默认渠道将被清空。' : '解约提交后将进入后台处理流程。'"
        type="warning"
        :closable="false"
        show-icon
        class="confirm-alert"
      />
      <el-descriptions v-if="terminationDialog.channel" :column="1" border>
        <el-descriptions-item label="支付渠道">{{ channelLabel(terminationDialog.channel.channel) }}</el-descriptions-item>
        <el-descriptions-item label="第三方用户 ID">{{ terminationDialog.channel.thirdUserId }}</el-descriptions-item>
        <el-descriptions-item label="逻辑卡号">{{ terminationDialog.channel.cardId }}</el-descriptions-item>
        <el-descriptions-item label="票种">{{ cardTypeLabel(terminationDialog.channel.cardType) }}</el-descriptions-item>
        <el-descriptions-item label="签约流水号">{{ terminationDialog.channel.reqContractNo || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="terminationDialog.visible = false">取消</el-button>
        <el-button type="danger" :loading="terminationDialog.submitting" @click="submitTermination">确认解约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="UserPayChannel">
import { executeTermination, getItpPayChannels } from '@/api/trans/userSearch'
import { formatCodeLabel } from '@/utils/codeLabel'

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const channels = ref([])
const query = reactive({
  thirdUserId: route.query.thirdUserId || '',
  cardId: route.query.cardId || '',
  cardType: route.query.cardType || '',
  itpCardType: route.query.itpCardType || ''
})
const terminationDialog = reactive({ visible: false, submitting: false, channel: null })

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

const defaultChannel = computed(() => channels.value.find((channel) => channel.defaultChannel))

function cardTypeLabel(value) {
  return formatCodeLabel(value, cardTypeLabels)
}

function channelLabel(value) {
  return formatCodeLabel(value, channelLabels)
}

function formatTime(value) {
  if (!value) return '-'
  return proxy.parseTime(value) || String(value)
}

function normalizeChannels(list) {
  return (list || []).map((channel) => ({
    ...channel,
    displayStatus: channel.status === 'TERMINATED' ? '已解约' : '已关联'
  }))
}

function loadChannels() {
  if (!query.thirdUserId || !query.cardId || !query.cardType) {
    proxy.$modal.msgError('缺少用户或票种参数，无法查询签约渠道')
    return
  }
  loading.value = true
  getItpPayChannels(query).then((response) => {
    channels.value = normalizeChannels(response.data)
    if (!channels.value.length) proxy.$modal.msgInfo('该票种暂无关联支付渠道')
  }).finally(() => { loading.value = false })
}

function confirmTermination(channel) {
  if (!channel.terminationReady) {
    proxy.$modal.msgWarning('该渠道缺少可用的签约流水号，暂不能解约')
    return
  }
  terminationDialog.channel = channel
  terminationDialog.visible = true
}

function submitTermination() {
  const channel = terminationDialog.channel
  if (!channel || terminationDialog.submitting) return
  terminationDialog.submitting = true
  executeTermination({
    thirdUserId: channel.thirdUserId,
    requestSignSeq: channel.reqContractNo,
    paymentVendor: channel.channel,
    cardId: channel.cardId,
    cardType: channel.cardType
  }).then(() => {
    channel.displayStatus = '解约中'
    channel.terminationReady = false
    terminationDialog.visible = false
    proxy.$modal.msgSuccess('解约请求已提交，当前状态为解约中')
  }).finally(() => { terminationDialog.submitting = false })
}

function goBack() {
  router.back()
}

loadChannels()
</script>

<style scoped>
.user-summary-card {
  margin-top: 16px;
}

.summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.channel-help,
.confirm-alert {
  margin: 16px 0;
}

.default-tag {
  margin-left: 8px;
}

.muted-text {
  color: #909399;
}
</style>
