import request from '@/utils/request'

/** 注册量统计：按票种分组计数，可选注册日期窗。 */
export function listRegStats(params) {
  return request({
    url: '/account-server/page/user/itp/reg-stats',
    method: 'get',
    params
  })
}
