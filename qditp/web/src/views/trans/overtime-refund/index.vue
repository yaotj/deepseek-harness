<template>
  <div class="app-container">
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" label-width="90px">
      <el-form-item label="车站">
        <el-select
          v-model="queryParams.stationCode"
          placeholder="全部车站"
          clearable
          filterable
          style="width: 220px"
        >
          <el-option
            v-for="item in stationOptions"
            :key="item.stationCode"
            :label="item.stationNm + '（' + item.stationCode + '）'"
            :value="item.stationCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="交易日期" required>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">圈单查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      type="warning"
      :closable="false"
      class="mb8"
      title="圈定口径：OVERTIME_AMOUNT>0 且 单边 且 超时出站 且 扣费成功/在途。圈出为候选单，勾选后逐单发起退款，金额为各自超时罚金，已退/不可退的会在结果里标失败。"
    />

    <el-row class="mb8" type="flex" justify="space-between" align="middle">
      <div>
        <el-button
          type="danger"
          icon="Coin"
          :disabled="multipleSelection.length === 0"
          :loading="refunding"
          @click="handleBatchRefund"
        >批量退超时罚金（已选 {{ multipleSelection.length }} 笔）</el-button>
        <span class="selected-summary" v-if="multipleSelection.length > 0">
          合计超时罚金 {{ selectedOvertimeTotal }} 元
        </span>
      </div>
      <span class="total-tip">共圈出 {{ total }} 笔候选单</span>
    </el-row>

    <el-table
      v-loading="loading"
      :data="orderList"
      border
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="序号" type="index" width="60" align="center">
        <template #default="scope">
          {{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column label="订单号" prop="orderNo" min-width="200" align="center" show-overflow-tooltip />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="150" align="center" show-overflow-tooltip />
      <el-table-column label="出站车站" min-width="130" align="center">
        <template #default="scope">{{ scope.row.exitStationName || scope.row.outStation || '-' }}</template>
      </el-table-column>
      <el-table-column label="超时罚金(元)" width="120" align="center">
        <template #default="scope">{{ fenToYuan(scope.row.overtimeAmount) }}</template>
      </el-table-column>
      <el-table-column label="实付(元)" width="110" align="center">
        <template #default="scope">{{ fenToYuan(scope.row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="扣费状态" prop="debitStatus" width="110" align="center" />
      <el-table-column label="出站时间" prop="outTime" min-width="150" align="center" />
    </el-table>

    <pagination
      v-show="total > 0"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />

    <el-dialog v-model="resultVisible" title="批量退款结果" width="760px" append-to-body>
      <el-row class="mb8">
        <el-tag type="info">共 {{ refundResult.total }} 笔</el-tag>
        <el-tag type="success" style="margin-left:8px">成功 {{ refundResult.successCount }}</el-tag>
        <el-tag type="danger" style="margin-left:8px">失败 {{ refundResult.failCount }}</el-tag>
      </el-row>
      <el-table :data="refundResult.items" border max-height="420">
        <el-table-column label="订单号" prop="orderNo" min-width="200" align="center" show-overflow-tooltip />
        <el-table-column label="退款金额(元)" width="120" align="center">
          <template #default="scope">{{ scope.row.refundAmount != null ? fenToYuan(scope.row.refundAmount) : '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.success ? 'success' : 'danger'">
              {{ scope.row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" prop="retMsg" min-width="220" align="center" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="OvertimeRefund">
import { listOvertimeRefundable, batchRefundOvertime } from '@/api/trans/overtimeRefund'
import { listStationInfo } from '@/api/para/station'
import { ElMessage, ElMessageBox } from 'element-plus'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const refunding = ref(false)
const orderList = ref([])
const total = ref(0)
const dateRange = ref([])
const stationOptions = ref([])
const multipleSelection = ref([])
const resultVisible = ref(false)
const refundResult = ref({ total: 0, successCount: 0, failCount: 0, items: [] })

const queryFormRef = ref()
const queryParams = reactive({ stationCode: undefined, pageNum: 1, pageSize: 10 })

const selectedOvertimeTotal = computed(() => {
  const fen = multipleSelection.value.reduce((sum, row) => sum + (Number(row.overtimeAmount) || 0), 0)
  return fenToYuan(fen)
})

function fenToYuan(fen) {
  const n = Number(fen)
  if (!Number.isFinite(n)) return '-'
  return (n / 100).toFixed(2)
}

function loadStations() {
  listStationInfo({ pageNum: 1, pageSize: 500 }).then((response) => {
    const page = response.data || {}
    stationOptions.value = page.list || []
  })
}

function getList() {
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请先选择交易日期范围')
    return
  }
  loading.value = true
  listOvertimeRefundable({
    stationCode: queryParams.stationCode,
    startDate: dateRange.value[0],
    endDate: dateRange.value[1],
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize
  }).then((response) => {
    const page = response.data || {}
    orderList.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  multipleSelection.value = []
  getList()
}

function resetQuery() {
  dateRange.value = []
  queryParams.stationCode = undefined
  queryParams.pageNum = 1
  orderList.value = []
  total.value = 0
  multipleSelection.value = []
}

function handleSelectionChange(rows) {
  multipleSelection.value = rows
}

function handleBatchRefund() {
  const count = multipleSelection.value.length
  if (count === 0) return
  if (count > 200) {
    ElMessage.warning('单批最多 200 笔，请分批提交')
    return
  }
  ElMessageBox.confirm(
    `将对选中的 ${count} 笔订单逐单发起超时罚金退款，合计 ${selectedOvertimeTotal.value} 元。已退/不可退的单笔会标记失败但不阻断整批。是否继续？`,
    '批量退款确认',
    { confirmButtonText: '确认退款', cancelButtonText: '取消', type: 'warning' }
  ).then(() => {
    refunding.value = true
    const orderNos = multipleSelection.value.map((row) => row.orderNo)
    batchRefundOvertime({ orderNos }).then((response) => {
      refundResult.value = response.data || { total: 0, successCount: 0, failCount: 0, items: [] }
      resultVisible.value = true
      getList()
    }).finally(() => { refunding.value = false })
  }).catch(() => {})
}

onMounted(loadStations)
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}
.selected-summary {
  margin-left: 12px;
  color: #e6a23c;
  font-weight: 600;
}
.total-tip {
  color: #909399;
  font-size: 13px;
}
</style>
