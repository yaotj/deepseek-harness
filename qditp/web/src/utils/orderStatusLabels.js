/**
 * 订单/退款/补款状态码表（英文枚举串 ↔ 中文）。
 *
 * <p><b>四张表是四个互不相通的码值空间</b>，虽然字面值大量重叠（都有 SUCCESS/FAIL/PROCESSING），
 * 但含义由各自的状态机决定，MUST NOT 合并成一张表</b> —— 与 `payChannel.js` / `cardType.js` /
 * `f2fChannel.js` 同一条红线（「同列出现」≠「同一码值空间」）。
 *
 * <p>取值真源（改动前 MUST 先读，NEVER 凭界面下拉里看到的几个值增删）：
 * - 扣费状态：`gate-txn-pay-server/.../constant/DebitStatus.java`（5 值）
 *   + `gate-txn-pay-schema.sql` 列注释（多一个 CLOSED）。
 * - 退款状态：`pay-txn-schema.sql` PAY_REFUND_DETAIL 列注释 6 值 + `PayRefundDetailMapper` 的写入点。
 * - 补款支付状态：`SupplementOrderServiceImpl` / `SupplementPayCenterFlow` 的常量，5 值。
 * - 补款收口状态：`SupplementPayCenterFlow` 常量 + `SupplementOrderLocalWriter`，3 值。
 *
 * <p>展示口径沿用 `utils/codeLabel.js` 的「编码-中文」，翻译不到只显示编码、空值显示 `-`。
 */
import { formatCodeLabel } from './codeLabel'

/**
 * 闸机扣费状态。
 *
 * <p>`CLOSED` 只存在于列注释与前端下拉，`DebitStatus` 枚举没有它、全模块也无写入点
 * （2026-11-06 实测测试库 166.9 万行取值只有 FAIL/SUCCESS/RETRY 三种）—— 保留是为防未来出现，
 * 不要因为它查不到数据就删掉。
 */
const DEBIT_STATUS_LABELS = {
  INIT: '已落单待扣款',
  PROCESSING: '扣款处理中',
  RETRY: '待补扣',
  SUCCESS: '扣款成功',
  FAIL: '扣款失败',
  CLOSED: '关闭'
}

/**
 * 退款状态（PAY_REFUND_DETAIL）。
 *
 * <p>两个值必须按代码原文解释，界面上不能翻成运营想当然的意思：
 * - `CLOSED` 是「回查超龄、我方放弃自动定性、**结果未知**」，
 *   NEVER 写成「退款失败」——那会让运营再发一笔（`PayRefundDetailMapper.exhaustFromQuery` 注释原文）。
 * - `RETRY` 是 2026-09-22 前旧版本遗留、现已不再写入，库里仍有历史行。
 */
const REFUND_STATUS_LABELS = {
  INIT: '退款已受理未发起',
  PROCESSING: '退款处理中',
  SUCCESS: '退款成功',
  FAIL: '退款失败',
  RETRY: '退款待重试(旧版遗留)',
  CLOSED: '结果未知(超龄收口,需人工)'
}

/** 补款单支付状态（SUPPLEMENT_ORDER.PAY_STATUS）。 */
const SUPPLEMENT_PAY_STATUS_LABELS = {
  INIT: '补款已下单',
  PROCESSING: '补款支付中',
  SUCCESS: '补款成功',
  FAIL: '补款失败',
  CLOSED: '补款超时关闭'
}

/**
 * 补款明细收口状态（SUPPLEMENT_ORDER_ITEM.SETTLE_STATUS）。
 *
 * <p>`SUCCESS` **不是**本列合法取值，别按别的表的习惯去补它。
 */
const SETTLE_STATUS_LABELS = {
  PENDING: '待收口',
  SETTLED: '已收口',
  FAILED: '收口失败'
}

export function debitStatusLabel(code) {
  return formatCodeLabel(code, DEBIT_STATUS_LABELS)
}

export function refundStatusLabel(code) {
  return formatCodeLabel(code, REFUND_STATUS_LABELS)
}

export function supplementPayStatusLabel(code) {
  return formatCodeLabel(code, SUPPLEMENT_PAY_STATUS_LABELS)
}

export function settleStatusLabel(code) {
  return formatCodeLabel(code, SETTLE_STATUS_LABELS)
}

/** 筛选下拉用的选项数组：value 是原始码、label 是「编码-中文」。 */
export function debitStatusOptions() {
  return Object.entries(DEBIT_STATUS_LABELS).map(([value, name]) => ({ value, label: `${value}-${name}` }))
}
