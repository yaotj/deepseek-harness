<template>
  <div class="app-container">
    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="日统计报表（按支付账户）：与「日统计汇总（按支付账户）」同一数据口径，本报表面向明细展示（金额分项：现金/优惠/退款）。" />
    <DailyStatsPanel mode="VENDOR" />
  </div>
</template>

<script setup name="DailyStatsAccountReport">
import DailyStatsPanel from '@/views/report/panels/DailyStatsPanel.vue'
</script>
