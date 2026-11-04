<template>
  <div class="app-container">
    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="灰名单为观察/预警名单：不参与乘车拦截、不渠道同步；与黑名单（拦截+两阶段解除）是两套独立机制。" />
    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="卡号" prop="cardId">
        <el-input v-model="queryParams.cardId" placeholder="请输入逻辑卡号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="用户ID" prop="thirdUserId">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入第三方用户ID" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="进灰时间" prop="dateRange">
        <query-time-range v-model="queryParams.dateRange" :value-format="DATE_FORMAT" :allow-all="true" @change="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button v-hasPermi="['trans:graylist:add']" type="primary" plain icon="Plus" @click="handleAdd">进灰</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border>
      <template #empty>
        <empty-state
          :error="queryError"
          :filters="queryParams"
          :range="queryParams.dateRange"
          :value-format="DATE_FORMAT"
          @update:range="queryParams.dateRange = $event"
          @clear="resetQuery"
          @retry="getList"
        />
      </template>
      <el-table-column label="卡号" prop="cardId" min-width="180" show-overflow-tooltip />
      <el-table-column label="用户ID" prop="thirdUserId" min-width="150" show-overflow-tooltip />
      <el-table-column label="卡类型" prop="cardType" width="90" align="center" />
      <el-table-column label="发起方" width="90" align="center"><template #default="{ row }">{{ formatSource(row.graySource) }}</template></el-table-column>
      <el-table-column label="业务单号" prop="bizNo" min-width="150" show-overflow-tooltip />
      <el-table-column label="进灰原因" prop="reason" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作者" prop="createBy" width="110" align="center" />
      <el-table-column label="进灰时间" prop="createTime" width="170" align="center" />
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['trans:graylist:remove']" link type="danger" icon="Remove" @click="handleRemove(row)">移出</el-button>
          <el-button link type="primary" icon="Document" @click="handleLogs(row)">流水</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="addOpen" title="进灰" width="500px" append-to-body destroy-on-close>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="90px">
        <el-form-item label="卡号" prop="cardId">
          <el-input v-model="addForm.cardId" placeholder="请输入逻辑卡号" />
        </el-form-item>
        <el-form-item label="用户ID" prop="thirdUserId">
          <el-input v-model="addForm.thirdUserId" placeholder="请输入第三方用户ID（可空）" />
        </el-form-item>
        <el-form-item label="卡类型" prop="cardType">
          <el-input v-model="addForm.cardType" placeholder="如 0441（可空）" />
        </el-form-item>
        <el-form-item label="业务单号" prop="bizNo">
          <el-input v-model="addForm.bizNo" placeholder="关联业务单号（可空）" />
        </el-form-item>
        <el-form-item label="进灰原因" prop="reason">
          <el-input v-model="addForm.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="请输入进灰原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addOpen = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="submitAdd">确认进灰</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="removeOpen" title="移出灰名单" width="460px" append-to-body destroy-on-close>
      <el-form ref="removeFormRef" :model="removeForm" :rules="removeRules" label-width="90px">
        <el-form-item label="卡号"><el-input :model-value="removeForm.cardId" disabled /></el-form-item>
        <el-form-item label="移出原因" prop="reason">
          <el-input v-model="removeForm.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="请输入移出原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="removeOpen = false">取消</el-button>
        <el-button type="danger" :loading="removing" @click="submitRemove">确认移出</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logOpen" title="操作流水" width="640px" append-to-body destroy-on-close>
      <el-table v-loading="logLoading" :data="logRows" border max-height="360">
        <el-table-column label="操作" width="90" align="center"><template #default="{ row }">{{ row.operateType === 'ADD' ? '进灰' : '移出' }}</template></el-table-column>
        <el-table-column label="卡号" prop="cardId" min-width="160" show-overflow-tooltip />
        <el-table-column label="原因" prop="reason" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作者" prop="operator" width="110" align="center" />
        <el-table-column label="时间" prop="operateTime" width="170" align="center" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup name="GraylistManagement">
import { listGraylists, addGraylist, removeGraylist, listGraylistOperateLogs } from '@/api/trans/graylist'
import { defaultTodayRange } from '@/utils/dateRange'
import useUserStore from '@/store/modules/user'

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'

const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const loading = ref(false)
const total = ref(0)
const rows = ref([])
const queryError = ref('')
const queryParams = reactive({
  cardId: '', thirdUserId: '', dateRange: defaultTodayRange(DATE_FORMAT), pageNum: 1, pageSize: 10
})

const addOpen = ref(false)
const adding = ref(false)
const addForm = reactive({ cardId: '', thirdUserId: '', cardType: '', bizNo: '', reason: '' })
const addRules = {
  cardId: [{ required: true, message: '请输入逻辑卡号', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入进灰原因', trigger: 'blur' }]
}

const removeOpen = ref(false)
const removing = ref(false)
const removeForm = reactive({ cardId: '', reason: '' })
const removeRules = { reason: [{ required: true, message: '请输入移出原因', trigger: 'blur' }] }

const logOpen = ref(false)
const logLoading = ref(false)
const logRows = ref([])

function getList() {
  loading.value = true
  queryError.value = ''
  const [createTimeBegin, createTimeEnd] = queryParams.dateRange || []
  listGraylists({
    cardId: queryParams.cardId,
    thirdUserId: queryParams.thirdUserId,
    createTimeBegin: createTimeBegin || undefined,
    createTimeEnd: createTimeEnd || undefined,
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize
  }).then((response) => {
    const page = response.data || {}
    rows.value = page.list || []
    total.value = Number(page.total || 0)
  }).catch((error) => {
    queryError.value = error?.message || '查询失败'
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.dateRange = defaultTodayRange(DATE_FORMAT)
  rows.value = []
  total.value = 0
}

function handleAdd() {
  addForm.cardId = queryParams.cardId
  addForm.thirdUserId = ''
  addForm.cardType = ''
  addForm.bizNo = ''
  addForm.reason = ''
  addOpen.value = true
}

function submitAdd() {
  proxy.$refs.addFormRef.validate((valid) => {
    if (!valid) return
    adding.value = true
    addGraylist({
      ...addForm,
      operator: userStore.name || ''
    }).then(() => {
      // 后端幂等：卡已在灰名单时同样返回成功，不覆盖原记录。
      proxy.$modal.msgSuccess('进灰成功')
      addOpen.value = false
      getList()
    }).finally(() => { adding.value = false })
  })
}

function handleRemove(row) {
  removeForm.cardId = row.cardId
  removeForm.reason = ''
  proxy.resetForm('removeFormRef')
  removeOpen.value = true
}

function submitRemove() {
  proxy.$refs.removeFormRef.validate((valid) => {
    if (!valid) return
    removing.value = true
    removeGraylist(removeForm.cardId, {
      reason: removeForm.reason,
      operator: userStore.name || ''
    }).then(() => {
      proxy.$modal.msgSuccess('移出成功')
      removeOpen.value = false
      getList()
    }).catch(() => {
      // 业务错误（如不在灰名单）由 request 拦截器统一弹错误提示，这里不重复弹窗。
    }).finally(() => { removing.value = false })
  })
}

function handleLogs(row) {
  logOpen.value = true
  logLoading.value = true
  logRows.value = []
  listGraylistOperateLogs({ cardId: row.cardId, pageNum: 1, pageSize: 50 }).then((response) => {
    logRows.value = response.data?.list || []
  }).catch((error) => {
    proxy.$modal.msgError(error?.message || '流水查询失败')
  }).finally(() => { logLoading.value = false })
}

function formatSource(value) {
  return ({ '01': '系统自动', '09': '运维手工' })[value] || value || '-'
}

onMounted(() => { getList() })
</script>
