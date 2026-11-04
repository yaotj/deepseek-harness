import request from '@/utils/request'

// 本页数据源为 face-pay-server 的 SUPPLEMENT_ORDER（IF8A-26 在线补款单，face-pay-server 为 owner，综管台只读接口）。

/** 分页查询在线补款单。要求单号/卡号/用户标识或完整交易日期范围。 */
export function listSupplementOrders(params) {
  return request({
    url: '/face-pay-server/page/face-pay/supplement-orders',
    method: 'get',
    params
  })
}
