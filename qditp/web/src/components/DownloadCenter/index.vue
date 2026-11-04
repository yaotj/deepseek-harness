<template>
  <el-popover v-if="allowed" placement="bottom-end" :width="360" trigger="click" popper-class="download-center-pop">
    <template #reference>
      <el-badge :value="activeCount" :hidden="activeCount === 0" :max="99" class="download-badge">
        <div class="download-entry">
          <el-icon><Download /></el-icon>
        </div>
      </el-badge>
    </template>

    <div class="dc-head">
      <span class="dc-title">下载中心</span>
      <el-button link type="primary" size="small" icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
    </div>

    <div v-if="!tasks.length" class="dc-empty">近期没有导出任务</div>
    <ul v-else class="dc-list">
      <li v-for="task in tasks" :key="task.id" class="dc-item">
        <div class="dc-line">
          <status-tag :value="task.status" class="dc-tag" />
          <span class="dc-name" :title="task.taskName">{{ task.taskName }}</span>
        </div>
        <div class="dc-meta">
          <span>{{ formatTime(task.createTime) }}</span>
          <span v-if="task.status === 'SUCCESS'">{{ task.rowCount ?? 0 }} 行 · {{ formatSize(task.fileSize) }}</span>
          <span v-else-if="task.status === 'FAILED'" class="dc-fail" :title="task.errorMsg">失败：{{ shortError(task.errorMsg) }}</span>
          <el-button
            v-if="task.status === 'SUCCESS'"
            link
            type="primary"
            size="small"
            icon="Download"
            :loading="downloadingId === task.id"
            @click="downloadTask(task)"
          >下载</el-button>
        </div>
      </li>
    </ul>

    <div class="dc-foot">
      <el-button link type="primary" size="small" @click="goHistory">历史任务与筛选</el-button>
    </div>
  </el-popover>
</template>

<script setup name="DownloadCenter">
import { ElNotification } from 'element-plus'
import { listAsyncExportTask, downloadAsyncExportFile } from '@/api/monitor/asyncExport'
import { parseTime } from '@/utils/points'
import useUserStore from '@/store/modules/user'
import { saveAs } from 'file-saver'

/**
 * 顶栏下载中心：导出任务原本只在「系统监控-异步导出任务」这个孤立页里可见，
 * 提交后用户不点进去就感知不到完成，这里把「我的任务」提到全局。
 */
const POLL_MS = 10000
const PAGE_SIZE = 8

const router = useRouter()
const userStore = useUserStore()
const tasks = ref([])
const loading = ref(false)
const downloadingId = ref(null)
/** 首次拉取只建基线、不弹通知，否则一进系统就被历史任务刷屏。 */
const seeded = ref(false)
let timer = null

const allowed = computed(() => userStore.permissions.some(
  (permission) => permission === '*:*:*' || permission === 'monitor:async-export:query'
))

const activeCount = computed(() => tasks.value.filter(
  (task) => task.status === 'PENDING' || task.status === 'RUNNING'
).length)

function fetchTasks() {
  if (!allowed.value) return
  loading.value = true
  listAsyncExportTask({ pageNum: 1, pageSize: PAGE_SIZE, createBy: userStore.name })
    .then((response) => {
      const rows = response.rows || []
      notifyFinished(rows)
      tasks.value = rows
    })
    .catch(() => {
      // 轮询失败不打扰用户：下一轮还会再试，历史任务页会给出可读错误。
    })
    .finally(() => {
      loading.value = false
      seeded.value = true
    })
}

/** 只对「上一轮还在跑、这一轮已成功」的任务弹通知，避免每次刷新都提示同一条。 */
function notifyFinished(rows) {
  if (!seeded.value) return
  const previous = new Map(tasks.value.map((task) => [task.id, task.status]))
  rows.forEach((row) => {
    const before = previous.get(row.id)
    if (row.status === 'SUCCESS' && (before === 'PENDING' || before === 'RUNNING')) {
      ElNotification({
        title: '导出完成',
        message: `${row.taskName}（${row.rowCount ?? 0} 行）`,
        type: 'success',
        duration: 8000
      })
    }
  })
}

function refresh() {
  fetchTasks()
}

function downloadTask(task) {
  if (!task.filePath) {
    ElNotification({ title: '无法下载', message: '任务没有产出文件', type: 'warning' })
    return
  }
  downloadingId.value = task.id
  downloadAsyncExportFile(task.id)
    .then((response) => {
      const fileName = task.filePath.substring(task.filePath.lastIndexOf('/') + 1)
      saveAs(new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), fileName)
    })
    .catch(() => {
      ElNotification({ title: '下载失败', message: '文件可能已被清理，请到历史任务页重新生成', type: 'error' })
    })
    .finally(() => {
      downloadingId.value = null
    })
}

function goHistory() {
  router.push('/monitor/async-export')
}

function formatTime(value) {
  return parseTime(value, '{m}-{d} {h}:{i}') || '-'
}

function formatSize(size) {
  const num = Number(size)
  if (!Number.isFinite(num) || num <= 0) return '-'
  if (num < 1024) return `${num} B`
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(2)} KB`
  return `${(num / (1024 * 1024)).toFixed(2)} MB`
}

function shortError(errorMsg) {
  const text = String(errorMsg || '未知原因')
  return text.length > 18 ? `${text.slice(0, 18)}…` : text
}

onMounted(() => {
  if (!allowed.value) return
  fetchTasks()
  timer = setInterval(fetchTasks, POLL_MS)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.download-entry {
  display: flex;
  align-items: center;
  height: 100%;
  cursor: pointer;
}
.download-badge {
  line-height: 50px;
}
.dc-head,
.dc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dc-title {
  font-weight: 700;
  font-size: 14px;
}
.dc-empty {
  padding: 16px 0;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dc-list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}
.dc-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.dc-item:last-child {
  border-bottom: none;
}
.dc-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dc-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
}
.dc-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dc-fail {
  color: var(--el-color-danger);
}
.dc-foot {
  margin-top: 8px;
  justify-content: flex-end;
}
</style>
