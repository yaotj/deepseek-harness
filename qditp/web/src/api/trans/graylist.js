import request from '@/utils/request'

// 本页数据源为 blacklist-server 的 GRAYLIST / GRAYLIST_OPERATE_LOG（观察/预警名单，综管台接口）。
// 灰名单不参与乘车拦截、不渠道同步，写操作即删主表行 + 记流水（单阶段，区别于黑名单两阶段解除）。

/** 分页查询灰名单。 */
export function listGraylists(params) {
  return request({
    url: '/blacklist-server/page/graylist',
    method: 'get',
    params
  })
}

/** 进灰（发起方服务端定死 09 运维手工；已在灰视为幂等成功）。 */
export function addGraylist(data) {
  return request({
    url: '/blacklist-server/page/graylist',
    method: 'post',
    data
  })
}

/** 移出灰名单并记流水。 */
export function removeGraylist(cardId, params) {
  return request({
    url: `/blacklist-server/page/graylist/${encodeURIComponent(cardId)}`,
    method: 'delete',
    params
  })
}

/** 灰名单操作流水分页（cardId / operateType 可选）。 */
export function listGraylistOperateLogs(params) {
  return request({
    url: '/blacklist-server/page/graylist/operate-logs',
    method: 'get',
    params
  })
}
