import request from '@/utils/request'

/** 查询单程票最大购买张数。 */
export function getSingleTicketPurchaseLimit() {
  return request({
    url: '/para-server/page/single-ticket-purchase-limit',
    method: 'get'
  })
}

/** 更新单程票最大购买张数。 */
export function updateSingleTicketPurchaseLimit(data) {
  return request({
    url: '/para-server/page/single-ticket-purchase-limit',
    method: 'put',
    data
  })
}
