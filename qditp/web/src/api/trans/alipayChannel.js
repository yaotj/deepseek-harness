import request from '@/utils/request'

// 支付宝渠道管理页数据源：alipay-pay-sign-server 的 ALIPAY_SIGN_INFO（签约用户）/
// ALIPAY_REFUND_TXN_DETAIL（渠道退款记录），综管台只读接口（走 web 侧 nginx 前缀，NodePort 30022）。

/** 签约用户分页（用户标识 / 卡号 / 签约时间窗至少一项必填，后端强校验）。 */
export function listAlipaySignedUsers(params) {
  return request({
    url: '/alipay-pay-sign-server/page/alipay/signed-users',
    method: 'get',
    params
  })
}

/** 渠道退款记录分页（退款单号 / 原订单号 / 交易日期窗至少一项必填，后端强校验；日期窗 yyyy-MM-dd）。 */
export function listAlipayRefundRecords(params) {
  return request({
    url: '/alipay-pay-sign-server/page/alipay/refund-records',
    method: 'get',
    params
  })
}
