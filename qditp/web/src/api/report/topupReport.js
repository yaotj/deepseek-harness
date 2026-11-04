import request from '@/utils/request'

// 数据源为 collect-pay-server 订单表按日聚合（纯查询，走 web 侧 nginx 前缀）。

/** TVM 充值日报表：TBL_TVM_ORDER_TOPUP 按下单日期聚合。 */
export function listTvmTopupDaily(params) {
  return request({
    url: '/collect-pay-server/page/report/tvm-topup-daily',
    method: 'get',
    params
  })
}

/** 非现金充值数据查询：TBL_BOM_ORDER_PAY（TRANS_TYPE=22 充值）按下单日期聚合。 */
export function listNocashTopup(params) {
  return request({
    url: '/collect-pay-server/page/report/nocash-topup',
    method: 'get',
    params
  })
}
