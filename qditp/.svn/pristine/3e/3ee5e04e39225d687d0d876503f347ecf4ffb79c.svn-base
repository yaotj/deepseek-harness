<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="卡ID">
        <el-input v-model="queryParams.cardId" placeholder="请输入卡ID" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="三方用户ID">
        <el-input v-model="queryParams.thirdUserId" placeholder="请输入三方用户ID" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker v-model="queryParams.dateRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" style="width: 340px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['trans:blacklist:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="blacklist" border>
      <el-table-column label="序号" width="70" align="center">
        <template #default="scope">{{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}</template>
      </el-table-column>
      <el-table-column label="卡ID" prop="cardId" min-width="220" show-overflow-tooltip />
      <el-table-column label="三方用户ID" prop="thirdUserId" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.thirdUserId || '-' }}</template>
      </el-table-column>
      <el-table-column label="拉黑原因" prop="reason" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="180" align="center">
        <template #default="{ row }">{{ parseTime(row.createTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-tooltip content="移除黑名单" placement="top">
            <el-button v-hasPermi="['trans:blacklist:remove']" link type="danger" icon="Delete" @click="handleDelete(row)" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="open" title="新增黑名单" width="560px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="卡ID" prop="cardId">
          <el-input v-model="form.cardId" maxlength="32" placeholder="请输入卡ID" />
        </el-form-item>
        <el-form-item label="三方用户ID" prop="thirdUserId">
          <el-input v-model="form.thirdUserId" maxlength="16" placeholder="请输入三方用户ID" />
        </el-form-item>
        <el-form-item label="卡类型" prop="cardType">
          <el-input v-model="form.cardType" maxlength="32" placeholder="用于渠道同步，可不填" />
        </el-form-item>
        <el-form-item label="拉黑原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="请输入拉黑原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="BlacklistManagement">
import { addBlacklist, delBlacklist, listBlacklists } from '@/api/trans/blacklist'
// 新增和删除均调用后台管理入口，后台会保留操作日志并执行既有渠道同步。

const { proxy } = getCurrentInstance()
const loading = ref(false)
const submitting = ref(false)
const open = ref(false)
const blacklist = ref([])
const total = ref(0)
const queryParams = reactive({ cardId: '', thirdUserId: '', dateRange: [], pageNum: 1, pageSize: 10 })
const form = reactive({ cardId: '', thirdUserId: '', cardType: '', reason: '' })
const rules = {
  cardId: [
    { required: true, message: '请输入卡ID', trigger: 'blur' },
    { max: 32, message: '卡ID不能超过32个字符', trigger: 'blur' }
  ],
  thirdUserId: [{ max: 16, message: '三方用户ID不能超过16个字符', trigger: 'blur' }],
  cardType: [{ max: 32, message: '卡类型不能超过32个字符', trigger: 'blur' }],
  reason: [{ max: 1000, message: '拉黑原因不能超过1000个字符', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  const [createTimeBegin, createTimeEnd] = queryParams.dateRange || []
  listBlacklists({
    cardId: queryParams.cardId,
    thirdUserId: queryParams.thirdUserId,
    createTimeBegin,
    createTimeEnd,
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize
  }).then((response) => {
    const page = response.data || {}
    blacklist.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.cardId = ''
  queryParams.thirdUserId = ''
  queryParams.dateRange = []
  handleQuery()
}

function resetForm() {
  form.cardId = ''
  form.thirdUserId = ''
  form.cardType = ''
  form.reason = ''
  proxy.resetForm('formRef')
}

function handleAdd() {
  resetForm()
  open.value = true
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    submitting.value = true
    addBlacklist({
      cardId: form.cardId.trim(),
      thirdUserId: form.thirdUserId.trim() || null,
      cardType: form.cardType.trim() || null,
      reason: form.reason.trim() || null
    }).then(() => {
      proxy.$modal.msgSuccess('新增成功')
      open.value = false
      getList()
    }).finally(() => { submitting.value = false })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm(`确认移除卡ID“${row.cardId}”的黑名单吗？`).then(() => {
    return delBlacklist(row.cardId)
  }).then(() => {
    proxy.$modal.msgSuccess('移除成功')
    getList()
  }).catch(() => {})
}

onMounted(getList)
</script>
