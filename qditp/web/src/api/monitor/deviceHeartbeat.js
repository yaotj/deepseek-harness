import request from '@/utils/request'

// 本页数据源为 face-pay-server 的 F2F_DEVICE_STATUS（设备最新心跳，只读接口，走 web 侧 nginx 前缀）。

/**
 * 分页查询 TVM/BOM 设备最新心跳状态。
 *
 * @param {object} params channel / onlineFlag / deviceId / pageNum / pageSize
 * @returns {Promise} 响应 `data` 含 `list`、`total` 与全表口径的 `summary`
 */
export function listDeviceHeartbeat(params) {
  return request({
    url: '/face-pay-server/page/face-pay/devices',
    method: 'get',
    params
  })
}
