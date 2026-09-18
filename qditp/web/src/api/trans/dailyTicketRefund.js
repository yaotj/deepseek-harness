import request from '@/utils/request'

/** 分页查询可退款的日票订单。 */
export function listDailyTicketRefundOrders(params) {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/orders',
    method: 'get',
    params
  })
}

/** 对指定日票/旅游票订单发起退款。 */
export function requestDailyTicketRefund(orderNo, orderType = '1') {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/request',
    method: 'post',
    data: { orderNo, orderType }
  })
}

/** 查询支付平台支付结果，并同步订单支付状态。 */
export function queryDailyTicketPay(orderNo, orderType = '1') {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/pay-query',
    method: 'post',
    data: { orderNo, orderType }
  })
}

/** 查询支付平台的退款处理结果。 */
export function queryDailyTicketRefund(orderNo, orderType = '1') {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/query',
    method: 'post',
    data: { orderNo, orderType }
  })
}

/** 使用原退款单号重试支付平台退款。 */
export function retryDailyTicketRefund(orderNo, orderType = '1') {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/retry',
    method: 'post',
    data: { orderNo, orderType }
  })
}

/** 查询旅游票主单下的子单。 */
export function listTravelTicketSubOrders(orderNo) {
  return request({
    url: `/daily-ticket-server/page/daily-ticket/refund/travel/${orderNo}/sub-orders`,
    method: 'get'
  })
}

/** 运营端对旅游票子单发起部分退款。 */
export function requestTravelTicketSubRefund(data) {
  return request({
    url: '/daily-ticket-server/page/daily-ticket/refund/travel/sub-refund',
    method: 'post',
    data
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
