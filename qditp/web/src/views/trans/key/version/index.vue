<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" class="mb8"
      title="只读展示各密钥域的版本元信息，不涉及密钥材料。" />

    <el-table v-loading="loading" :data="versionList" border>
      <el-table-column label="序号" type="index" width="70" align="center" />
      <el-table-column label="密钥域" prop="keyDomain" width="170" align="center">
        <template #default="{ row }">{{ domainName(row.keyDomain) }}</template>
      </el-table-column>
      <el-table-column label="接入方" prop="providerId" width="110" align="center">
        <template #default="{ row }">{{ row.providerId || '-' }}</template>
      </el-table-column>
      <el-table-column label="当前版本" prop="currentVersion" min-width="140" align="center">
        <template #default="{ row }">{{ row.currentVersion || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="statusDesc" width="130" align="center" />
      <el-table-column label="生效日期" prop="effectiveDate" width="180" align="center">
        <template #default="{ row }">{{ row.effectiveDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="180" align="center">
        <template #default="{ row }">{{ row.updateTime || '-' }}</template>
      </el-table-column>
    </el-table>

    <div class="mt8">
      <el-button icon="Refresh" @click="getList">刷新</el-button>
    </div>
  </div>
</template>

<script setup name="KeyVersion">
import { listKeyVersions } from '@/api/trans/keyVersion'

const loading = ref(false)
const versionList = ref([])

const DOMAIN_NAMES = {
  AGM_KEY: 'AGM 闸机密钥',
  CA_KEYSTORE: 'CA 密钥仓库',
  HCE_STATIC_KEY: 'HCE 静态密钥'
}

function domainName(code) {
  return DOMAIN_NAMES[code] || code
}

function getList() {
  loading.value = true
  listKeyVersions()
    .then((response) => { versionList.value = response.data || [] })
    .finally(() => { loading.value = false })
}

onMounted(getList)
</script>
