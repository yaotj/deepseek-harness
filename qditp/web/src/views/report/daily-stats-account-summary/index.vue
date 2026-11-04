<template>
  <div class="app-container">
    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="日统计汇总（按支付账户）：对 PAY_TXN_DETAIL 按「交易日期 + 支付账户（PAYMENT_VENDOR）」纯查询聚合，不建聚合表。" />
    <DailyStatsPanel mode="VENDOR" />
  </div>
</template>

<script setup name="DailyStatsAccountSummary">
import DailyStatsPanel from '@/views/report/panels/DailyStatsPanel.vue'
</script>
