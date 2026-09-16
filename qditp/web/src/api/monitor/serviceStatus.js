import request from '@/utils/request'

/** 各微服务运行状态探活聚合。 */
export function listServiceStatus() {
  return request({ url: '/web-server/monitor/service-status/list', method: 'get' })
}
