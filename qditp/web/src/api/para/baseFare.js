import request from '@/utils/request'

/** 分页查询当前费率版本的基础票价。 */
export function listBaseFares(params) {
  return request({
    url: '/para-server/page/base-fare',
    method: 'get',
    params
  })
}

/** 查询当前路网版本的车站选项，可按线路过滤。 */
export function listBaseFareStations(params) {
  return request({
    url: '/para-server/page/base-fare/stations',
    method: 'get',
    params
  })
}

/** 查询当前路网版本的线路选项。 */
export function listBaseFareLines() {
  return request({
    url: '/para-server/page/base-fare/lines',
    method: 'get'
  })
}
