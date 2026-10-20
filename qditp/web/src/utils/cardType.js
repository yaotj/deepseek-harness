import { formatCodeLabel } from '@/utils/codeLabel'

/**
 * 卡种（票种）码表 —— **唯一真源**。
 *
 * 两套键指向同一批票种，故并列在同一张表里：
 * - **2 位键**：APP 入参卡类型，取自后端 `model` 的 `CardTypeMapping.ISSUE_CARD_TYPES`；
 * - **4 位键**：发卡卡类型，取自后端 `CardTypeCodeEnum`。
 *
 * 2026-10-15 由 `trans/user/itp`、`trans/user/alipay`、`trans/user/pay-channel` 三页各一份的
 * **逐字节相同副本**收敛而来（收敛时保持内容不变，确保是纯搬迁）。
 *
 * 已知缺口：`CardTypeCodeEnum` 里还有 `044A 爱山东`，原三份副本都没收，本轮**不顺手加**——
 * 加了会改动三个无关页面的既有显示，属另一件事。
 */
export const CARD_TYPE_LABELS = {
  '02': '后付费二维码',
  '03': 'HCE卡',
  '04': '新HCE卡',
  '11': '员工码',
  '12': '一日票',
  '13': '三日票',
  '14': '七日票',
  '15': '多日计次票',
  '0441': '后付费二维码',
  '0442': 'HCE卡',
  '0443': '新HCE卡',
  '0444': '员工码',
  '0445': '一日票',
  '0446': '三日票',
  '0447': '七日票',
  '0448': '多日计次票'
}

/**
 * 把卡种编码翻成 `编码-中文`。
 *
 * @param {*} code 卡种编码（2 位或 4 位均可）
 * @returns {string} `0445-一日票` / 查不到时原样编码 / 空值 `-`
 */
export function cardTypeLabel(code) {
  return formatCodeLabel(code, CARD_TYPE_LABELS)
}
