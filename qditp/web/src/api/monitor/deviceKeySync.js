import request from '@/utils/request'

/**
 * 分页查询设备最新密钥同步状态。
 *
 * 源表：key-server 的 COM_DEVICE_SYN_KEY（按 DEVICE_ID MERGE，仅保留最新一条）。
 *
 * @param {object} params deviceId / stationCode / status / pageNum / pageSize
 * @returns {Promise} 响应 data 含 { list, total }
 */
export function listDeviceKeySync(params) {
  return request({
    url: '/key-server/page/key/device-sync/list',
    method: 'get',
    params
  })
}
