/**
 * 设备管理模式码表（唯一真源）。
 *
 * 码值域 = 业主自定义的设备管理模式，存 `TBL_DEVICE_INFO.MANAGE_MODE`，
 * 受 `CK_TBL_DEVICE_INFO_MODE CHECK (MANAGE_MODE IN ('01','02'))` 约束。
 *
 * **NEVER 并进 `utils/payChannel.js` / `utils/cardType.js` / `utils/f2fChannel.js`**：
 * `01`/`02` 在各码值空间含义完全不同（支付渠道 01-APP、卡种 02-后付费二维码、
 * F2F 设备类型 02-TVM），这里是「正常使用 / 节能关机」，合并即张冠李戴。
 *
 * 与 ACC 侧 `manageLevel`（存 `ACC_MANAGE_LEVEL` 列）也是**两个空间**：
 * 那是 ACC 的管理级别、同步字段，码值清单待 ACC 提供，页面只透出编码不臆造名称。
 */
export const deviceManageModeOptions = [
  { value: '01', label: '正常使用', tag: 'success' },
  { value: '02', label: '节能关机', tag: 'warning' }
]

export const deviceManageModeMap = Object.fromEntries(
  deviceManageModeOptions.map((item) => [item.value, item])
)

/** 表外编码原样显示编码，不臆造名称（同 f2fChannel.js 设备类型表口径）。 */
export function deviceManageModeLabel(value) {
  return deviceManageModeMap[value]?.label || value || '-'
}
