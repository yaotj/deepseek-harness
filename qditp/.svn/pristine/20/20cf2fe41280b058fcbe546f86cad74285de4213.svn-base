import request from '@/utils/request'

/** 分页查询订单自动退款周期配置。 */
export function listOrderRefundCycles(params) {
  return request({
    url: '/para-server/page/order-refund-cycle',
    method: 'get',
    params
  })
}

/** 新增订单自动退款周期配置。 */
export function addOrderRefundCycle(data) {
  return request({
    url: '/para-server/page/order-refund-cycle',
    method: 'post',
    data
  })
}

/** 修改指定票卡类型的订单自动退款周期配置。 */
export function updateOrderRefundCycle(ticketType, data) {
  return request({
    url: `/para-server/page/order-refund-cycle/${encodeURIComponent(ticketType)}`,
    method: 'put',
    data
  })
}

/** 删除指定票卡类型的订单自动退款周期配置。 */
export function delOrderRefundCycle(ticketType) {
  return request({
    url: `/para-server/page/order-refund-cycle/${encodeURIComponent(ticketType)}`,
    method: 'delete'
  })
}
