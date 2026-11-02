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
      <el-button type="warning" icon="Download" :disabled="!versionList.length" @click="handleExport">导出 Excel</el-button>
      <el-button type="primary" icon="Clock" @click="handleAsyncExport">后台全量导出</el-button>
    </div>
  </div>
</template>

<script setup name="KeyVersion">
import { listKeyVersions } from '@/api/trans/keyVersion'
import { submitAsyncExportTask } from '@/api/monitor/asyncExport'
import { exportToExcel } from '@/utils/excelExport'

const { proxy } = getCurrentInstance()
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

/** 导出密钥域版本「当前页」（全量，无分页）。 */
function handleExport() {
  if (!versionList.value.length) { proxy.$modal.msgWarning('当前页没有可导出的密钥域版本'); return }
  try {
    const data = versionList.value.map((r) => ({
      keyDomain: domainName(r.keyDomain),
      providerId: r.providerId || '-',
      currentVersion: r.currentVersion || '-',
      statusDesc: r.statusDesc,
      effectiveDate: r.effectiveDate || '-',
      updateTime: r.updateTime || '-'
    }))
    const headers = [
      { key: 'keyDomain', header: '密钥域' },
      { key: 'providerId', header: '接入方' },
      { key: 'currentVersion', header: '当前版本' },
      { key: 'statusDesc', header: '状态' },
      { key: 'effectiveDate', header: '生效日期' },
      { key: 'updateTime', header: '更新时间' }
    ]
    const dateStr = new Date().toISOString().slice(0, 10)
    exportToExcel(data, headers, `KEY版本_${dateStr}`)
    proxy.$modal.msgSuccess(`已导出 ${data.length} 条密钥域版本`)
  } catch (e) {
    proxy.$modal.msgError(e.message || '导出失败')
  }
}

/** 提交后台异步全量导出任务（每日凌晨 2 点统一执行）。 */
function handleAsyncExport() {
  const data = {
    taskCode: 'KEY_VERSION',
    taskName: '密钥版本全量导出',
    paramJson: JSON.stringify({})
  }
  submitAsyncExportTask(data).then((response) => {
    proxy.$modal.msgSuccess(response.msg || '提交成功，请前往「系统监控-异步导出任务」查看')
  }).catch((error) => {
    proxy.$modal.msgError(error.message || '提交失败')
  })
}

onMounted(getList)
</script>
