import request from '@/utils/request'

// 本页数据源为 pay-sign-server 的 PAY_REFUND_DETAIL（退款明细，pay-sign-server 为 owner，综管台只读接口）。

/**
 * 按一批原支付订单号取退款明细（扣费明细合并页回填「退款状态 / 退款金额」用）。
 *
 * <p><b>为什么走这个端点而不是 `pay-sign-server/ci/app/queryPayTxnBatch`</b>：
 * 后者读 `PAY_TXN_DETAIL`，而 2026-11-06 现网实测闸机扣费成功的 287,906 行里只有 71 行
 * 在那张表有记录，用它做这列会对运营真正关心的单永远显示空；
 * `PAY_REFUND_DETAIL.ORDER_NO` 才是与 `GATE_TXN_PAY.ORDER_NO` 同域的键（49 笔退款全挂在扣费成功的单上）。
 *
 * <p>返回扁平列表、不分页：同一原单可能有多行退款（历史上被重复提交过），分组口径由调用方决定。
 *
 * @param {string[]} orderNos 原支付订单号数组（服务端上限 200）
 */
export function listRefundsByOrders(orderNos) {
  return request({
    url: '/pay-sign-server/page/pay-refund/by-orders',
    method: 'get',
    params: { orderNos }
  })
}
