<template>
  <div class="app-container">
    <!-- 查询区域 -->
    <el-form :model="query" :inline="true">
      <el-form-item label="员工号">
        <el-input v-model="query.cardNo" clearable placeholder="请输入员工号" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="query.employeeName" clearable placeholder="请输入姓名" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.cardStatus" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">查询</el-button>
        <el-button icon="Refresh" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮 -->
    <el-row class="mb8">
      <el-button type="primary" icon="Plus" @click="openAdd">新增模拟卡</el-button>
      <el-button type="success" icon="Promotion" :disabled="selected.length === 0" :loading="sending"
                 @click="sendSelected">发送状态通知（{{ selected.length }}）</el-button>
      <el-button type="warning" icon="Edit" :disabled="selected.length !== 1" :loading="sendingUpdate"
                 @click="openUpdateNotify">发送资料变更</el-button>
      <el-button icon="Delete" type="danger" plain @click="clearCards">清空测试数据</el-button>
    </el-row>

    <!-- 员工卡列表 -->
    <el-table v-loading="loading" :data="cards" border @selection-change="selected = $event">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="序号" width="70" align="center">
        <template #default="{ $index }">{{ (query.pageNum - 1) * query.pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column label="照片" width="80" align="center">
        <template #default="{ row }">
          <el-button v-if="row.photo" link type="primary" icon="View" @click="viewPhoto(row)">查看</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="cardNo" label="员工号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="employeeName" label="姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.cardStatus)">{{ statusLabel(row.cardStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="company" label="公司" min-width="160" show-overflow-tooltip />
      <el-table-column prop="center" label="中心" min-width="120" show-overflow-tooltip />
      <el-table-column prop="department" label="部门" min-width="140" show-overflow-tooltip />
      <el-table-column prop="position" label="岗位" width="120" show-overflow-tooltip />
      <el-table-column prop="updateTms" label="更新时间" width="180" />
      <el-table-column label="操作" width="90" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="Edit" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total"
                @pagination="loadCards" />

    <!-- 调用历史 -->
    <el-divider content-position="left">调用记录</el-divider>
    <el-row class="mb8">
      <el-button icon="Delete" type="danger" plain size="small" @click="clearHistory">清空调用记录</el-button>
    </el-row>
    <el-table v-loading="historyLoading" :data="history" border>
      <el-table-column prop="operation" label="操作" width="130" />
      <el-table-column prop="targetUrl" label="目标地址" min-width="260" show-overflow-tooltip />
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.transportSuccess ? 'success' : 'danger'">{{ row.transportSuccess ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="httpStatus" label="HTTP" width="70" align="center" />
      <el-table-column prop="elapsedMs" label="耗时(ms)" width="90" align="center" />
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column label="操作" width="100" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="View" @click="viewHistory(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="historyTotal > 0" v-model:page="historyQuery.pageNum" v-model:limit="historyQuery.pageSize"
                :total="historyTotal" @pagination="loadHistory" />

    <!-- 新增/编辑员工卡对话框 -->
    <el-dialog v-model="dialog.open" :title="dialog.edit ? '编辑模拟员工卡' : '新增模拟员工卡'" width="640px" append-to-body
               destroy-on-close>
      <el-form ref="formRef" :model="dialog.form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="员工号" prop="cardNo">
              <el-input v-model="dialog.form.cardNo" maxlength="32" :disabled="dialog.edit" placeholder="实体员工卡卡号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="dialog.form.phone" maxlength="11" placeholder="11位手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="姓名">
              <el-input v-model="dialog.form.employeeName" maxlength="256" placeholder="员工姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="cardStatus">
              <el-select v-model="dialog.form.cardStatus" style="width: 100%">
                <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="身份证号">
          <el-input v-model="dialog.form.idCardNo" maxlength="18" placeholder="18位身份证号" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="公司">
              <el-input v-model="dialog.form.company" maxlength="256" placeholder="所属公司" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="中心">
              <el-input v-model="dialog.form.center" maxlength="256" placeholder="所属中心" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门">
              <el-input v-model="dialog.form.department" maxlength="256" placeholder="所属部门" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-input v-model="dialog.form.position" maxlength="256" placeholder="岗位" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="照片">
          <el-upload
            class="photo-uploader"
            :show-file-list="false"
            accept="image/png,image/jpeg"
            :http-request="handlePhotoUpload"
            :before-upload="beforePhotoUpload"
          >
            <img v-if="dialog.form.photo" :src="'data:image/jpeg;base64,' + dialog.form.photo" class="photo-preview" />
            <el-icon v-else class="photo-uploader-icon"><Plus /></el-icon>
          </el-upload>
          <div v-if="dialog.form.photo" class="photo-tip">
            <el-button link type="danger" size="small" @click="dialog.form.photo = ''">移除照片</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.open = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 资料变更通知对话框 -->
    <el-dialog v-model="updateDialog.open" title="发送资料变更通知" width="560px" append-to-body destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        向 ITP 发送选中员工的资料变更通知（公司/中心/部门/岗位/照片），仅支持单条发送。
      </el-alert>
      <el-form :model="updateDialog.form" label-width="100px">
        <el-form-item label="员工号">
          <el-input v-model="updateDialog.form.cardNo" disabled />
        </el-form-item>
        <el-form-item label="公司">
          <el-input v-model="updateDialog.form.company" maxlength="256" />
        </el-form-item>
        <el-form-item label="中心">
          <el-input v-model="updateDialog.form.center" maxlength="256" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="updateDialog.form.department" maxlength="256" />
        </el-form-item>
        <el-form-item label="岗位">
          <el-input v-model="updateDialog.form.position" maxlength="256" />
        </el-form-item>
        <el-form-item label="照片">
          <el-upload
            class="photo-uploader"
            :show-file-list="false"
            accept="image/png,image/jpeg"
            :http-request="handleUpdatePhotoUpload"
            :before-upload="beforePhotoUpload"
          >
            <img v-if="updateDialog.form.photo" :src="'data:image/jpeg;base64,' + updateDialog.form.photo" class="photo-preview" />
            <el-icon v-else class="photo-uploader-icon"><Plus /></el-icon>
          </el-upload>
          <div v-if="updateDialog.form.photo" class="photo-tip">
            <el-button link type="danger" size="small" @click="updateDialog.form.photo = ''">移除照片</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="updateDialog.open = false">取消</el-button>
        <el-button type="warning" :loading="updateDialog.sending" @click="sendUpdateNotify">发送变更通知</el-button>
      </template>
    </el-dialog>

    <!-- 照片查看对话框 -->
    <el-dialog v-model="photoViewer.open" :title="photoViewer.title" width="340px" append-to-body destroy-on-close>
      <div class="photo-viewer">
        <img :src="photoViewer.src" class="photo-viewer-img" alt="员工照片" />
      </div>
    </el-dialog>

    <!-- 调用历史详情对话框 -->
    <el-dialog v-model="historyDetail.open" title="调用详情" width="720px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作">{{ historyDetail.data?.operation }}</el-descriptions-item>
        <el-descriptions-item label="目标地址">{{ historyDetail.data?.targetUrl }}</el-descriptions-item>
        <el-descriptions-item label="传输结果">
          <el-tag :type="historyDetail.data?.transportSuccess ? 'success' : 'danger'">
            {{ historyDetail.data?.transportSuccess ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="HTTP 状态码">{{ historyDetail.data?.httpStatus ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ historyDetail.data?.elapsedMs }} ms</el-descriptions-item>
        <el-descriptions-item label="时间">{{ historyDetail.data?.createdAt }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="historyDetail.data?.errorMessage">
        <el-divider content-position="left">错误信息</el-divider>
        <el-input :model-value="historyDetail.data.errorMessage" type="textarea" :rows="2" readonly />
      </template>
      <el-divider content-position="left">请求体</el-divider>
      <el-input :model-value="historyDetail.data?.requestBody" type="textarea" :rows="4" readonly />
      <el-divider content-position="left">响应体</el-divider>
      <el-input :model-value="historyDetail.data?.responseBody || '（无）'" type="textarea" :rows="4" readonly />
    </el-dialog>
  </div>
</template>

<script setup name="EmployeeCardAccSimulator">
import { reactive, ref, onMounted, getCurrentInstance } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import {
  clearAccSimulatorCards,
  clearAccSimulatorHistory,
  listAccSimulatorCards,
  listAccSimulatorHistory,
  saveAccSimulatorCard,
  sendEmployeeCardNotify,
  sendEmployeeCardUpdateNotify
} from '@/api/trans/employeeCardAccSimulator'

const { proxy } = getCurrentInstance()

/** 电子卡状态枚举，与 ACC 模拟库 CARD_STATUS 列一致 */
const statuses = [
  { value: 1, label: '1-启用' },
  { value: 2, label: '2-禁用' },
  { value: 3, label: '3-未启用' },
  { value: 4, label: '4-注销' }
]

// ---- 员工卡列表 ----
const loading = ref(false)
const sending = ref(false)
const sendingUpdate = ref(false)
const cards = ref([])
const selected = ref([])
const total = ref(0)
const query = reactive({ cardNo: '', employeeName: '', cardStatus: undefined, pageNum: 1, pageSize: 10 })

// ---- 调用历史 ----
const historyLoading = ref(false)
const history = ref([])
const historyTotal = ref(0)
const historyQuery = reactive({ pageNum: 1, pageSize: 10 })

// ---- 新增/编辑对话框 ----
const dialog = reactive({ open: false, edit: false, saving: false, form: {} })
const formRef = ref()
const rules = {
  cardNo: [{ required: true, message: '请输入员工号', trigger: 'blur' }],
  cardStatus: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

// ---- 资料变更通知对话框 ----
const updateDialog = reactive({ open: false, sending: false, form: {} })

// ---- 调用历史详情 ----
const historyDetail = reactive({ open: false, data: null })

// ---- 照片查看 ----
const photoViewer = reactive({ open: false, src: '', title: '' })

/** 照片大小上限 250KB，与 ACC 端限制一致 */
const PHOTO_MAX_SIZE = 250 * 1024

/**
 * 上传前校验：只允许 PNG/JPG，且大小不超过 250KB。
 *
 * @param {File} file 待上传文件
 * @returns {boolean} 校验通过返回 true
 */
function beforePhotoUpload(file) {
  const isImg = file.type === 'image/png' || file.type === 'image/jpeg'
  if (!isImg) {
    proxy.$modal.msgError('只能上传 PNG 或 JPG 格式的图片')
    return false
  }
  if (file.size > PHOTO_MAX_SIZE) {
    proxy.$modal.msgError('照片大小不能超过 250KB')
    return false
  }
  return true
}

/**
 * 将上传的图片文件转为 Base64 字符串（不含 data URI 前缀），写入表单。
 *
 * @param {Object} options el-upload 自定义上传参数，含 file
 */
function handlePhotoUpload(options) {
  fileToBase64(options.file).then(base64 => { dialog.form.photo = base64 })
}

/**
 * 将资料变更对话框中的图片文件转为 Base64，写入变更表单。
 *
 * @param {Object} options el-upload 自定义上传参数，含 file
 */
function handleUpdatePhotoUpload(options) {
  fileToBase64(options.file).then(base64 => { updateDialog.form.photo = base64 })
}

/**
 * 读取文件并转为纯 Base64 字符串（去掉 data:image/xxx;base64, 前缀）。
 *
 * @param {File} file 图片文件
 * @returns {Promise<string>} 纯 Base64 字符串
 */
function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result
      const base64 = result.includes(',') ? result.split(',')[1] : result
      resolve(base64)
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

/** 状态标签文本 */
function statusLabel(value) {
  return statuses.find(item => item.value === value)?.label || (value == null || value === '' ? '-' : String(value))
}

/** 状态标签颜色 */
function statusTagType(value) {
  if (value === 1) return 'success'
  if (value === 2 || value === 4) return 'danger'
  return 'warning'
}

/** 加载员工卡分页列表 */
function loadCards() {
  loading.value = true
  listAccSimulatorCards(query).then(res => {
    const page = res.data || {}
    cards.value = page.list || []
    total.value = Number(page.total || 0)
    selected.value = []
  }).finally(() => { loading.value = false })
}

/** 加载调用历史分页列表 */
function loadHistory() {
  historyLoading.value = true
  listAccSimulatorHistory(historyQuery).then(res => {
    const page = res.data || {}
    history.value = page.list || []
    historyTotal.value = Number(page.total || 0)
  }).finally(() => { historyLoading.value = false })
}

/** 查询，重置到第一页 */
function search() { query.pageNum = 1; loadCards() }

/** 重置查询条件 */
function reset() { query.cardNo = ''; query.employeeName = ''; query.cardStatus = undefined; search() }

/** 构建空白表单 */
function blankForm() {
  return { cardNo: '', phone: '', cardStatus: 3, employeeName: '', idCardNo: '', company: '', center: '', department: '', position: '', photo: '' }
}

/** 打开新增对话框 */
function openAdd() { dialog.edit = false; dialog.form = blankForm(); dialog.open = true }

/** 打开编辑对话框 */
function openEdit(row) { dialog.edit = true; dialog.form = { ...row }; dialog.open = true }

/** 保存员工卡 */
function save() {
  formRef.value.validate(valid => {
    if (!valid) return
    dialog.saving = true
    saveAccSimulatorCard(dialog.form).then(() => {
      proxy.$modal.msgSuccess('保存成功')
      dialog.open = false
      loadCards()
    }).finally(() => { dialog.saving = false })
  })
}

/** 发送选中员工的状态通知（ACC → ITP） */
function sendSelected() {
  sending.value = true
  const cardList = selected.value.map(row => ({
    cardNo: row.cardNo,
    phone: row.phone,
    cardStatus: row.cardStatus,
    employeeName: row.employeeName,
    idCardNo: row.idCardNo,
    company: row.company,
    center: row.center,
    department: row.department,
    position: row.position,
    photoUrl: row.photo
  }))
  sendEmployeeCardNotify({ cardList })
    .then(() => { proxy.$modal.msgSuccess('状态通知已发送'); loadHistory() })
    .finally(() => { sending.value = false })
}

/** 打开资料变更通知对话框 */
function openUpdateNotify() {
  const row = selected.value[0]
  updateDialog.form = {
    cardNo: row.cardNo,
    company: row.company || '',
    center: row.center || '',
    department: row.department || '',
    position: row.position || '',
    photo: row.photo || ''
  }
  updateDialog.open = true
}

/** 发送资料变更通知（ACC → ITP） */
function sendUpdateNotify() {
  updateDialog.sending = true
  const employee = {
    cardNo: updateDialog.form.cardNo,
    company: updateDialog.form.company,
    center: updateDialog.form.center,
    department: updateDialog.form.department,
    position: updateDialog.form.position,
    photo: updateDialog.form.photo
  }
  sendEmployeeCardUpdateNotify({ employee })
    .then(() => { proxy.$modal.msgSuccess('资料变更通知已发送'); updateDialog.open = false; loadHistory() })
    .finally(() => { updateDialog.sending = false })
}

/** 查看员工照片：将 base64 转成 data URI 弹窗展示 */
function viewPhoto(row) {
  photoViewer.src = 'data:image/jpeg;base64,' + row.photo
  photoViewer.title = (row.employeeName || row.cardNo) + ' 的照片'
  photoViewer.open = true
}

/** 查看调用历史详情 */
function viewHistory(row) {
  historyDetail.data = row
  historyDetail.open = true
}

/** 清空模拟员工卡 */
function clearCards() {
  proxy.$modal.confirm('确认清空模拟 ACC 员工码库？此操作不可恢复。').then(() => clearAccSimulatorCards())
    .then(() => { proxy.$modal.msgSuccess('已清空'); loadCards() })
    .catch(() => {})
}

/** 清空调用历史 */
function clearHistory() {
  proxy.$modal.confirm('确认清空所有调用记录？').then(() => clearAccSimulatorHistory())
    .then(() => { proxy.$modal.msgSuccess('已清空'); loadHistory() })
    .catch(() => {})
}

onMounted(() => { loadCards(); loadHistory() })
</script>

<style scoped>
/* 照片上传区域：100x120 证件照比例 */
.photo-uploader :deep(.el-upload) {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  width: 100px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: border-color 0.2s;
}

.photo-uploader :deep(.el-upload:hover) {
  border-color: var(--el-color-primary);
}

.photo-preview {
  width: 100px;
  height: 120px;
  object-fit: cover;
  display: block;
}

.photo-uploader-icon {
  font-size: 24px;
  color: var(--el-text-color-secondary);
}

.photo-tip {
  margin-top: 4px;
}

/* 照片查看弹窗：居中原图展示，限制最大高度 */
.photo-viewer {
  display: flex;
  justify-content: center;
}

.photo-viewer-img {
  max-width: 100%;
  max-height: 60vh;
  border-radius: 4px;
}
</style>
