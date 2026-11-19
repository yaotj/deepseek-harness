<template>
  <div class="app-container">
    <el-form :model="query" :inline="true">
      <el-form-item label="逻辑卡号"><el-input v-model="query.cardId" clearable placeholder="请输入逻辑卡号" @keyup.enter="search"/></el-form-item>
      <el-form-item label="用户订单"><el-input v-model="query.userOrderNo" clearable placeholder="请输入用户订单号" @keyup.enter="search"/></el-form-item>
      <el-form-item label="规则编号"><el-input v-model="query.ruleId" clearable placeholder="请输入风险规则编号" @keyup.enter="search"/></el-form-item>
      <el-form-item label="风险状态"><el-input v-model="query.riskStatus" clearable placeholder="请输入风险状态" @keyup.enter="search"/></el-form-item>
      <el-form-item label="命中时间"><query-time-range v-model="query.dateRange" :value-format="DATE_FORMAT" @change="search" /></el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="warning" icon="Download" :loading="exporting" @click="handleExport">导出 Excel</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows" border>
      <template #empty>
        <empty-state
          :error="queryError"
          :filters="query"
          :range="query.dateRange"
          :value-format="DATE_FORMAT"
          @update:range="query.dateRange = $event"
          @relax="search"
          @clear="resetQuery"
          @retry="load"
        />
      </template>
      <el-table-column label="序号" width="70" align="center"><template #default="s">{{(query.pageNum-1)*query.pageSize+s.$index+1}}</template></el-table-column>
      <el-table-column label="逻辑卡号" prop="cardId" min-width="200" show-overflow-tooltip><template #default="{row}">{{row.cardId||'-'}}</template></el-table-column>
      <el-table-column label="用户订单" prop="userOrderNo" min-width="210" show-overflow-tooltip><template #default="{row}">{{row.userOrderNo||'-'}}</template></el-table-column>
      <el-table-column label="风险规则编号" prop="ruleId" width="140" align="center"/>
      <el-table-column label="风险规则名称" prop="ruleNameSnap" min-width="200"><template #default="{row}">{{row.ruleNameSnap||'-'}}</template></el-table-column>
      <el-table-column label="风险规则描述" prop="ruleDescSnap" min-width="240" show-overflow-tooltip><template #default="{row}">{{row.ruleDescSnap||'-'}}</template></el-table-column>
      <el-table-column label="风险等级" prop="riskLevelSnap" width="90" align="center"/>
      <el-table-column label="风险状态" prop="riskStatus" width="100" align="center"><template #default="{row}">{{row.riskStatus||'-'}}</template></el-table-column>
      <el-table-column label="设备ID" prop="riskDeviceId" width="130" align="center"><template #default="{row}">{{row.riskDeviceId||'-'}}</template></el-table-column>
      <el-table-column label="风险发生最后时间" width="180" align="center"><template #default="{row}">{{parseTime(row.riskHitTime)||'-'}}</template></el-table-column>
      <el-table-column label="处理描述" prop="riskHandleDesc" min-width="180" show-overflow-tooltip><template #default="{row}">{{row.riskHandleDesc||'-'}}</template></el-table-column>
      <el-table-column label="入库时间" width="170" align="center"><template #default="{row}">{{parseTime(row.createTime)||'-'}}</template></el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
  </div>
</template>
<script setup name="RiskControlLog">
import { exportRiskControlLogs, listRiskControlLogs } from '@/api/para/risk'
import { defaultTodayRange, isEmptyRange } from '@/utils/dateRange'
import { exportToExcel } from '@/utils/excelExport'

/** 必须与模板里查询区间的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

/** 后端导出单次上限，与 RiskManagementServiceImpl.EXPORT_LIMIT 对齐。 */
const EXPORT_MAX_ROWS = 20000

const { proxy } = getCurrentInstance()
// 风险命中记录为审计数据，只提供查询和分页浏览。
const loading=ref(false),rows=ref([]),total=ref(0),exporting=ref(false),queryError=ref('')
const query=reactive({cardId:'',userOrderNo:'',ruleId:'',riskStatus:'',dateRange:defaultTodayRange(DATE_FORMAT),pageNum:1,pageSize:10})

/** 把页面上的区间/筛选字段摊平成接口参数；导出与列表查询共用同一套筛选口径。 */
function toParams(overrides) {
  const [riskHitTimeBegin, riskHitTimeEnd] = query.dateRange || []
  return {
    cardId: query.cardId,
    userOrderNo: query.userOrderNo,
    ruleId: query.ruleId,
    riskStatus: query.riskStatus,
    riskHitTimeBegin,
    riskHitTimeEnd,
    ...overrides
  }
}

function load(){
  loading.value=true
  queryError.value=''
  listRiskControlLogs(toParams({pageNum:query.pageNum,pageSize:query.pageSize}))
    .then(r=>{rows.value=r.data?.list||[];total.value=Number(r.data?.total||0)})
    .catch(e=>{queryError.value=e?.message||'查询失败'})
    .finally(()=>{loading.value=false})
}
function search(){query.pageNum=1;load()}
function resetQuery(){Object.assign(query,{cardId:'',userOrderNo:'',ruleId:'',riskStatus:'',dateRange:defaultTodayRange(DATE_FORMAT)});search()}

const EXPORT_HEADERS = [
  { key: 'cardId', header: '逻辑卡号' },
  { key: 'userOrderNo', header: '用户订单' },
  { key: 'ruleId', header: '风险规则编号' },
  { key: 'ruleNameSnap', header: '风险规则名称' },
  { key: 'ruleDescSnap', header: '风险规则描述' },
  { key: 'riskLevelSnap', header: '风险等级' },
  { key: 'riskStatus', header: '风险状态' },
  { key: 'riskDeviceId', header: '设备ID' },
  { key: 'riskHandleDesc', header: '处理描述' },
  { key: 'riskHitTime', header: '风险发生最后时间' },
  { key: 'createTime', header: '入库时间' }
]

/**
 * 走后端导出端点一次性拉取（para 侧已实现 /control-logs/export，上限 20000 行）。
 * 超过上限时后端会截断，前端提示收窄范围。
 */
async function handleExport() {
  if (isEmptyRange(query.dateRange) && !query.cardId && !query.userOrderNo && !query.ruleId && !query.riskStatus) {
    proxy.$modal.msgWarning('请先设置命中时间范围或至少一个筛选条件，避免导出全表')
    return
  }
  exporting.value = true
  try {
    const first = await listRiskControlLogs(toParams({ pageNum: 1, pageSize: 1 }))
    const count = Number(first.data?.total || 0)
    if (count > EXPORT_MAX_ROWS) {
      proxy.$modal.msgWarning(`当前筛选命中 ${count} 条，超过单次导出上限 ${EXPORT_MAX_ROWS} 条，请收窄时间范围`)
      return
    }
    const res = await exportRiskControlLogs(toParams())
    const all = res.data || []
    if (!all.length) {
      proxy.$modal.msgWarning('当前筛选没有可导出的记录')
      return
    }
    const data = all.map((row) => ({
      ...row,
      riskHitTime: proxy.parseTime(row.riskHitTime) || '',
      createTime: proxy.parseTime(row.createTime) || '',
      riskHandleTime: proxy.parseTime(row.riskHandleTime) || ''
    }))
    exportToExcel(data, EXPORT_HEADERS, `风控记录_${new Date().toISOString().slice(0, 10)}`)
    proxy.$modal.msgSuccess(`已导出 ${data.length} 条风控记录`)
  } catch (e) {
    proxy.$modal.msgError(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>
