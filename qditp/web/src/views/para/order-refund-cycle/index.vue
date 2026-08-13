<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true">
      <el-form-item label="票卡类型" prop="ticketType">
        <el-input v-model="queryParams.ticketType" placeholder="请输入票卡类型" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['para:order-refund-cycle:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="cycleList" border>
      <el-table-column label="序号" width="70" align="center">
        <template #default="scope">{{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}</template>
      </el-table-column>
      <el-table-column label="票卡类型" prop="ticketType" min-width="160" align="center" />
      <el-table-column label="自动退款周期" min-width="160" align="center">
        <template #default="{ row }">{{ row.autoRefundPeriod }} 天</template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="260" show-overflow-tooltip />
      <el-table-column label="创建时间" width="180" align="center">
        <template #default="{ row }">{{ parseTime(row.createTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="修改时间" width="180" align="center">
        <template #default="{ row }">{{ parseTime(row.updateTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default="{ row }">
          <el-tooltip content="修改" placement="top">
            <el-button v-hasPermi="['para:order-refund-cycle:edit']" link type="primary" icon="Edit" @click="handleUpdate(row)" />
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button v-hasPermi="['para:order-refund-cycle:remove']" link type="danger" icon="Delete" @click="handleDelete(row)" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="520px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="票卡类型" prop="ticketType">
          <el-input v-model="form.ticketType" :disabled="isEdit" maxlength="8" placeholder="请输入票卡类型" />
        </el-form-item>
        <el-form-item label="自动退款周期" prop="autoRefundPeriod">
          <el-input-number v-model="form.autoRefundPeriod" :min="0" :max="3650" :step="1" controls-position="right" />
          <span class="unit-label">天</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="OrderRefundCycle">
import { addOrderRefundCycle, delOrderRefundCycle, listOrderRefundCycles, updateOrderRefundCycle } from '@/api/para/orderRefundCycle'
// 票卡类型是退款周期的配置键，编辑时不允许改为另一票卡类型。

const { proxy } = getCurrentInstance()
const loading = ref(false)
const submitting = ref(false)
const open = ref(false)
const isEdit = ref(false)
const title = ref('')
const cycleList = ref([])
const total = ref(0)
const queryParams = reactive({ ticketType: '', pageNum: 1, pageSize: 10 })
const form = reactive({ ticketType: '', autoRefundPeriod: 0, remark: '' })
const rules = {
  ticketType: [
    { required: true, message: '请输入票卡类型', trigger: 'blur' },
    { max: 8, message: '票卡类型不能超过 8 个字符', trigger: 'blur' }
  ],
  autoRefundPeriod: [
    { required: true, message: '请输入自动退款周期', trigger: 'change' },
    { type: 'number', min: 0, max: 3650, message: '自动退款周期必须在 0 到 3650 天之间', trigger: 'change' }
  ],
  remark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listOrderRefundCycles({ ...queryParams }).then((response) => {
    const page = response.data || {}
    cycleList.value = page.list || []
    total.value = Number(page.total || 0)
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.ticketType = ''
  handleQuery()
}

function resetForm() {
  form.ticketType = ''
  form.autoRefundPeriod = 0
  form.remark = ''
  proxy.resetForm('formRef')
}

function handleAdd() {
  resetForm()
  isEdit.value = false
  title.value = '新增订单退款周期'
  open.value = true
}

function handleUpdate(row) {
  resetForm()
  form.ticketType = row.ticketType
  form.autoRefundPeriod = row.autoRefundPeriod
  form.remark = row.remark || ''
  isEdit.value = true
  title.value = '修改订单退款周期'
  open.value = true
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    submitting.value = true
    const data = { ...form, ticketType: form.ticketType.trim(), remark: form.remark.trim() || null }
    const request = isEdit.value ? updateOrderRefundCycle(form.ticketType, data) : addOrderRefundCycle(data)
    request.then(() => {
      proxy.$modal.msgSuccess(isEdit.value ? '修改成功' : '新增成功')
      open.value = false
      getList()
    }).finally(() => { submitting.value = false })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm(`确认删除票卡类型“${row.ticketType}”的退款周期配置吗？`).then(() => {
    return delOrderRefundCycle(row.ticketType)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

onMounted(getList)
</script>

<style scoped>
.unit-label {
  margin-left: 8px;
  color: var(--el-text-color-regular);
}
</style>
