<template>
  <div class="app-container">
    <el-form :model="query" :inline="true"><el-form-item label="规则编号"><el-input v-model="query.ruleId" clearable placeholder="请输入规则编号"/></el-form-item><el-form-item label="规则名称"><el-input v-model="query.ruleName" clearable placeholder="请输入规则名称"/></el-form-item><el-form-item label="风险组"><el-select v-model="query.groupId" clearable placeholder="全部"><el-option v-for="g in groups" :key="g.groupId" :label="g.groupName" :value="g.groupId"/></el-select></el-form-item><el-form-item><el-button type="primary" icon="Search" @click="search">查询</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item></el-form>
    <el-row class="mb8"><el-col :span="1.5"><el-button v-hasPermi="['para:risk-rule:add']" type="primary" plain icon="Plus" @click="add">新增</el-button></el-col></el-row>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
  </div>
</template>
<script setup name="RiskRule">
import { addRiskRule, delRiskRule, listGroupRuleIds, listRiskGroupOptions, listRiskRules, updateRiskRule } from '@/api/para/risk'
function load(){loading.value=true;listRiskRules({...query}).then(r=>{rows.value=r.data?.list||[];total.value=Number(r.data?.total||0)}).catch(()=>{proxy.$modal.msgError('查询风险规则失败')}).finally(()=>loading.value=false)}function loadGroups(){listRiskGroupOptions().then(r=>groups.value=r.data||[])}
function search(){query.pageNum=1;load()}function resetQuery(){Object.assign(query,{ruleId:'',ruleName:'',groupId:null});search()}
function add(){resetForm();editing.value=false;title.value='新增风险规则';open.value=true}
function edit(row){resetForm();Object.assign(form,row);form.groupIds=[];editing.value=true;title.value='修改风险规则';open.value=true;loadGroupIds(row.ruleId)}
function loadGroupIds(ruleId){groups.value.forEach(g=>{listGroupRuleIds(g.groupId).then(r=>{if((r.data||[]).includes(ruleId))form.groupIds.push(g.groupId)})})}
function remove(row){proxy.$modal.confirm(`确认删除风险规则“${row.ruleName}”吗？`).then(()=>delRiskRule(row.ruleId)).then(()=>{proxy.$modal.msgSuccess('删除成功');load()}).catch(()=>{})}
onMounted(()=>{loadGroups();load()})
</script>
