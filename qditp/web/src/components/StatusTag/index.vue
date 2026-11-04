<template>
  <el-tag :type="resolvedType" :effect="effect" disable-transitions>{{ resolvedLabel }}</el-tag>
</template>

<script setup name="StatusTag">
/**
 * 全站唯一的「状态 → 语义色」出口：UP/成功=绿、DOWN/失败=红、存疑或配置异常=橙、处理中=蓝。
 *
 * 只覆盖**运行态**这类在各页面含义一致的值。业务码表（渠道 / 卡种 / 设备类型 / 状态字符串
 * 属于某个业务域）不在此处建第二套真源——那些页面继续用自己的码表 util 取标签，
 * 再显式传 `type`。
 */
const props = defineProps({
  /** 原始状态值（后端字段），按内置运行态表取色与文案。 */
  value: { type: [String, Number], default: '' },
  /** 显式覆盖语义色；业务域状态由调用方决定，避免在这里猜。 */
  type: { type: String, default: '' },
  /** 显式覆盖文案；用于已本地化的业务状态。 */
  label: { type: String, default: '' },
  effect: { type: String, default: 'light' }
})

const RUNTIME = {
  UP: { type: 'success', label: 'UP' },
  DOWN: { type: 'danger', label: 'DOWN' },
  UNKNOWN: { type: 'warning', label: 'UNKNOWN' },
  SUCCESS: { type: 'success', label: '成功' },
  FAILED: { type: 'danger', label: '失败' },
  PENDING: { type: 'primary', label: '待执行' },
  RUNNING: { type: 'primary', label: '执行中' },
  ONLINE: { type: 'success', label: '在线' },
  OFFLINE: { type: 'danger', label: '离线' },
  ACTIVE: { type: 'success', label: '生效中' },
  RELEASING: { type: 'warning', label: '解除中' },
  EXPIRED: { type: 'info', label: '已过期' }
}

const resolved = computed(() => RUNTIME[String(props.value).toUpperCase()] || null)
const resolvedType = computed(() => props.type || resolved.value?.type || 'info')
const resolvedLabel = computed(() => props.label || resolved.value?.label || props.value || '-')
</script>
