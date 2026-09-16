import request from '@/utils/request'

/** 批量退超时罚金：圈单查询（日期窗必填，yyyy-MM-dd）。 */
export function listOvertimeRefundable(params) {
  return request({ url: '/gate-txn-pay-server/page/gate-txn-pay/overtime-refundable', method: 'get', params })
}

/** 批量发起超时罚金退款：逐单走单笔链路，单批 ≤ 200 笔。 */
export function batchRefundOvertime(data) {
  return request({ url: '/gate-txn-pay-server/page/gate-txn-pay/batch-refund-overtime', method: 'post', data })
}
