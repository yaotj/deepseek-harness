import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'

/**
 * 前端将 JSON 数据导出为真正的 .xlsx 文件（无需后端参与）。
 *
 * <p>适用于「查询结果已全量在前端」的导出场景（如综管台各类查询页）。
 * 若后端已通过统一导入导出框架 {@code /common/importer/export} 提供导出，
 * 应优先走 {@code proxy.download(...)}；本工具仅用于无后端导出端点、或
 * 希望直接导出当前页面已加载数据的场景。
 *
 * @param {Array<object>} rows    数据行，键须与 headers[].key 对应
 * @param {Array<{header: string, key: string}>} headers 列定义（数组顺序即导出列顺序）
 * @param {string} filename 文件名（不含扩展名），如 `itp用户查询_2026-11-02`
 */
export function exportToExcel(rows, headers, filename) {
  if (!rows || !rows.length) {
    throw new Error('没有可导出的数据')
  }
  const data = rows.map((row) => {
    const obj = {}
    headers.forEach((h) => {
      const v = row[h.key]
      obj[h.header] = v === null || v === undefined ? '' : v
    })
    return obj
  })

  const worksheet = XLSX.utils.json_to_sheet(data, {
    header: headers.map((h) => h.header)
  })
  // 按列标题估算列宽，避免中文被挤压
  const colWidths = headers.map((h) => ({
    wch: Math.max(10, h.header.length * 2 + 4)
  }))
  worksheet['!cols'] = colWidths

  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Sheet1')
  const wbout = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' })
  saveAs(new Blob([wbout], { type: 'application/octet-stream' }), `${filename}.xlsx`)
}
