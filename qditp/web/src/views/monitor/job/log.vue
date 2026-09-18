<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
         <el-form-item label="任务名称" prop="jobName">
            <el-input
               v-model="queryParams.jobName"
               placeholder="请输入任务名称"
               clearable
               style="width: 240px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="任务组名" prop="jobGroup">
            <el-select
               v-model="queryParams.jobGroup"
               placeholder="请选择任务组名"
               clearable
               style="width: 240px"
            >
               <el-option
                  v-for="dict in sys_job_group"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item label="执行状态" prop="status">
            <el-select
               v-model="queryParams.status"
               placeholder="请选择执行状态"
               clearable
               style="width: 240px"
            >
               <el-option
                  v-for="dict in sys_common_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item label="执行时间" style="width: 308px">
            <el-date-picker
               v-model="dateRange"
               value-format="YYYY-MM-DD"
               type="daterange"
               range-separator="-"
               start-placeholder="开始日期"
               end-placeholder="结束日期"
            ></el-date-picker>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button
               type="danger"
               plain
               icon="Delete"
               :disabled="multiple"
               @click="handleDelete"
               v-hasPermi="['monitor:job:remove']"
            >删除</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="danger"
               plain
               icon="Delete"
               @click="handleClean"
               v-hasPermi="['monitor:job:remove']"
            >清空</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="warning"
               plain
               icon="Download"
               @click="handleExport"
               v-hasPermi="['monitor:job:export']"
            >导出</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button 
               type="warning" 
               plain 
               icon="Close"
               @click="handleClose"
            >关闭</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="jobLogList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="日志编号" width="80" align="center" prop="jobLogId" />
         <el-table-column label="任务名称" align="center" prop="jobName" :show-overflow-tooltip="true" />
         <el-table-column label="任务组名" align="center" prop="jobGroup" :show-overflow-tooltip="true">
            <template #default="scope">
               <dict-tag :options="sys_job_group" :value="scope.row.jobGroup" />
            </template>
         </el-table-column>
         <el-table-column label="调用目标字符串" align="center" prop="invokeTarget" :show-overflow-tooltip="true" />
         <el-table-column label="日志信息" align="center" prop="jobMessage" :show-overflow-tooltip="true" />
         <el-table-column label="执行状态" align="center" prop="status">
            <template #default="scope">
               <dict-tag :options="sys_common_status" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column label="执行时间" align="center" prop="createTime" width="180">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="180" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['monitor:job:query']">详细</el-button>
               <el-button
                  v-if="hasTraceId(scope.row)"
                  link
                  type="primary"
                  icon="Document"
                  @click="handleTraceLog(scope.row)"
                  v-hasPermi="['monitor:job:query']"
               >执行日志</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination
         v-show="total > 0"
         :total="total"
         v-model:page="queryParams.pageNum"
         v-model:limit="queryParams.pageSize"
         @pagination="getList"
      />

      <!-- 调度日志详细 -->
      <el-dialog title="调度日志详细" v-model="open" width="700px" append-to-body>
         <el-form :model="form" label-width="100px">
            <el-row>
               <el-col :span="12">
                  <el-form-item label="日志序号：">{{ form.jobLogId }}</el-form-item>
                  <el-form-item label="任务名称：">{{ form.jobName }}</el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="任务分组：">{{ form.jobGroup }}</el-form-item>
                  <el-form-item label="执行时间：">{{ form.createTime }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="调用方法：">{{ form.invokeTarget }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="日志信息：">{{ form.jobMessage }}</el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="执行状态：">
                     <div v-if="form.status == 0">正常</div>
                     <div v-else-if="form.status == 1">失败</div>
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="异常信息：" v-if="form.status == 1">{{ form.exceptionInfo }}</el-form-item>
               </el-col>
            </el-row>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="open = false">关 闭</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 执行日志（按 traceId 从日志系统反查全链路） -->
      <el-dialog title="执行日志" v-model="traceOpen" width="1100px" append-to-body>
         <div class="mb8">
            <span>traceId：{{ traceInfo.traceId }}</span>
            <span style="margin-left: 20px">检索区间：{{ formatLogTime(traceInfo.startTime) }} ~ {{ formatLogTime(traceInfo.endTime) }}</span>
         </div>
         <el-alert
            v-if="traceInfo.truncated"
            type="warning"
            :closable="false"
            title="日志条数已达单次返回上限，仅展示部分内容，完整链路请到日志系统按该 traceId 检索"
            class="mb8"
         />
         <el-table v-loading="traceLoading" :data="traceInfo.lines" max-height="480" empty-text="该 traceId 在日志系统中没有记录">
            <el-table-column label="时间" width="200">
               <template #default="scope">
                  <span>{{ formatLogTime(scope.row._time) }}</span>
               </template>
            </el-table-column>
            <el-table-column label="级别" prop="level" width="80" align="center" />
            <el-table-column label="服务" prop="app" width="140" :show-overflow-tooltip="true" />
            <el-table-column label="线程" prop="thread" width="160" :show-overflow-tooltip="true" />
            <el-table-column label="日志内容" prop="_msg" :show-overflow-tooltip="true" />
         </el-table>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="traceOpen = false">关 闭</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="JobLog">
import { getJob } from "@/api/monitor/job"
import { listJobLog, delJobLog, cleanJobLog, getJobTraceLog } from "@/api/monitor/jobLog"
import { defaultTodayRange } from '@/utils/dateRange'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD'

const { proxy } = getCurrentInstance()
const { sys_common_status, sys_job_group } = proxy.useDict("sys_common_status", "sys_job_group")

const jobLogList = ref([])
const open = ref(false)
const traceOpen = ref(false)
const traceLoading = ref(false)
const traceInfo = ref({ traceId: "", startTime: "", endTime: "", truncated: false, lines: [] })
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const dateRange = ref(defaultTodayRange(DATE_FORMAT))
const route = useRoute()

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    dictName: undefined,
    dictType: undefined,
    status: undefined
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询调度日志列表 */
function getList() {
  loading.value = true
  listJobLog(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    jobLogList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 返回按钮
function handleClose() {
  const obj = { path: "/monitor/job" }
  proxy.$tab.closeOpenPage(obj)
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = defaultTodayRange(DATE_FORMAT)
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.jobLogId)
  multiple.value = !selection.length
}

/** 详细按钮操作 */
function handleView(row) {
  open.value = true
  form.value = row
}

/** 是否记录了 traceId：AbstractQuartzJob 把它追加在 job_message 末尾，没有则无从检索 */
function hasTraceId(row) {
  return /traceId=[0-9a-fA-F]{32}/.test(row.jobMessage || "")
}

/**
 * UTC 时间串转本地「YYYY-MM-DD HH:mm:ss.SSS」。
 *
 * 后端返回的检索区间与 VictoriaLogs 的 _time 都带 Z 后缀（event template 固定 timeZone=UTC），
 * 直接展示会比本地时间早 8 小时。
 * 这里不能用全局 parseTime：它会把 ISO 串里的 - 换成 /、并整段删掉毫秒，
 * 遇到 Z 后缀解析结果不可靠，而日志排序恰恰要看毫秒。
 */
function formatLogTime(value) {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num, len = 2) => String(num).padStart(len, "0")
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    + `.${pad(date.getMilliseconds(), 3)}`
}

/** 执行日志按钮操作 */
function handleTraceLog(row) {
  traceInfo.value = { traceId: "", startTime: "", endTime: "", truncated: false, lines: [] }
  traceOpen.value = true
  traceLoading.value = true
  getJobTraceLog(row.jobLogId).then(response => {
    traceInfo.value = response.data
  }).catch(() => {
    traceOpen.value = false
  }).finally(() => {
    traceLoading.value = false
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除调度日志编号为"' + ids.value + '"的数据项?').then(function () {
    return delJobLog(ids.value)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm("是否确认清空所有调度日志数据项?").then(function () {
    return cleanJobLog()
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("清空成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/jobLog/export", {
    ...queryParams.value,
  }, `job_log_${new Date().getTime()}.xlsx`)
}

(() => {
  const jobId = route.params && route.params.jobId
  if (jobId !== undefined && jobId != 0) {
    getJob(jobId).then(response => {
      queryParams.value.jobName = response.data.jobName
      queryParams.value.jobGroup = response.data.jobGroup
      getList()
    })
  } else {
    getList()
  }
})()
</script>
