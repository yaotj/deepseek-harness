import request from '@/utils/request'

/** 分页查询黑名单。 */
export function listBlacklists(params) {
  return request({
    url: '/blacklist-server/page/blacklist',
    method: 'get',
    params
  })
}

/** 新增黑名单。 */
export function addBlacklist(data) {
  return request({
    url: '/blacklist-server/page/blacklist',
    method: 'post',
    data
  })
}

/** 移除指定卡ID的黑名单。 */
export function delBlacklist(cardId) {
  return request({
    url: `/blacklist-server/page/blacklist/${encodeURIComponent(cardId)}`,
    method: 'delete'
  })
}
