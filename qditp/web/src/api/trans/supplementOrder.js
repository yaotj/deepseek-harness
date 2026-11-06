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

/**
 * 按一批**原扣费订单号**反查补款单（扣费明细合并页回填「补款金额 / 补款支付渠道 / 补款订单号」用）。
 *
 * <p>返回扁平列表、不分页：一个原单可能对应多条补款明细，分组口径由调用方决定。
 * 关联键是 `SUPPLEMENT_ORDER_ITEM.ORIG_ORDER_NO`，实测 51/51 能在 `GATE_TXN_PAY.ORDER_NO` 命中。
 *
 * @param {string[]} origOrderNos 原扣费订单号数组（服务端上限 200）
 */
export function listSupplementsByOrig(origOrderNos) {
  return request({
    url: '/face-pay-server/page/face-pay/supplement-orders/by-orig',
    method: 'get',
    params: { origOrderNos }
  })
}
