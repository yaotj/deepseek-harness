import { formatCodeLabel, formatCodeName } from '@/utils/codeLabel'
import { CARD_TYPE_LABELS } from '@/utils/cardType'

/**
 * 支付渠道（签约渠道）码表 —— **唯一真源**。
 *
 * 与后端 `pay-sign-server` 的 `PaymentVendorEnum` 逐条一一对应（2026-10-15 逐项核对）。
 * 收录页面：用户查询-ITP「默认渠道」、签约渠道管理「支付渠道」、用户查询-扣费信息「签约渠道」。
 *
 * 约定：
 * - 保持上送原值做码值匹配，含 `0B` / `0C` 这类 16 进制写法，**不做进制转换**；
 * - 展示走 {@link formatCodeLabel}，即 `编码-中文`；查不到时只显示编码，NEVER 臆造名称。
 *
 * NEVER 把本表与「发卡渠道」混用：`trans/user/alipay` 页的 `issueChannelLabels`
 * （`01 正常渠道` / `07 支付宝`）取自后端 `IssueChannelCodeEnum`，码值空间不同。
 */
export const PAY_CHANNEL_LABELS = {
  '03': '支付宝',
  '04': '微信',
  '05': '支付宝出行',
  '06': '龙支付',
  '0601': '招商银行',
  '0602': '中国银行',
  '08': '建行数币',
  '0801': '中行数币',
  '0802': '邮储数币',
  '0803': '交行数币',
  '0B': '钱包',
  '0C': '数币APP'
}

/**
 * 把支付渠道编码翻成 `编码-中文`。
 *
 * @param {*} code 渠道编码原值
 * @returns {string} `03-支付宝` / 查不到时原样编码 / 空值 `-`
 */
export function payChannelLabel(code) {
  return formatCodeLabel(code, PAY_CHANNEL_LABELS)
}

/** 与 `formatCodeLabel` 内部一致的大小写兜底（`0B` / `0b` 都要能命中）。 */
function lookup(labels, code) {
  return labels[code] ?? labels[code.toUpperCase()]
}

/**
 * 「签约渠道」列专用：**先按支付渠道翻，翻不到再按卡种翻**。
 *
 * 为什么需要两层：`GATE_TXN_PAY.SIGN_CHANNEL_CODE` 实测会落**两类码值**——
 * 绝大多数是支付渠道（`03` 支付宝 / `04` 微信 …），但少数行是 **2 位卡种码**
 * （`12` 一日票 / `15` 多日计次票）。实测分布与出处见 `docs/tracking/IT-007-扣费信息签约渠道显示编码.md`。
 *
 * **两张表 MUST 保持独立、NEVER 合并**：码值空间不同（例如 `04` 在支付渠道是「微信」，
 * 在卡种表里没有这个码），合并会造出错误译名。两层都查不到时原样显示编码，不臆造。
 *
 * @param {*} code `SIGN_CHANNEL_CODE` 原值
 * @returns {string} `03-支付宝` / `12-一日票` / 原样编码 / 空值 `-`
 */
export function signChannelLabel(code) {
  const raw = code == null ? '' : String(code).trim()
  if (!raw) return '-'
  return formatCodeName(raw, lookup(PAY_CHANNEL_LABELS, raw) ?? lookup(CARD_TYPE_LABELS, raw))
}
