<template>
  <div class="pwd-strength">
    <div class="pwd-strength__bar">
      <span
        v-for="seg in 3"
        :key="seg"
        class="pwd-strength__seg"
        :class="seg <= strength.level ? `pwd-strength__seg--lv${strength.level}` : ''"
      ></span>
      <span v-if="strength.text" class="pwd-strength__text" :style="{ color: strength.color }">{{ strength.text }}</span>
    </div>
    <ul class="pwd-strength__chars">
      <li v-for="item in chars" :key="item.key" :class="{ 'is-pass': item.passed }">
        <i class="pwd-strength__dot"></i>{{ item.label }}
      </li>
    </ul>
    <p class="pwd-strength__hint">{{ policyHint }}</p>
  </div>
</template>

<script setup name="PasswordStrength">
import { checkPasswordChars, getPasswordStrength, PASSWORD_POLICY_HINT } from '@/utils/password'

const props = defineProps({
  /** 待评估的密码明文；空串表示尚未输入 */
  password: {
    type: String,
    default: ''
  }
})

const chars = computed(() => checkPasswordChars(props.password))
const strength = computed(() => getPasswordStrength(props.password))
const policyHint = PASSWORD_POLICY_HINT
</script>

<style scoped>
/* 本组件自带样式：其 DOM 全部由本组件模板渲染，故 scoped 生效。
   （对比 Sidebar/index.vue 的教训：作用于「子组件渲染出来的元素」的 scoped 规则才是死代码。） */
.pwd-strength {
  margin-top: 6px;
}

.pwd-strength__bar {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pwd-strength__seg {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background-color: var(--el-border-color-lighter, #ebeef5);
  transition: background-color 0.2s;
}

.pwd-strength__seg--lv1 {
  background-color: var(--el-color-danger, #f56c6c);
}

.pwd-strength__seg--lv2 {
  background-color: var(--el-color-warning, #e6a23c);
}

.pwd-strength__seg--lv3 {
  background-color: var(--el-color-success, #67c23a);
}

.pwd-strength__text {
  flex: none;
  width: 1.5em;
  text-align: right;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}

.pwd-strength__chars {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 14px;
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-placeholder, #a8abb2);
}

.pwd-strength__chars li {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pwd-strength__chars li.is-pass {
  color: var(--el-color-success, #67c23a);
}

.pwd-strength__dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background-color: currentColor;
}

.pwd-strength__hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-placeholder, #a8abb2);
}
</style>
