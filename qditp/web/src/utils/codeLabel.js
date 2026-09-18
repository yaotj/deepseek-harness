/**
 * 编码翻译的统一展示规则。
 *
 * 约定：能翻译就显示 `编码-翻译`，翻译不到就只显示 `编码`（不再拼"渠道 XX"之类的兜底文案），
 * 空值统一显示 `-`。所有码值展示都走这里，避免各页面各写一套格式。
 */

/**
 * 已知编码和名称时直接拼。
 *
 * @param {*} code 原始编码
 * @param {*} name 翻译结果
 * @returns {string} `编码-翻译` / `编码` / `-`
 */
export function formatCodeName(code, name) {
  const raw = code == null ? '' : String(code).trim()
  if (!raw) return '-'
  const text = name == null ? '' : String(name).trim()
  return text ? `${raw}-${text}` : raw
}

/**
 * 从码表里查翻译后展示。
 *
 * @param {*} code 原始编码
 * @param {Record<string, string>} labels 码值到名称的映射
 * @returns {string} `编码-翻译` / `编码` / `-`
 */
export function formatCodeLabel(code, labels) {
  const raw = code == null ? '' : String(code).trim()
  if (!raw) return '-'
  return formatCodeName(raw, labels?.[raw] ?? labels?.[raw.toUpperCase()])
}
