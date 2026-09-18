<template>
  <div class="app-container">
    <el-form :model="query" :inline="true">
      <el-form-item label="逻辑卡号"><el-input v-model="query.cardId" clearable placeholder="请输入逻辑卡号" @keyup.enter="search"/></el-form-item>
      <el-form-item label="用户订单"><el-input v-model="query.userOrderNo" clearable placeholder="请输入用户订单号" @keyup.enter="search"/></el-form-item>
      <el-form-item label="规则编号"><el-input v-model="query.ruleId" clearable placeholder="请输入风险规则编号" @keyup.enter="search"/></el-form-item>
      <el-form-item label="命中时间"><el-date-picker v-model="query.dateRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" style="width:340px"/></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="search">查询</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column label="序号" width="70" align="center"><template #default="s">{{(query.pageNum-1)*query.pageSize+s.$index+1}}</template></el-table-column>
      <el-table-column label="逻辑卡号" prop="cardId" min-width="200" show-overflow-tooltip><template #default="{row}">{{row.cardId||'-'}}</template></el-table-column>
      <el-table-column label="用户订单" prop="userOrderNo" min-width="210" show-overflow-tooltip><template #default="{row}">{{row.userOrderNo||'-'}}</template></el-table-column>
      <el-table-column label="风险规则编号" prop="ruleId" width="150" align="center"/>
      <el-table-column label="风险规则名称" prop="ruleName" min-width="230"><template #default="{row}">{{row.ruleName||'-'}}</template></el-table-column>
      <el-table-column label="风险发生最后时间" width="190" align="center"><template #default="{row}">{{parseTime(row.riskHitTime)||'-'}}</template></el-table-column>
      <el-table-column label="入库时间" width="180" align="center"><template #default="{row}">{{parseTime(row.createTime)||'-'}}</template></el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
  </div>
</template>
<script setup name="RiskControlLog">
import { listRiskControlLogs } from '@/api/para/risk'
import { defaultTodayRange } from '@/utils/dateRange'

/** 必须与模板里 el-date-picker 的 value-format 保持一致。 */
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'
// 风险命中记录为审计数据，只提供查询和分页浏览。
const loading=ref(false),rows=ref([]),total=ref(0);const query=reactive({cardId:'',userOrderNo:'',ruleId:'',dateRange:defaultTodayRange(DATE_FORMAT),pageNum:1,pageSize:10})
function load(){loading.value=true;const [riskHitTimeBegin,riskHitTimeEnd]=query.dateRange||[];listRiskControlLogs({cardId:query.cardId,userOrderNo:query.userOrderNo,ruleId:query.ruleId,riskHitTimeBegin,riskHitTimeEnd,pageNum:query.pageNum,pageSize:query.pageSize}).then(r=>{rows.value=r.data?.list||[];total.value=Number(r.data?.total||0)}).finally(()=>loading.value=false)}function search(){query.pageNum=1;load()}function resetQuery(){Object.assign(query,{cardId:'',userOrderNo:'',ruleId:'',dateRange:defaultTodayRange(DATE_FORMAT)});search()}onMounted(load)
</script>
