import request from '@/utils/request'

// 本页数据源为 collect-pay-server 的 TBL_TVM_ORDER_TOPUP ＋ TBL_TVM_Topup_Notiy（综管台只读接口，走 web 侧 nginx 前缀）。

/** 分页查询 TVM 充值订单（带最新充值回执态）。要求订单标识或完整下单时间范围。 */
export function listTvmTopupOrders(params) {
  return request({
    url: '/collect-pay-server/page/tvm/topup-orders',
    method: 'get',
    params
  })
}
