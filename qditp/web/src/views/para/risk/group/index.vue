<template>
  <div class="app-container">
    <el-form :model="query" :inline="true"><el-form-item label="风险组名称"><el-input v-model="query.groupName" clearable placeholder="请输入风险组名称" @keyup.enter="search" /></el-form-item><el-form-item><el-button type="primary" icon="Search" @click="search">查询</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item></el-form>
    <el-row class="mb8">
      <el-col :span="1.5"><el-button v-hasPermi="['para:risk-group:add']" type="primary" plain icon="Plus" @click="add">新增</el-button></el-col>
    </el-row>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column label="序号" width="70" align="center"><template #default="s">{{ (query.pageNum - 1) * query.pageSize + s.$index + 1 }}</template></el-table-column>
      <el-table-column label="风险组名称" prop="groupName" min-width="180"/>
      <el-table-column label="风险组描述" prop="groupDesc" min-width="260" show-overflow-tooltip/>
      <el-table-column label="修改时间" width="160" align="center"><template #default="{ row }">{{ parseTime(row.updateTime) || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="240" align="center">
        <template #default="{ row }">
          <el-tooltip content="管理规则" placement="top"><el-button v-hasPermi="['para:risk-group:edit']" link type="primary" icon="List" @click="manageRules(row)" /></el-tooltip>
          <el-tooltip content="修改" placement="top"><el-button v-hasPermi="['para:risk-group:edit']" link type="primary" icon="Edit" @click="edit(row)" /></el-tooltip>
          <el-tooltip content="删除" placement="top"><el-button v-hasPermi="['para:risk-group:remove']" link type="danger" icon="Delete" @click="remove(row)" /></el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>

    <!-- 新增/编辑组弹窗 -->
    <el-dialog v-model="open" :title="title" width="560px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="风险组名称" prop="groupName"><el-input v-model="form.groupName" maxlength="100"/></el-form-item>
        <el-form-item label="风险组描述" prop="groupDesc"><el-input v-model="form.groupDesc" type="textarea" :rows="3" maxlength="500" show-word-limit/></el-form-item>
      </el-form>
      <template #footer><el-button @click="open=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">确定</el-button></template>
    </el-dialog>

    <!-- 管理规则弹窗 -->
    <el-dialog v-model="ruleOpen" title="管理风险组规则" width="700px" append-to-body @open="onRuleDialogOpen">
      <div class="mb8">
        <el-input v-model="ruleSearch" clearable placeholder="搜索规则编号/名称" style="width:260px;margin-right:8px"/>
        <span class="text-secondary">已选 <el-tag type="primary" size="small">{{ currentRuleIds.length }}</el-tag> / {{ ruleOptions.length }} 条</span>
      </div>
      <el-table :data="filteredRuleOptions" border size="small" max-height="360" @selection-change="onRuleSelectionChange">
        <el-table-column type="selection" width="40"/>
        <el-table-column label="规则编号" prop="ruleId" width="130" align="center"/>
        <el-table-column label="规则名称" prop="ruleName" min-width="200"/>
        <el-table-column label="风险等级" prop="riskLevel" width="90" align="center"><template #default="{row}">{{row.riskLevel}}级</template></el-table-column>
        <el-table-column label="阈值" prop="riskLimitValue" width="100" align="right"/>
      </el-table>
      <div class="mt8 text-secondary" style="font-size:12px">勾选后点击确定保存。可在本弹窗内直接增减规则。</div>
      <template #footer>
        <el-button @click="ruleOpen=false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="saveGroupRules">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup name="RiskGroup">
import { addRiskGroup, delRiskGroup, listGroupRuleIds, listRiskGroups, listRiskRules, saveGroupRuleIds, updateRiskGroup } from '@/api/para/risk'
const { proxy } = getCurrentInstance()
const loading = ref(false), saving = ref(false), ruleSaving = ref(false)
const open = ref(false), editing = ref(false), title = ref(''), rows = ref([]), total = ref(0), ruleOptions = ref([])
// 规则管理弹窗
const ruleOpen = ref(false), currentGroup = ref(null), currentRuleIds = ref([]), ruleSearch = ref('')

const query = reactive({ groupName: '', pageNum: 1, pageSize: 10 })
const rules = { groupName: [{ required: true, message: '请输入风险组名称', trigger: 'blur' }] }

function load() {
  loading.value = true
  listRiskGroups({ ...query }).then(r => { rows.value = r.data?.list || []; total.value = Number(r.data?.total || 0) })
    .catch(() => { proxy.$modal.msgError('查询风险组失败') })
    .finally(() => { loading.value = false })
}
function loadRuleOptions() {
  listRiskRules({ pageNum: 1, pageSize: 100 }).then(r => { ruleOptions.value = r.data?.list || [] })
}
function search() { query.pageNum = 1; load() }
function resetQuery() { query.groupName = ''; search() }
function add() { resetForm(); editing.value = false; title.value = '新增风险组'; open.value = true }
function edit(row) { resetForm(); Object.assign(form, row); editing.value = true; title.value = '修改风险组'; open.value = true }
function submit() {
  proxy.$refs.formRef.validate(valid => {
    if (!valid) return
    saving.value = true
    if (editing.value) {
      updateRiskGroup(form.groupId, data).then(() => { proxy.$modal.msgSuccess('修改成功'); open.value = false; load() })
        .catch(() => { proxy.$modal.msgError('保存风险组失败') }).finally(() => saving.value = false)
    } else {
      addRiskGroup(data).then(r => { proxy.$modal.msgSuccess('新增成功'); open.value = false; load() })
        .catch(() => { proxy.$modal.msgError('新增风险组失败') }).finally(() => saving.value = false)
    }
  })
}
function remove(row) {
  proxy.$modal.confirm(`确认删除风险组"${row.groupName}"吗？`).then(() => delRiskGroup(row.groupId))
    .then(() => { proxy.$modal.msgSuccess('删除成功'); load() }).catch(() => {})
}

// 管理规则弹窗逻辑
function manageRules(row) {
  currentGroup.value = row
  ruleSearch.value = ''
  ruleOpen.value = true
}
function onRuleDialogOpen() {
  if (!currentGroup.value) return
  listGroupRuleIds(currentGroup.value.groupId).then(r => { currentRuleIds.value = r.data || [] })
}
const filteredRuleOptions = computed(() => {
  if (!ruleSearch.value) return ruleOptions.value
  const kw = ruleSearch.value.toLowerCase()
  return ruleOptions.value.filter(r => (r.ruleId || '').toLowerCase().includes(kw) || (r.ruleName || '').toLowerCase().includes(kw))
})
function onRuleSelectionChange(selected) {
  currentRuleIds.value = selected.map(r => r.ruleId)
}
function saveGroupRules() {
  if (!currentGroup.value) return
  ruleSaving.value = true
  saveGroupRuleIds(currentGroup.value.groupId, currentRuleIds.value)
    .then(() => { proxy.$modal.msgSuccess('规则已更新'); ruleOpen.value = false; load() })
    .catch(() => { proxy.$modal.msgError('保存规则失败') })
    .finally(() => { ruleSaving.value = false })
}

onMounted(() => { load(); loadRuleOptions() })
</script>
<style scoped>
.mb8 { margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
.text-secondary { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
