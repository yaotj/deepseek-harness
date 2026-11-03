<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
         <el-form-item label="文件名称" prop="fileName">
            <el-input
               v-model="queryParams.fileName"
               placeholder="请输入文件名称"
               clearable
               style="width: 200px;"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="文件类型" prop="fileType">
            <el-select
               v-model="queryParams.fileType"
               placeholder="请选择文件类型"
               clearable
               style="width: 160px"
            >
               <el-option label="Excel" value="xlsx" />
               <el-option label="Excel 97-2003" value="xls" />
               <el-option label="CSV" value="csv" />
               <el-option label="文本" value="txt" />
            </el-select>
         </el-form-item>
         <el-form-item label="对账日期" prop="reconDate">
            <el-date-picker
               v-model="queryParams.reconDate"
               value-format="YYYYMMDD"
               type="date"
               placeholder="请选择对账日期"
               style="width: 160px"
            />
         </el-form-item>
         <el-form-item label="上传时间" style="width: 320px">
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
         <el-col :span="1.5">
            <el-upload
               ref="uploadRef"
               action="#"
               :http-request="customUpload"
               :show-file-list="false"
               :before-upload="beforeUpload"
               accept=".xlsx,.xls,.csv,.txt"
            >
               <el-button type="primary" icon="Upload" v-hasPermi="['recon:upload-file:upload']">上传对账文件</el-button>
            </el-upload>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <!-- 上传对账文件对话框 -->
      <el-dialog
         title="上传对账文件"
         v-model="uploadDialogVisible"
         width="500px"
         append-to-body
         destroy-on-close
         :close-on-click-modal="false"
      >
         <el-form :model="uploadForm" ref="uploadFormRef" label-width="100px">
            <el-form-item label="对账日期" prop="reconDate" required>
               <el-date-picker
                  v-model="uploadForm.reconDate"
                  value-format="YYYYMMDD"
                  type="date"
                  placeholder="请选择对账日期"
                  style="width: 100%"
               />
            </el-form-item>
            <el-form-item label="已选文件">
               <span>{{ pendingFile ? pendingFile.name : '' }}</span>
            </el-form-item>
            <el-form-item label="备注" prop="remark">
               <el-input
                  v-model="uploadForm.remark"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入备注"
               />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="uploadDialogVisible = false">取 消</el-button>
               <el-button type="primary" @click="handleUploadSubmit">确 定</el-button>
            </div>
         </template>
      </el-dialog>

      <el-table ref="tableRef" v-loading="loading" :data="fileList">
         <el-table-column label="文件ID" align="center" prop="id" width="80" />
         <el-table-column label="原始文件名" align="center" prop="fileName" :show-overflow-tooltip="true" />
         <el-table-column label="文件类型" align="center" prop="fileType" width="100" />
         <el-table-column label="文件大小" align="center" prop="fileSize" width="110">
            <template #default="scope">
               <span>{{ formatFileSize(scope.row.fileSize) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="对账日期" align="center" prop="reconDate" width="120" />
         <el-table-column label="状态" align="center" prop="status" width="100">
            <template #default="scope">
               <el-tag v-if="scope.row.status === 'UPLOADED'" type="success">已上传</el-tag>
               <el-tag v-else>{{ scope.row.status }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="上传人" align="center" prop="createBy" width="120" />
         <el-table-column label="上传时间" align="center" prop="createTime" width="170">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
         <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
            <template #default="scope">
               <el-button
                  link
                  type="primary"
                  icon="View"
                  @click="handlePreview(scope.row)"
                  v-hasPermi="['recon:upload-file:download']"
               >预览</el-button>
               <el-button
                  link
                  type="primary"
                  icon="Download"
                  @click="handleDownload(scope.row)"
                  v-hasPermi="['recon:upload-file:download']"
               >下载</el-button>
               <el-button
                  link
                  type="danger"
                  icon="Delete"
                  @click="handleDelete(scope.row)"
                  v-hasPermi="['recon:upload-file:remove']"
               >删除</el-button>
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

      <!-- 文件预览弹窗 -->
      <el-dialog
         v-model="previewVisible"
         :title="'预览：' + (previewRow.fileName || '')"
         width="80%"
         top="5vh"
         destroy-on-close
         :close-on-click-modal="false"
      >
         <div v-loading="previewLoading" class="preview-container">
            <!-- Excel 预览 -->
            <vue-office-excel
               v-if="isExcel(previewRow.fileType)"
               :src="previewSrc"
               style="height: 70vh;"
            />
            <!-- CSV 预览 -->
            <div v-else-if="previewRow.fileType === 'csv'" class="text-preview">
               <el-table :data="csvRows" border stripe height="70vh">
                  <el-table-column
                     v-for="(col, idx) in csvCols"
                     :key="idx"
                     :label="col"
                     :prop="'col' + idx"
                     min-width="120"
                     show-overflow-tooltip
                  />
               </el-table>
            </div>
            <!-- 文本预览 -->
            <pre v-else-if="previewRow.fileType === 'txt'" class="text-preview">{{ previewText }}</pre>
            <el-empty v-else description="暂不支持的预览格式" />
         </div>
      </el-dialog>
   </div>
</template>

<script setup name="ReconUploadFile">
import { listReconUploadFile, uploadReconFile, downloadReconFile, delReconUploadFile, previewReconFile } from "@/api/recon/uploadFile"
import { parseTime } from '@/utils/points'
import { defaultTodayRange } from '@/utils/dateRange'
import { saveAs } from 'file-saver'
import VueOfficeExcel from '@vue-office/excel'
import '@vue-office/excel/lib/index.css'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

const { proxy } = getCurrentInstance()

const fileList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dateRange = ref(defaultTodayRange(DATE_FORMAT))

// 上传对话框
const uploadDialogVisible = ref(false)
const pendingFile = ref(null)
const uploadFormRef = ref(null)
const uploadForm = reactive({
  reconDate: undefined,
  remark: undefined
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    fileName: undefined,
    fileType: undefined,
    reconDate: undefined
  }
})

const { queryParams } = toRefs(data)

// 预览相关状态
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewRow = ref({})
const previewSrc = ref(null)
const previewText = ref('')
const csvRows = ref([])
const csvCols = ref([])

/** 查询对账文件列表 */
function getList() {
  loading.value = true
  listReconUploadFile(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    fileList.value = response.rows
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

/** 上传前校验 */
function beforeUpload(file) {
   const validTypes = ['xlsx', 'xls', 'csv', 'txt']
   const extension = file.name.substring(file.name.lastIndexOf('.') + 1).toLowerCase()
   if (!validTypes.includes(extension)) {
      proxy.$modal.msgError('仅支持上传 xlsx、xls、csv、txt 格式的文件')
      return false
   }
   if (file.size > 50 * 1024 * 1024) {
      proxy.$modal.msgError('文件大小不能超过 50MB')
      return false
   }
   // 暂存文件并弹出对账日期/备注录入框
   pendingFile.value = file
   uploadForm.reconDate = undefined
   uploadForm.remark = undefined
   uploadDialogVisible.value = true
   return false
}

/** 自定义上传（el-upload 的 http-request，实际在上传对话框确认后触发） */
function customUpload(options) {
   const formData = new FormData()
   formData.append('file', options.file)
   if (options.data) {
      formData.append('reconDate', options.data.reconDate)
      if (options.data.remark) {
         formData.append('remark', options.data.remark)
      }
   }
   proxy.$modal.loading("正在上传对账文件，请稍候...")
   uploadReconFile(formData).then(response => {
      proxy.$modal.closeLoading()
      proxy.$modal.msgSuccess("上传成功")
      uploadDialogVisible.value = false
      getList()
   }).catch(error => {
      proxy.$modal.closeLoading()
      proxy.$modal.msgError("上传失败")
      console.error(error)
   })
}

/** 上传对话框确认 */
function handleUploadSubmit() {
   if (!pendingFile.value) {
      proxy.$modal.msgError('请选择文件')
      return
   }
   if (!uploadForm.reconDate) {
      proxy.$modal.msgError('请选择对账日期')
      return
   }
   customUpload({
      file: pendingFile.value,
      data: { reconDate: uploadForm.reconDate, remark: uploadForm.remark }
   })
}

/** 判断是否 Excel */
function isExcel(fileType) {
  return fileType === 'xlsx' || fileType === 'xls'
}

/** 预览文件 */
function handlePreview(row) {
  if (!row.filePath) {
    proxy.$modal.msgWarning("文件路径为空，无法预览")
    return
  }
  if (row.fileSize > 10 * 1024 * 1024) {
    proxy.$modal.msgWarning("文件超过 10MB，建议下载后查看")
    return
  }

  previewRow.value = row
  previewVisible.value = true
  previewLoading.value = true
  previewSrc.value = null
  previewText.value = ''
  csvRows.value = []
  csvCols.value = []

  previewReconFile(row.id).then(response => {
    const fileType = row.fileType
    if (isExcel(fileType)) {
      previewSrc.value = response
    } else if (fileType === 'csv' || fileType === 'txt') {
      const decoder = new TextDecoder('utf-8')
      const text = decoder.decode(response)
      if (fileType === 'csv') {
        parseCsv(text)
      } else {
        previewText.value = text
      }
    }
    previewLoading.value = false
  }).catch(error => {
    previewLoading.value = false
    proxy.$modal.msgError("预览失败")
    console.error(error)
  })
}

/** 简单解析 CSV */
function parseCsv(text) {
  const lines = text.split(/\r?\n/).filter(line => line.trim() !== '')
  if (lines.length === 0) {
    csvCols.value = []
    csvRows.value = []
    return
  }
  // 按逗号拆分，暂不支持引号内逗号
  const headers = lines[0].split(',').map(h => h.trim())
  csvCols.value = headers
  csvRows.value = lines.slice(1).map(line => {
    const cells = line.split(',').map(c => c.trim())
    const row = {}
    headers.forEach((h, idx) => {
      row['col' + idx] = cells[idx] || ''
    })
    return row
  })
}

/** 下载文件 */
function handleDownload(row) {
  if (!row.filePath) {
    proxy.$modal.msgWarning("文件路径为空，无法下载")
    return
  }
  proxy.$modal.loading("正在下载文件，请稍候...")
  downloadReconFile(row.id).then(response => {
    const blob = new Blob([response])
    saveAs(blob, row.fileName)
    proxy.$modal.closeLoading()
    proxy.$modal.msgSuccess("下载成功")
  }).catch(error => {
    proxy.$modal.closeLoading()
    proxy.$modal.msgError("下载失败")
    console.error(error)
  })
}

/** 删除文件记录 */
function handleDelete(row) {
  proxy.$modal.confirm('确认删除文件「' + row.fileName + '」吗？').then(() => {
    return delReconUploadFile(row.id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.preview-container {
  min-height: 200px;
}
.text-preview {
  height: 70vh;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background-color: #f5f7fa;
}
</style>
