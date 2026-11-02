<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
         <el-form-item label="任务编码" prop="taskCode">
            <el-input
               v-model="queryParams.taskCode"
               placeholder="请输入任务编码"
               clearable
               style="width: 200px;"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="任务名称" prop="taskName">
            <el-input
               v-model="queryParams.taskName"
               placeholder="请输入任务名称"
               clearable
               style="width: 200px;"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select
               v-model="queryParams.status"
               placeholder="任务状态"
               clearable
               style="width: 160px"
            >
               <el-option label="待执行" value="PENDING" />
               <el-option label="执行中" value="RUNNING" />
               <el-option label="成功" value="SUCCESS" />
               <el-option label="失败" value="FAILED" />
            </el-select>
         </el-form-item>
         <el-form-item label="创建时间" style="width: 320px">
            <el-date-picker
               v-model="dateRange"
               value-format="YYYY-MM-DD HH:mm:ss"
               type="daterange"
               range-separator="-"
               start-placeholder="开始日期"
               end-placeholder="结束日期"
               :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
            ></el-date-picker>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table ref="tableRef" v-loading="loading" :data="taskList">
         <el-table-column label="任务ID" align="center" prop="id" width="80" />
         <el-table-column label="任务编码" align="center" prop="taskCode" width="150" :show-overflow-tooltip="true" />
         <el-table-column label="任务名称" align="center" prop="taskName" :show-overflow-tooltip="true" />
         <el-table-column label="状态" align="center" prop="status" width="100">
            <template #default="scope">
               <el-tag v-if="scope.row.status === 'PENDING'" type="info">待执行</el-tag>
               <el-tag v-else-if="scope.row.status === 'RUNNING'" type="warning">执行中</el-tag>
               <el-tag v-else-if="scope.row.status === 'SUCCESS'" type="success">成功</el-tag>
               <el-tag v-else-if="scope.row.status === 'FAILED'" type="danger">失败</el-tag>
               <el-tag v-else>{{ scope.row.status }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="导出行数" align="center" prop="rowCount" width="100" />
         <el-table-column label="文件大小" align="center" prop="fileSize" width="110">
            <template #default="scope">
               <span>{{ formatFileSize(scope.row.fileSize) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="执行开始" align="center" prop="executeTime" width="170">
            <template #default="scope">
               <span>{{ parseTime(scope.row.executeTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="完成时间" align="center" prop="finishTime" width="170">
            <template #default="scope">
               <span>{{ parseTime(scope.row.finishTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="失败原因" align="center" prop="errorMsg" :show-overflow-tooltip="true" />
         <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="120">
            <template #default="scope">
               <el-button
                  link
                  type="primary"
                  icon="Download"
                  @click="handleDownload(scope.row)"
                  :disabled="scope.row.status !== 'SUCCESS' || !scope.row.filePath"
                  v-hasPermi="['monitor:async-export:download']"
               >下载</el-button>
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
   </div>
</template>

<script setup name="AsyncExportTask">
import { listAsyncExportTask, downloadAsyncExportFile } from "@/api/monitor/asyncExport"
import { parseTime } from '@/utils/points'
import { defaultTodayRange } from '@/utils/dateRange'
import { saveAs } from 'file-saver'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

const { proxy } = getCurrentInstance()

const taskList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dateRange = ref(defaultTodayRange(DATE_FORMAT))

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    taskCode: undefined,
    taskName: undefined,
    status: undefined
  }
})

const { queryParams } = toRefs(data)

/** 查询任务列表 */
function getList() {
  loading.value = true
  listAsyncExportTask(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    taskList.value = response.rows
    total.value = response.total
    loading.value = false
  })
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
  queryParams.value.pageNum = 1
  handleQuery()
}

/** 格式化文件大小 */
function formatFileSize(size) {
  if (size == null || size === '') {
    return '-'
  }
  const num = Number(size)
  if (num < 1024) {
    return num + ' B'
  } else if (num < 1024 * 1024) {
    return (num / 1024).toFixed(2) + ' KB'
  } else if (num < 1024 * 1024 * 1024) {
    return (num / (1024 * 1024)).toFixed(2) + ' MB'
  }
  return (num / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

/** 下载文件 */
function handleDownload(row) {
  if (row.status !== 'SUCCESS' || !row.filePath) {
    proxy.$modal.msgWarning("任务未完成或文件不存在")
    return
  }
  proxy.$modal.loading("正在下载文件，请稍候...")
  downloadAsyncExportFile(row.id).then(response => {
    const fileName = row.filePath.substring(row.filePath.lastIndexOf('/') + 1)
    const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    saveAs(blob, fileName)
    proxy.$modal.closeLoading()
    proxy.$modal.msgSuccess("下载成功")
  }).catch(error => {
    proxy.$modal.closeLoading()
    proxy.$modal.msgError("下载失败，请检查文件是否已清理")
    console.error(error)
  })
}

getList()
</script>
