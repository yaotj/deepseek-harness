<template>
  <div class="empty-state">
    <template v-if="notice">
      <el-icon class="empty-icon"><InfoFilled /></el-icon>
      <p class="empty-title">请先完善查询条件</p>
      <p class="empty-desc">{{ notice }}</p>
    </template>
    <template v-else-if="error">
      <el-icon class="empty-icon is-error"><WarningFilled /></el-icon>
      <p class="empty-title">查询失败</p>
      <p class="empty-desc">{{ error }}</p>
      <el-button size="small" type="primary" plain icon="Refresh" @click="emit('retry')">重试</el-button>
    </template>
    <template v-else-if="activeFilterCount > 0">
      <el-icon class="empty-icon"><Search /></el-icon>
      <p class="empty-title">无匹配结果</p>
      <p class="empty-desc">
        已应用 {{ activeFilterCount }} 个筛选条件（时间范围：{{ rangeCaption }}），没有记录同时满足
      </p>
      <el-button size="small" plain icon="RefreshLeft" @click="emit('clear')">清空筛选</el-button>
    </template>
    <template v-else-if="plain">
      <el-icon class="empty-icon"><DocumentRemove /></el-icon>
      <p class="empty-title">暂无记录</p>
      <p v-if="hint" class="empty-desc">{{ hint }}</p>
      <el-button size="small" plain icon="Refresh" @click="emit('retry')">重新查询</el-button>
    </template>
    <template v-else>
      <el-icon class="empty-icon"><DocumentRemove /></el-icon>
      <p class="empty-title">该时间范围内暂无记录</p>
      <p class="empty-desc">当前范围：{{ rangeCaption }}{{ hintSuffix }}</p>
      <el-button v-if="!relaxed" size="small" type="primary" plain @click="relax">放宽到近 30 天</el-button>
      <el-button v-else size="small" plain icon="Refresh" @click="emit('retry')">重新查询</el-button>
    </template>
  </div>
</template>

<script setup name="EmptyState">
import { lastDaysRange, isEmptyRange, describeRange } from '@/utils/dateRange'

const props = defineProps({
  /** 查询失败的原文（页面在 catch 里写入）；为空表示请求成功、只是没有数据。 */
  error: { type: String, default: '' },
  /** 后端要求的前置条件文案：还没发请求就拦在本地时用它，NEVER 塞进 error（那会被读成「查询失败」）。 */
  notice: { type: String, default: '' },
  /** 页面整个查询对象，组件按值筛出「真正生效的筛选条件」，页面不需要再写一个 computed。 */
  filters: { type: Object, default: () => ({}) },
  /** 当前时间区间，用于「放宽到近 30 天」直接改写页面筛选。 */
  range: { type: Array, default: () => [] },
  valueFormat: { type: String, default: 'YYYY-MM-DD' },
  /** 业务上确实只有当天数据的页面（如当日订单退款）不需要引导用户放宽区间。 */
  hint: { type: String, default: '' },
  /** 没有时间区间概念的页面（探活、树表）用它，避免把「暂无记录」说成「该时间范围内暂无记录」。 */
  plain: { type: Boolean, default: false }
})

const emit = defineEmits(['retry', 'clear', 'relax', 'update:range'])

/** 分页与区间不是「筛选条件」；queryType/keyword 在用户查询页有默认值，计入会把「一进页面」说成「有筛选」。 */
const NON_FILTER_KEYS = ['pageNum', 'pageSize', 'params', 'queryType', 'keyword']

const rangeCaption = computed(() => describeRange(props.range, props.valueFormat))

/**
 * 数「真正生效的筛选条件」。
 *
 * 数组值一律不算——它是时间区间，已经在「当前范围」里单独回显了。
 * 若把它计成筛选条件，凡是把区间放在同一个查询对象里的页面
 * （control-log / blacklist / card-pool…）一进页面就走「无匹配结果」分支，
 * 最该出现的「放宽到近 30 天」引导反而永远看不到（2026-11-04 线上实测踩到）。
 */
const activeFilterCount = computed(() =>
  Object.entries(props.filters || []).filter(([key, value]) => {
    if (NON_FILTER_KEYS.includes(key)) return false
    if (Array.isArray(value)) return false
    return value !== '' && value !== null && value !== undefined
  }).length
)

const relaxed = computed(() => isEmptyRange(props.range) || props.range[0] < lastDaysRange(30, props.valueFormat)[0])

const hintSuffix = computed(() => (props.hint ? `；${props.hint}` : ''))

function relax() {
  const next = lastDaysRange(30, props.valueFormat)
  emit('update:range', next)
  emit('relax', next)
}
</script>

<style scoped>
.empty-state {
  padding: 24px 0;
  text-align: center;
}
.empty-icon {
  font-size: 28px;
  color: var(--el-text-color-placeholder);
}
.empty-icon.is-error {
  color: var(--el-color-danger);
}
.empty-title {
  margin: 8px 0 4px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.empty-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
