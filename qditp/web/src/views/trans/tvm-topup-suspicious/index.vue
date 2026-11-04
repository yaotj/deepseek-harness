<template>
  <div class="app-container">
    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="本页列出设备充值回执状态为存疑（02）的上报记录，对标旧 ITP「TVM充值存疑订单处理」；处置走既有内部补偿链路，本页只读。" />
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="接收时间" prop="dateRange">
        <query-time-range v-model="queryParams.dateRange" :value-format="DATE_FORMAT" :allow-all="false" @change="handleQuery" />
      </el-form-item>
      <el-form-item label="订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入 ITP 订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="受理渠道" prop="channel">
        <el-select v-model="queryParams.channel" clearable placeholder="全部" style="width: 130px">
          <el-option label="APP (01)" value="01" />
          <el-option label="TVM (02)" value="02" />
          <el-option label="BOM (03)" value="03" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备编号" prop="deviceId">
        <el-input v-model="queryParams.deviceId" placeholder="请输入设备编号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="回执状态" prop="topupStatus">
        <el-select v-model="queryParams.topupStatus" style="width: 150px">
          <el-option label="存疑 (02)" value="02" />
          <el-option label="成功 (00)" value="00" />
          <el-option label="失败 (01)" value="01" />
          <el-option label="取消 (03)" value="03" />
        </el-select>
      </el-form-item>
      <el-form-item label="处理状态" prop="processed">
        <el-select v-model="queryParams.processed" clearable placeholder="全部" style="width: 130px">
          <el-option label="已处理" value="1" />
          <el-option label="未处理" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="reports" border>
      <template #empty>
        <empty-state
          :error="queryError"
          :notice="filterNotice"
          :filters="queryParams"
          :range="queryParams.dateRange"
          :value-format="DATE_FORMAT"
          @update:range="queryParams.dateRange = $event"
          @relax="handleQuery"
          @clear="resetQuery"
          @retry="getList"
        />
      </template>
      <el-table-column label="订单号" prop="orderNo" min-width="200" show-overflow-tooltip />
      <el-table-column label="受理渠道" width="90" align="center"><template #default="{ row }">{{ formatChannel(row.channel) }}</template></el-table-column>
      <el-table-column label="设备编号" prop="deviceId" min-width="130" show-overflow-tooltip />
      <el-table-column label="操作员" prop="operatorId" width="110" align="center" />
      <el-table-column label="回执状态" width="90" align="center"><template #default="{ row }">{{ formatTopupStatus(row.topupStatus) }}</template></el-table-column>
      <el-table-column label="充值金额" width="105" align="right"><template #default="{ row }">{{ formatAmount(row.transAmount) }}</template></el-table-column>
      <el-table-column label="故障单号" prop="faultSlipSeq" min-width="150" show-overflow-tooltip />
      <el-table-column label="错误码" prop="errorCode" width="100" align="center" />
      <el-table-column label="错误信息" prop="errorMessage" min-width="160" show-overflow-tooltip />
      <el-table-column label="处理结论" width="110" align="center"><template #default="{ row }">{{ row.optResult || '-' }}</template></el-table-column>
      <el-table-column label="处理说明" prop="optResultDesc" min-width="160" show-overflow-tooltip />
      <el-table-column label="处理状态" width="90" align="center"><template #default="{ row }"><el-tag :type="row.processed === '1' ? 'success' : 'warning'">{{ row.processed === '1' ? '已处理' : '未处理' }}</el-tag></template></el-table-column>
      <el-table-column label="接收时间" prop="receiveTms" width="170" align="center" />
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </div>
</template>

<script setup name="TvmTopupSuspicious">
import { listTopupSuspicious } from '@/api/trans/topupSuspicious'
import { defaultTodayRange } from '@/utils/dateRange'
import { f2fChannelLabel } from '@/utils/f2fChannel'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const total = ref(0)
const reports = ref([])
const queryError = ref('')
const filterNotice = ref('')
const queryParams = reactive({
  dateRange: defaultTodayRange(DATE_FORMAT), orderNo: '', channel: '', deviceId: '',
  topupStatus: '02', processed: '', pageNum: 1, pageSize: 10
})

function buildQuery() {
  const { dateRange, ...params } = queryParams
  return { ...params, beginTime: dateRange?.[0], endTime: dateRange?.[1] }
}

function hasSearchScope() {
  return Boolean(queryParams.orderNo?.trim() || (queryParams.dateRange?.[0] && queryParams.dateRange?.[1]))
}

function getList() {
  queryError.value = ''
  filterNotice.value = ''
  if (!hasSearchScope()) {
    reports.value = []
    total.value = 0
    filterNotice.value = '请至少填写订单号，或完整的接收时间范围'
    return
  }
  loading.value = true
  listTopupSuspicious(buildQuery()).then((response) => {
    const page = response.data || {}
    reports.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  if (!hasSearchScope()) {
    proxy.$modal.msgWarning('请填写订单号，或完整的接收时间范围')
    return
  }
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.dateRange = defaultTodayRange(DATE_FORMAT)
  queryParams.topupStatus = '02'
  reports.value = []
  total.value = 0
}

function formatAmount(value) { return value == null || value === '' ? '-' : `¥ ${(Number(value) / 100).toFixed(2)}` }
function formatChannel(value) { return f2fChannelLabel(value) || value || '-' }
function formatTopupStatus(value) {
  return ({ '00': '成功', '01': '失败', '02': '存疑', '03': '取消' })[value] || value || '-'
}
</script>
