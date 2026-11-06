/**
 * 卡池批次状态码表（`LOGIC_CARD_POOL_BATCH.STATUS`）。
 *
 * <p><b>与 `orderStatusLabels.js` 里的四张表是不同码值空间</b>：本表用 `FAILED`（不是 `FAIL`）、
 * 有 `CREATED`/`REQUESTING`/`DOWNLOADING`/`IMPORTING` 四个过程态，MUST NOT 合并。
 *
 * <p>取值与推进规则真源：`docs/business/card-pool.md` §状态机
 * （「CREATED → REQUESTING → DOWNLOADING → IMPORTING → SUCCESS，任一环节异常置 FAILED；
 * FAILED 且已有文件名的批次可重试回 DOWNLOADING」）与 `CardPoolServiceImpl` 的写入点。
 * 其中 `REQUESTING` 是已知缺口：表示已向 ACC 发出申请但未拿到文件名，进程中断后无法判断
 * 对端是否已生成批次，**无法自动续做**。
 *
 * <p>展示口径沿用 `utils/codeLabel.js` 的「编码-中文」。
 */
import { formatCodeLabel } from './codeLabel'

const CARD_POOL_BATCH_STATUS_LABELS = {
  CREATED: '批次已创建',
  REQUESTING: '已申请待取文件名',
  DOWNLOADING: '文件下载中',
  IMPORTING: '卡号导入中',
  SUCCESS: '导入完成',
  FAILED: '失败'
}

export function cardPoolStatusLabel(code) {
  return formatCodeLabel(code, CARD_POOL_BATCH_STATUS_LABELS)
}
