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

/**
 * 生成一个「含今天在内共 days 天」的查询区间：起点是那天的 00:00:00，终点是当前时刻。
 *
 * @param {number} days 天数，须为正整数
 * @param {string} valueFormat el-date-picker 的 value-format
 * @returns {string[]} [开始, 结束]
 */
export function lastDaysRange(days, valueFormat = 'YYYY-MM-DD') {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (days - 1), 0, 0, 0)
  return [format(start, valueFormat), format(now, valueFormat)]
}

/** 区间是否为空（空＝不限时间，两端都不传）。 */
export function isEmptyRange(value) {
  return !Array.isArray(value) || value.length === 0 || !value[0] || !value[1]
}

/**
 * 把区间写成一行可读文案，供「当前范围」提示与空状态引导复用。
 *
 * 只做字符串裁剪，不经过 `new Date(字符串)`——区间值本身就是 value-format 的产物，
 * 再解析一次会被浏览器时区/非 ISO 分隔符改写。
 *
 * @param {string[]} value 区间
 * @param {string} valueFormat 仅用于判断是否显示到时分秒
 * @returns {string} 形如 `11-04 00:00 ~ 11-04 14:23`，空区间为 `全部`
 */
export function describeRange(value, valueFormat = 'YYYY-MM-DD') {
  if (isEmptyRange(value)) return '全部'
  const withTime = /HH|mm|ss/.test(valueFormat)
  const label = (text) => {
    const [datePart = '', timePart = ''] = String(text).split(/[ T]/)
    const day = datePart.slice(5)
    return withTime && timePart ? `${day} ${timePart.slice(0, 5)}` : day
  }
  return `${label(value[0])} ~ ${label(value[1])}`
}
