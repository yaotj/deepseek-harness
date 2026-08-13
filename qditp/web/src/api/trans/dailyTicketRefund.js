import request from '@/utils/request'

/** 分页查询可退款的日票订单。 */
export function listDailyTicketRefundOrders(params) {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/orders',
    method: 'get',
    params
  })
}

/** 对指定日票订单发起退款。 */
export function requestDailyTicketRefund(orderNo) {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/request',
    method: 'post',
    // 服务端固定订单类型为日票，页面只提交业务主键。
    data: { orderNo }
  })
}

/** 查询支付平台支付结果，并同步日票订单支付状态。 */
export function queryDailyTicketPay(orderNo) {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/pay-query',
    method: 'post',
    // 服务端固定订单类型为日票，页面只提交业务主键。
    data: { orderNo }
  })
}

/** 查询支付平台的日票退款处理结果。 */
export function queryDailyTicketRefund(orderNo) {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/query',
    method: 'post',
    // 服务端固定订单类型为日票，页面只提交业务主键。
    data: { orderNo }
  })
}

/** 使用原退款单号重试支付平台退款。 */
export function retryDailyTicketRefund(orderNo) {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/retry',
    method: 'post',
    // 服务端先查询退款结果，再以同一退款单号重试，防止形成重复退款。
    data: { orderNo }
  })
}

/** 分页查询日票退款处理记录。 */
export function listDailyTicketRefundRecords(params) {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/records',
    method: 'get',
    params
  })
}
