import request from '@/utils/request'

// 本页数据源为 face-pay-server 的 F2F_RESULT_REPORT（设备充值回执上报，TOPUP_STATUS=02 存疑，综管台只读接口）。

/** 分页查询 TVM 充值存疑回执。要求订单号或完整接收时间范围。 */
export function listTopupSuspicious(params) {
  return request({
    url: '/face-pay-server/page/face-pay/topup-suspicious',
    method: 'get',
    params
  })
}
