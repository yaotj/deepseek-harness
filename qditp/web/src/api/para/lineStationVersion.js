import request from '@/utils/request'

/** 查询当前生效的各类参数版本（路网拓扑、费率等，一种类型一条记录）。 */
export function listLineStationVersion(params) {
  return request({
    url: '/para-server/page/line-station-version',
    method: 'get',
    params
  })
}
