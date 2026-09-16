import request from '@/utils/request'

// 数据源自 2026-09-15（ADR-D85）起由 collect-pay-server 切至 face-pay-server（F2F_ORDER）。
// URL 后半段 /page/face-pay/orders 两服务一字不差，只换前缀；NEVER 退回 /collect-pay-server。

/** 分页查询 TVM 当面付订单。 */
export function listFacePayOrders(params) {
  return request({
    url: '/face-pay-server/page/face-pay/orders',
    method: 'get',
    params
  })
}

/** 对支付成功的 TVM 当面付订单发起全额退款，退款金额由后台从订单计算。 */
export function requestFacePayRefund(orderNo, data) {
  return request({
    url: `/face-pay-server/page/face-pay/orders/${encodeURIComponent(orderNo)}/refund`,
    method: 'post',
    data
  })
}
