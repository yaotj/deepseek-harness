import request from '@/utils/request'

// 本页数据源为 collect-pay-server 的 TBL_BOM_ORDER_PAY（综管台只读接口，走 web 侧 nginx 前缀）。
// transType 口径：01 购票发售 / 22 充值（collect-pay BomBusinessCodeEnum）。

/** 分页查询 BOM 非现金订单。要求订单标识或完整下单时间范围。 */
export function listBomOrders(params) {
  return request({
    url: '/collect-pay-server/page/bom/orders',
    method: 'get',
    params
  })
}
