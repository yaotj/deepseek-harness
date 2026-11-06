/**
 * 对账文件类别码表。
 *
 * 取值真源是后端两处，**NEVER 在这里新增代码里没有的类别**：
 * - ITP 侧五类：`recon-server` 的 `ReconFtpService` 投递校验正则
 *   `ITP\.(EXP|PAY|BUS|DETAIL|TXNDIFF)\.\d{8}`，枚举定义见
 *   `model/.../recon/ReconFileTypeEnum.java`，中文含义取自 `docs/business/recon.md`。
 * - 渠道日账单：`recon.thirdparty.bill-file-pattern` 默认 glob `reconciliation-bill-*.csv`，
 *   在上传记录里归成合成类别码 `BILL`（它不属于 ITP.* 命名族）。
 *
 * 展示口径沿用 `utils/codeLabel.js` 的「编码-中文」，翻译不到只显示编码。
 */
import { formatCodeLabel } from './codeLabel'

export const RECON_FILE_TYPE_LABELS = {
  EXP: '单边交易明细',
  PAY: '统计汇总',
  BUS: '商业优惠汇总',
  DETAIL: '多日计次票明细',
  TXNDIFF: '对账差异明细',
  BILL: '渠道日账单'
}

export function reconFileTypeLabel(code) {
  return formatCodeLabel(code, RECON_FILE_TYPE_LABELS)
}

/**
 * 文件名命名约定（与后端 `ReconUploadFileController` 的两条正则同一口径）。
 *
 * 前端只做即时提示，**服务端才是裁判**：这里放过、那里拒掉是正常兜底，不是 bug。
 */
const ITP_NAME = /^ITP\.(EXP|PAY|BUS|DETAIL|TXNDIFF)\.(\d{8})(\.[A-Za-z0-9]{1,10})?$/
const BILL_NAME = /^reconciliation-bill-(\d{8})(\.[A-Za-z0-9]{1,10})?$/

/**
 * 解析对账文件名。
 *
 * <p>后缀 MUST 从正则捕获组取，NEVER 用 `lastIndexOf('.')`：
 * `ITP.PAY.20261105` 里最后一个点后面是账期不是后缀，按点切会把 `20261105` 当扩展名误判成不合规。
 *
 * @param {string} name 客户端文件名
 * @returns {{category: string, reconDate: string, extension: string}|null} 不合规返回 null
 */
export function parseReconFileName(name) {
  const raw = (name || '').trim()
  const itp = ITP_NAME.exec(raw)
  if (itp) return { category: itp[1], reconDate: itp[2], extension: normalizeExtension(itp[3]) }
  const bill = BILL_NAME.exec(raw)
  if (bill) return { category: 'BILL', reconDate: bill[1], extension: normalizeExtension(bill[2]) }
  return null
}

/** 无后缀时返回空串；有则统一小写，与后端 file_type 落库口径一致。 */
function normalizeExtension(group) {
  return group ? group.slice(1).toLowerCase() : ''
}

/** 给运营看的格式提示，出错时原样带上。 */
export const RECON_NAME_HINT =
  'ITP.EXP|PAY|BUS|DETAIL|TXNDIFF.yyyyMMdd（如 ITP.PAY.20261105）或 reconciliation-bill-yyyyMMdd（如 reconciliation-bill-20261105.csv），后缀可选'
