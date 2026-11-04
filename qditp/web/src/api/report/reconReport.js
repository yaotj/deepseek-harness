import request from '@/utils/request'

// 报表数据源：recon-server 对账落地表（RECON_TXN_DIFF / RECON_THIRD_BILL / PAY_TXN_DETAIL），综管台只读接口。
// 日期口径：账单日 / 交易日均为 yyyy-MM-dd（后端归一化为 yyyyMMdd）。

/** 对账差异明细分页（「itp平台与渠道对账」口径，payChannel 区分支付宝/微信/银联）。 */
export function listReconTxnDiff(params) {
  return request({
    url: '/recon-server/page/recon/txn-diff',
    method: 'get',
    params
  })
}

/** 渠道账单行分页（「ITP 与渠道对账报表」口径，paymentChannel 区分渠道）。 */
export function listReconThirdBill(params) {
  return request({
    url: '/recon-server/page/recon/third-bill',
    method: 'get',
    params
  })
}

/** 日统计：groupBy=VENDOR（按支付账户+交易日）/ groupBy=HOUR（当天分时，需单日窗）。 */
export function listReconDailyStats(params) {
  return request({
    url: '/recon-server/page/recon/daily-stats',
    method: 'get',
    params
  })
}

/** 业务清分明细：渠道账单按「账单日+支付商户」聚合。 */
export function listReconClearing(params) {
  return request({
    url: '/recon-server/page/recon/clearing',
    method: 'get',
    params
  })
}
