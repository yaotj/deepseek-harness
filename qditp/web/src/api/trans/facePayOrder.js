import request from '@/utils/request'

// 本页数据源为 face-pay-server 的 F2F_ORDER（综管台只读接口，走 web 侧 nginx 前缀）。

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
