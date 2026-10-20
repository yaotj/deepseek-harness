/**
 * 综管台密码策略（唯一来源）
 *
 * 2026-10-15 业主口径：长度 8-20；大写字母 / 小写字母 / 数字 / 特殊符号 四类中**至少 3 类**。
 * 强度等级条组件（components/PasswordStrength）、各处 el-form 校验器与提示文案都 MUST 从这里取，
 * NEVER 在页面里重复写正则或文案 —— 否则口径会随页面漂移。
 *
 * 注：utils/validate.js 的 validLowerCase / validUpperCase 是「整串是否全为小写/大写」的判定
 * （/^[a-z]+$/），不是「是否包含」，故此处不复用它们。
 */

export const PASSWORD_MIN_LENGTH = 8

export const PASSWORD_MAX_LENGTH = 20

/** 需命中的字符类数量下限 */
export const PASSWORD_MIN_CLASSES = 3

/** 非法字符，与既有前后端口径一致 */
export const PASSWORD_ILLEGAL_CHARS = /[<>"'|\\]/

/** 四类字符的判定规则；label 同时用于强度条的清单展示 */
const CHAR_RULES = [
  { key: 'upper', label: '大写字母', pattern: /[A-Z]/ },
  { key: 'lower', label: '小写字母', pattern: /[a-z]/ },
  { key: 'digit', label: '数字', pattern: /[0-9]/ },
  { key: 'symbol', label: '特殊符号', pattern: /[^A-Za-z0-9]/ }
]

/** 逐类判定，返回 [{ key, label, passed }] */
export function checkPasswordChars(password) {
  const pwd = password || ''
  return CHAR_RULES.map(item => ({
    key: item.key,
    label: item.label,
    passed: pwd.length > 0 && item.pattern.test(pwd)
  }))
}

/** 已命中的字符类数量 */
export function countPasswordChars(password) {
  return checkPasswordChars(password).filter(item => item.passed).length
}

/**
 * 强度等级：0 = 未输入，1 = 弱，2 = 中，3 = 强
 * 只取「命中类别数」与「长度」两个维度，不引入更多维度以免口径分散。
 */
export function getPasswordStrength(password) {
  const pwd = password || ''
  if (!pwd) {
    return { level: 0, text: '', color: '' }
  }
  const chars = countPasswordChars(pwd)
  const longEnough = pwd.length >= PASSWORD_MIN_LENGTH
  if (chars >= PASSWORD_MIN_CLASSES && longEnough) {
    return { level: 3, text: '强', color: 'var(--el-color-success, #67c23a)' }
  }
  if (chars >= 2) {
    return { level: 2, text: '中', color: 'var(--el-color-warning, #e6a23c)' }
  }
  return { level: 1, text: '弱', color: 'var(--el-color-danger, #f56c6c)' }
}

/** 策略提示文案（强度条与表单提示共用） */
export const PASSWORD_POLICY_HINT = `密码长度 ${PASSWORD_MIN_LENGTH}-${PASSWORD_MAX_LENGTH} 位，且需包含大写字母、小写字母、数字、特殊符号中的至少 ${PASSWORD_MIN_CLASSES} 类`

/**
 * 校验密码是否符合策略。
 * @param {string} password 待校验密码
 * @returns {string|null} 符合返回 null，否则返回错误文案
 */
export function validatePasswordPolicy(password) {
  const pwd = password || ''
  if (!pwd) {
    return '密码不能为空'
  }
  if (PASSWORD_ILLEGAL_CHARS.test(pwd)) {
    return `不能包含非法字符：< > " ' \\ |`
  }
  if (pwd.length < PASSWORD_MIN_LENGTH || pwd.length > PASSWORD_MAX_LENGTH) {
    return `密码长度必须介于 ${PASSWORD_MIN_LENGTH} 和 ${PASSWORD_MAX_LENGTH} 之间`
  }
  const chars = countPasswordChars(pwd)
  if (chars < PASSWORD_MIN_CLASSES) {
    return `密码需包含大写字母、小写字母、数字、特殊符号中的至少 ${PASSWORD_MIN_CLASSES} 类（当前 ${chars} 类）`
  }
  return null
}

/** 直接用于 el-form rules 的校验器 */
export function passwordPolicyValidator(rule, value, callback) {
  const message = validatePasswordPolicy(value)
  if (message) {
    callback(new Error(message))
  } else {
    callback()
  }
}
