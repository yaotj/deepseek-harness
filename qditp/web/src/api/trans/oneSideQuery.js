import request from '@/utils/request'

// 本页数据源为 gate-txn-pay-server 的 GATE_TXN_PAY（ORDER_EXP_TYPE 单边入/出，综管台只读接口）。
// 日期口径 TXN_DATE 存储格式 yyyyMMdd，后端 normalizeTxnDate 兼容 yyyy-MM-dd 输入。

/** 分页查询单边交易（默认圈 ORDER_EXP_TYPE 1/2，可指定 1~5）。要求订单标识或完整日期范围。 */
export function listOneSide(params) {
  return request({
    url: '/gate-txn-pay-server/page/gate-txn-pay/one-side',
    method: 'get',
    params
  })
}
