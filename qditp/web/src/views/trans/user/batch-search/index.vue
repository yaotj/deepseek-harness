<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" class="mb8"
      title="上传 .txt / .csv 文件（每行或逗号分隔一个逻辑卡号），或在下方文本框直接粘贴；单批最多 500 条。" />

    <el-form :inline="true">
      <el-form-item>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :show-file-list="false"
          accept=".txt,.csv"
          :on-change="handleFile"
        >
          <el-button type="primary" icon="Upload">选择文件</el-button>
        </el-upload>
      </el-form-item>
      <el-form-item>
        <el-button type="success" icon="Search" :loading="loading" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="warning" icon="Download" :disabled="resultList.length === 0" @click="handleExport">导出 CSV</el-button>
      </el-form-item>
    </el-form>

    <el-input
      v-model="cardIdText"
      type="textarea"
      :rows="4"
      placeholder="每行一个逻辑卡号，或用逗号分隔"
      class="mb8"
    />
    <div class="mb8">已识别卡号 {{ parsedCardIds.length }} 条<template v-if="parsedCardIds.length > 500">（超出 500 上限，请分批）</template></div>

    <el-table v-loading="loading" :data="resultList" border>
      <el-table-column label="序号" type="index" width="70" align="center" />
      <el-table-column label="逻辑卡号" prop="cardId" min-width="180" align="center" />
      <el-table-column label="手机号" prop="msisdn" width="130" align="center" />
      <el-table-column label="票种" prop="cardType" width="90" align="center" />
      <el-table-column label="状态" prop="status" width="90" align="center" />
      <el-table-column label="注册时间" prop="regTms" width="180" align="center" />
      <el-table-column label="解绑申请时间" prop="terminationRequestTime" width="180" align="center" />
      <el-table-column label="解绑成功时间" prop="terminationCompleteTime" width="180" align="center" />
    </el-table>
  </div>
</template>

<script setup name="ItpUserBatchSearch">
import { batchSearchUsers } from '@/api/trans/userBatchSearch'
import { ElMessage } from 'element-plus'

const MAX_BATCH = 500

const loading = ref(false)
const cardIdText = ref('')
const resultList = ref([])

/** 文本按行/逗号/分号/空白切分出卡号列表，去空白去重。 */
const parsedCardIds = computed(() => {
  const raw = cardIdText.value || ''
  const ids = raw.split(/[\s,，;；]+/).map(s => s.trim()).filter(Boolean)
  return [...new Set(ids)]
})

function handleFile(file) {
  const reader = new FileReader()
  reader.onload = (e) => {
    cardIdText.value = String(e.target.result || '')
  }
  reader.readAsText(file.raw, 'UTF-8')
}

function handleQuery() {
  const ids = parsedCardIds.value
  if (ids.length === 0) {
    ElMessage.warning('请先上传文件或粘贴逻辑卡号')
    return
  }
  if (ids.length > MAX_BATCH) {
    ElMessage.warning(`单批最多 ${MAX_BATCH} 条，当前 ${ids.length} 条，请分批`)
    return
  }
  loading.value = true
  batchSearchUsers(ids)
    .then((response) => { resultList.value = response.data || [] })
    .finally(() => { loading.value = false })
}

function resetQuery() {
  cardIdText.value = ''
  resultList.value = []
}

/** 前端直接拼 CSV 落盘（加 BOM 让 Excel 正确识别 UTF-8），无需后端导出端点。 */
function handleExport() {
  const header = '逻辑卡号,手机号,票种,状态,注册时间,解绑申请时间,解绑成功时间'
  const lines = resultList.value.map(row =>
    [row.cardId, row.msisdn, row.cardType, row.status, row.regTms, row.terminationRequestTime, row.terminationCompleteTime]
      .map(v => `"${String(v ?? '').replace(/"/g, '""')}"`)
      .join(','))
  const blob = new Blob(['﻿' + [header, ...lines].join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `逻辑卡号批量查询_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}
</script>
