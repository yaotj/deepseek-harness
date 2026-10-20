/**
 * face-pay 受理渠道 / 设备类型码表（唯一真源）。
 *
 * 码值域 = `CHANNEL`，`F2F_ORDER.CHANNEL` 与 `F2F_DEVICE_STATUS.CHANNEL` 同域，
 * 受 `CK_F2F_ORDER_CHANNEL CHECK (CHANNEL IN ('01','02','03'))` 约束，与后端 `F2fChannel` 逐条对应。
 *
 * **同一码值域有两个展示口，别混用**：
 * 1. **受理渠道**（`F2F_CHANNEL_LABELS` + `f2fChannelLabel`）—— 三值齐全，走
 *    `utils/codeLabel.js` 的统一约定输出 `编码-字典值`（如 `02-TVM`）。用于订单等业务行，
 *    那里的 `CHANNEL` 可能是 `01`（APP 下单，不是设备）。
 * 2. **设备类型**（`F2F_DEVICE_TYPE_LABELS` + `f2fDeviceTypeLabel`）—— **只收设备**，
 *    `01` APP 不是设备故不在表内（设备心跳页实测 21 台只有 `02`/`03`）；
 *    展示为**纯字典值**（`TVM` / `BOM`，不带编码前缀），业主 2026-10-15 选定。
 *
 * 两表的值字符串相同（`TVM` / `BOM`）但**用途与格式不同**，不是可以互换的同义词：
 * 设备类型的表外编码（如 `01`）原样显示编码，**不臆造名称**。
 *
 * NEVER 并进 `utils/payChannel.js` 或 `utils/cardType.js`：`02`/`03` 在三张表里含义完全不同
 * （这里是 TVM/BOM，支付渠道是 03-支付宝/04-微信，卡种是 02-后付费二维码/03-HCE卡），
 * 合并即把设备说成卡种。同类陷阱见 IT-007 与 `utils/payChannel.js` 顶部注释。
 */
import { formatCodeLabel } from '@/utils/codeLabel'

/** 受理渠道字典（三值齐全）。 */
export const F2F_CHANNEL_LABELS = {
  '01': 'APP',
  '02': 'TVM',
  '03': 'BOM'
}

/** 设备类型字典（只收设备；`01` APP 不是设备，刻意不在表内）。 */
export const F2F_DEVICE_TYPE_LABELS = {
  '02': 'TVM',
  '03': 'BOM'
}

/**
 * 受理渠道展示（`编码-字典值`，走 `codeLabel` 统一约定）。
 *
 * @param {*} code 渠道编码
 * @returns {string} `编码-字典值` / 表外编码原样 / `-`
 */
export function f2fChannelLabel(code) {
  return formatCodeLabel(code, F2F_CHANNEL_LABELS)
}

/**
 * 设备类型展示（**纯字典值**，不带编码前缀；业主 2026-10-15 选定）。
 *
 * 归一化口径与 `codeLabel.js` 对齐（`trim()`，先按原值查、再按大写查）。
 *
 * @param {*} code 渠道编码
 * @returns {string} 字典值（`TVM` / `BOM`）/ 表外编码原样 / `-`
 */
export function f2fDeviceTypeLabel(code) {
  const raw = code == null ? '' : String(code).trim()
  if (!raw) return '-'
  return F2F_DEVICE_TYPE_LABELS[raw] ?? F2F_DEVICE_TYPE_LABELS[raw.toUpperCase()] ?? raw
}
