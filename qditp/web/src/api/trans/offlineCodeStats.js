import request from '@/utils/request'

/** 离线码交易统计：按车站分组笔数与独立卡数，日期窗必填（yyyy-MM-dd）。 */
export function listOfflineCodeStats(params) {
  return request({ url: '/gate-txn-pay-server/page/gate-txn-pay/offline-stats', method: 'get', params })
}
