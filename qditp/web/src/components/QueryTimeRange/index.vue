<template>
  <div class="query-time-range">
    <div class="range-controls">
      <el-date-picker
        v-model="inner"
        :type="pickerType"
        :value-format="valueFormat"
        :default-time="defaultTime"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        :style="{ width: `${width}px` }"
        @change="onPickChange"
      />
      <span class="preset-group">
        <el-button
          v-for="preset in visiblePresets"
          :key="preset.key"
          link
          type="primary"
          :class="['preset-chip', { 'is-active': activeKey === preset.key }]"
          @click="applyPreset(preset)"
        >{{ preset.label }}</el-button>
      </span>
    </div>
  </div>
</template>

<script setup name="QueryTimeRange">
import { defaultTodayRange, lastDaysRange, isEmptyRange } from '@/utils/dateRange'

const props = defineProps({
  /** [开始, 结束]，与页面 el-date-picker 的 value-format 同为字符串数组；空数组表示不限时间。 */
  modelValue: { type: Array, default: () => [] },
  /** 必须与页面查询参数原本的 value-format 一致，决定区间精度与选择器类型。 */
  valueFormat: { type: String, default: 'YYYY-MM-DD' },
  /** 后端是否接受不带时间的查询。face-pay / gate-txn-pay / offline-stats 这类接口缺时间范围会直接 400，那些页面必须关掉。 */
  allowAll: { type: Boolean, default: true },
  /** 选择器宽度（px）。datetimerange 下 340 会遮住末尾秒数，统一放宽到 420 保证 HH:mm:ss 完整可见。 */
  width: { type: Number, default: 420 }
})

const emit = defineEmits(['update:modelValue', 'change'])

const PRESETS = [
  { key: 'today', label: '今天', build: (format) => defaultTodayRange(format) },
  { key: '7d', label: '近7天', build: (format) => lastDaysRange(7, format) },
  { key: '30d', label: '近30天', build: (format) => lastDaysRange(30, format) },
  { key: 'all', label: '全部', build: () => [] }
]

const inner = ref(normalize(props.modelValue))
const activeKey = ref(null)

const pickerType = computed(() => (/HH|mm|ss/.test(props.valueFormat) ? 'datetimerange' : 'daterange'))
/**
 * 手动只点日期时补齐的两端时刻：00:00:00 ~ 23:59:59。
 * 不设的话 Element Plus 对 datetimerange 两端都取 00:00:00，
 * 「选今天」会退化成零长度的一个瞬间，查出来永远是空表。
 */
const defaultTime = [new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]
const visiblePresets = computed(() => (props.allowAll ? PRESETS : PRESETS.filter((p) => p.key !== 'all')))

watch(() => props.modelValue, (value) => {
  inner.value = normalize(value)
})

function normalize(value) {
  return isEmptyRange(value) ? [] : [value[0], value[1]]
}

function setValue(value, key) {
  inner.value = value
  activeKey.value = key
  emit('update:modelValue', value)
  emit('change', value)
}

function applyPreset(preset) {
  setValue(preset.build(props.valueFormat), preset.key)
}

/** 手动改选后不再高亮任何快捷项——此时区间是用户自定义的，chip 不能假装还命中某个预设。 */
function onPickChange(value) {
  activeKey.value = null
  emit('update:modelValue', normalize(value))
  emit('change', normalize(value))
}
</script>

<style scoped>
.query-time-range {
  display: inline-block;
  vertical-align: top;
}
.range-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.preset-group {
  display: inline-flex;
  align-items: center;
  margin-left: 4px;
}
.preset-chip {
  padding: 0 6px;
  font-size: 12px;
}
.preset-chip.is-active {
  font-weight: 700;
  text-decoration: underline;
}
</style>
