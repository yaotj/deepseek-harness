<template>
  <div class="app-container">
    <el-table v-loading="summaryLoading" :data="summary" border>
      <el-table-column label="完整票种" width="170" align="center"><template #default="{ row }">{{ ticketTypeLabel(row.cardType) }}</template></el-table-column>
      <el-table-column label="ACC 子类型" prop="accTicketType" width="110" align="center" />
      <el-table-column label="发卡模式" width="170" align="center"><template #default="{ row }"><el-tag :type="row.poolEnabled ? 'success' : 'info'">{{ row.poolEnabled ? '卡池发卡' : (row.issueMode === 'SECURITY_SERVICE' ? '安全服务实时发卡' : '外部实时发卡') }}</el-tag></template></el-table-column>
      <el-table-column label="可用余量" prop="availableCount" width="120" align="right" />
      <el-table-column label="预占数量" prop="reservedCount" width="120" align="right" />
      <el-table-column label="已分配" prop="assignedCount" width="120" align="right" />
      <el-table-column label="低库存" width="100" align="center"><template #default="{ row }"><el-tag v-if="row.poolEnabled" :type="row.belowThreshold ? 'danger' : 'success'">{{ row.belowThreshold ? '低于 10000' : '正常' }}</el-tag><span v-else>-</span></template></el-table-column>
      <el-table-column label="最近批次号" prop="latestBatchNo" width="120" align="center" />
      <el-table-column label="最近文件" prop="latestFileName" min-width="180" show-overflow-tooltip />
      <el-table-column label="最近状态" prop="latestStatus" width="120" align="center" />
      <el-table-column label="操作" width="120" fixed="right" align="center"><template #default="{ row }"><el-button v-if="row.poolEnabled" v-hasPermi="['trans:card-pool:apply']" link type="primary" icon="Plus" @click="createBatch(row)">申请批次</el-button><span v-else>-</span></template></el-table-column>
    </el-table>

    <el-divider content-position="left">卡池批次</el-divider>
    <el-form ref="queryRef" :inline="true" :model="query">
      <el-form-item label="批次号"><el-input v-model="query.batchNo" clearable placeholder="六位批次号" style="width: 130px" @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="票种"><el-select v-model="query.cardType" clearable style="width: 190px"><el-option v-for="item in ticketTypes" :key="item" :label="ticketTypeLabel(item)" :value="item" /></el-select></el-form-item>
      <el-form-item label="ACC 子类型"><el-select v-model="query.accTicketType" clearable style="width: 120px"><el-option v-for="item in accTicketTypes" :key="item" :label="item" :value="item" /></el-select></el-form-item>
      <el-form-item label="来源"><el-select v-model="query.source" clearable style="width: 110px"><el-option label="自动" value="AUTO" /><el-option label="人工" value="MANUAL" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable style="width: 150px"><el-option v-for="item in statuses" :key="item" :label="item" :value="item" /></el-select></el-form-item>
      <el-form-item label="创建时间"><el-date-picker v-model="query.dateRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" style="width: 340px" /></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">查询</el-button><el-button icon="Refresh" @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-table v-loading="batchLoading" :data="batches" border>
      <el-table-column label="批次号" prop="batchNo" width="100" align="center"><template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">{{ row.batchNo }}</el-button></template></el-table-column>
      <el-table-column label="票种" width="160" align="center"><template #default="{ row }">{{ ticketTypeLabel(row.cardType) }}</template></el-table-column>
      <el-table-column label="ACC 子类型" prop="accTicketType" width="105" align="center" />
      <el-table-column label="来源" prop="source" width="100" align="center" />
      <el-table-column label="文件名" prop="fileName" min-width="180" show-overflow-tooltip />
      <el-table-column label="申请数" prop="requestNum" width="100" align="right" />
      <el-table-column label="有效/重复/非法" min-width="150" align="center"><template #default="{ row }">{{ row.validCount || 0 }} / {{ row.duplicateCount || 0 }} / {{ row.invalidCount || 0 }}</template></el-table-column>
      <el-table-column label="状态" prop="status" width="120" align="center"><template #default="{ row }"><el-tag :type="tagType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column label="失败原因" prop="errorMsg" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right" align="center"><template #default="{ row }"><el-button v-if="row.status === 'FAILED' && row.fileName" v-hasPermi="['trans:card-pool:retry']" link type="warning" icon="RefreshRight" @click="retry(row)">重试导入</el-button><span v-else>-</span></template></el-table-column>
    </el-table>
    <pagination v-show="batchTotal > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="batchTotal" @pagination="loadBatches" />

    <el-dialog v-model="detailOpen" title="逻辑卡号批次详情" width="820px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="批次号">{{ currentBatch.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="ACC 请求批次号">{{ currentBatch.requestSeq }}</el-descriptions-item>
        <el-descriptions-item label="完整票种">{{ ticketTypeLabel(currentBatch.cardType) }}</el-descriptions-item>
        <el-descriptions-item label="ACC 子类型">{{ currentBatch.accTicketType }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ currentBatch.source }}</el-descriptions-item>
        <el-descriptions-item label="请求数量">{{ currentBatch.requestNum }}</el-descriptions-item>
        <el-descriptions-item label="FTP 文件名" :span="2">{{ currentBatch.fileName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="FTP 路径" :span="2">{{ currentBatch.ftpPath || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ currentBatch.fileSize ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="SHA-256">{{ currentBatch.fileSha256 || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总行数">{{ currentBatch.totalCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="有效数">{{ currentBatch.validCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="重复数">{{ currentBatch.duplicateCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="非法数">{{ currentBatch.invalidCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ currentBatch.retryCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentBatch.operator || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentBatch.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ currentBatch.finishTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">{{ currentBatch.errorMsg || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup name="CardPoolManagement">
import { getCardPoolSummary, listCardPoolBatches, requestCardPoolBatch, retryCardPoolBatch } from '@/api/trans/cardPool'

const { proxy } = getCurrentInstance()
const summaryLoading = ref(false)
const batchLoading = ref(false)
const summary = ref([])
const batches = ref([])
const batchTotal = ref(0)
const detailOpen = ref(false)
const currentBatch = ref({})
const query = reactive({ batchNo: '', cardType: '', accTicketType: '', source: '', status: '', dateRange: [], pageNum: 1, pageSize: 10 })
const TICKET_TYPE_NAMES = { '0441': '后付费二维码', '0442': 'HCE', '0443': '新HCE', '0444': '员工码', '0445': '一日票', '0446': '三日票', '0447': '七日票', '0448': '月票', '044A': '爱山东' }
const ticketTypes = Object.keys(TICKET_TYPE_NAMES)
const accTicketTypes = ['41', '42', '43', '44', '45', '46', '47', '48', '4A']
const statuses = ['CREATED', 'REQUESTING', 'DOWNLOADING', 'IMPORTING', 'SUCCESS', 'FAILED']

function ticketTypeLabel(cardType) { const name = TICKET_TYPE_NAMES[cardType]; return name ? `${cardType} ${name}` : (cardType || '-') }
function loadSummary() { summaryLoading.value = true; getCardPoolSummary().then(res => { summary.value = res.data || [] }).finally(() => { summaryLoading.value = false }) }
function loadBatches() { batchLoading.value = true; const { dateRange, ...params } = query; listCardPoolBatches({ ...params, createTimeBegin: dateRange?.[0], createTimeEnd: dateRange?.[1] }).then(res => { const page = res.data || {}; batches.value = page.list || []; batchTotal.value = Number(page.total || 0) }).finally(() => { batchLoading.value = false }) }
function handleQuery() { query.pageNum = 1; loadBatches() }
function reset() { proxy.resetForm('queryRef'); query.dateRange = []; handleQuery() }
function createBatch(row) { proxy.$modal.confirm(`将为 ${ticketTypeLabel(row.cardType)} 向 ACC 申请固定 100000 个逻辑卡号。`).then(() => requestCardPoolBatch(row.cardType)).then(() => { proxy.$modal.msgSuccess('批次申请已执行'); loadSummary(); handleQuery() }).catch(() => {}) }
function retry(row) { retryCardPoolBatch(row.batchNo).then(() => { proxy.$modal.msgSuccess('已重试下载和导入'); loadSummary(); loadBatches() }) }
function showDetail(row) { currentBatch.value = row; detailOpen.value = true }
function tagType(status) { return status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : 'warning' }
loadSummary(); loadBatches()
</script>
