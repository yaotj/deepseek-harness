/**
 * 查询区间的"当天"默认值。
 *
 * 规则（运营侧统一约定）：
 * - 单个日期 / 纯日期区间（value-format 只到日）→ 今天 ~ 今天
 * - 带时分秒的区间 → 今天 00:00:00 ~ 当前时刻
 *
 * 两种规则统一由 value-format 推导：格式里带 HH/mm/ss 就补时间，否则只出日期，
 * 因此各页面只需把自己的 value-format 原样传进来即可。
 */

const TOKENS = /YYYY|MM|DD|HH|mm|ss/g

function pad(value) {
  return String(value).padStart(2, '0')
}

function format(date, pattern) {
  const parts = {
    YYYY: date.getFullYear(),
    MM: pad(date.getMonth() + 1),
    DD: pad(date.getDate()),
    HH: pad(date.getHours()),
    mm: pad(date.getMinutes()),
    ss: pad(date.getSeconds())
  }
  return pattern.replace(TOKENS, (token) => parts[token])
}

/**
 * 生成一个从今天 00:00:00 开始的查询区间。
 *
 * @param {string} valueFormat el-date-picker 的 value-format，默认 'YYYY-MM-DD'
 * @returns {string[]} [开始, 结束]；仅日期格式时两端都是今天
 */
export function defaultTodayRange(valueFormat = 'YYYY-MM-DD') {
  const now = new Date()
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
  return [format(startOfDay, valueFormat), format(now, valueFormat)]
}
