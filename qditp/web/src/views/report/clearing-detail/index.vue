<template>
  <div class="app-container">
    <el-alert class="mb8" type="info" :closable="false" show-icon
      title="业务清分详细报表：渠道账单（RECON_THIRD_BILL）按「账单日 + 支付商户」聚合的清分口径，纯查询。" />
    <ClearingPanel />
  </div>
</template>

<script setup name="ReconClearingDetail">
import ClearingPanel from '@/views/report/panels/ClearingPanel.vue'
</script>
