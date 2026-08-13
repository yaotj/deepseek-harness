<template>
  <div class="app-container">
    <el-alert title="该参数控制用户单次购买单程票的最大张数，修改后由购票服务读取生效。" type="info" :closable="false" show-icon class="mb8" />

    <el-table v-loading="loading" :data="limitList" border>
      <el-table-column label="序号" width="70" align="center">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>
      <el-table-column label="购买单程票最大张数" prop="maxPurchaseQuantity" min-width="240" align="center" />
      <el-table-column label="更新日期" width="200" align="center">
        <template #default="{ row }">{{ parseTime(row.updateTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="注册日期" width="200" align="center">
        <template #default="{ row }">{{ parseTime(row.createTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-tooltip content="修改" placement="top">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)" v-hasPermi="['para:single-ticket-purchase-limit:edit']" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !limitList.length" description="未初始化单程票购买上限参数" :image-size="100" />

    <el-dialog v-model="open" title="修改单程票最大购买张数" width="440px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="170px">
        <el-form-item label="最大购买张数" prop="maxPurchaseQuantity">
          <el-input-number v-model="form.maxPurchaseQuantity" :min="1" :max="99" :step="1" controls-position="right" />
          <span class="unit-label">张</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SingleTicketPurchaseLimit">
import { getSingleTicketPurchaseLimit, updateSingleTicketPurchaseLimit } from '@/api/para/singleTicketPurchaseLimit'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const submitting = ref(false)
const open = ref(false)
const limitList = ref([])
const form = reactive({ maxPurchaseQuantity: undefined, version: undefined })
const rules = {
  maxPurchaseQuantity: [
    { required: true, message: '请输入最大购买张数', trigger: 'change' },
    { type: 'number', min: 1, max: 99, message: '最大购买张数必须在 1 到 99 之间', trigger: 'change' }
  ]
}

function getList() {
  loading.value = true
  getSingleTicketPurchaseLimit().then((response) => {
    const data = response.data
    limitList.value = data ? [data] : []
  }).finally(() => { loading.value = false })
}

function handleUpdate(row) {
  form.maxPurchaseQuantity = row.maxPurchaseQuantity
  form.version = row.version
  open.value = true
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    submitting.value = true
    updateSingleTicketPurchaseLimit({ ...form }).then(() => {
      proxy.$modal.msgSuccess('修改成功')
      open.value = false
      getList()
    }).finally(() => { submitting.value = false })
  })
}

onMounted(getList)
</script>

<style scoped>
.unit-label {
  margin-left: 8px;
  color: var(--el-text-color-regular);
}
</style>
