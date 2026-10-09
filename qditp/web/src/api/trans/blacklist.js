import request from '@/utils/request'

/**
 * 分页查询黑名单。
 *
 * status 与 channelSyncStatus 都是可选筛选，不传时两种行状态都返回
 * （解除中的行仍算黑名单，运营需要看得到）。
 */
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

/**
 * 发起解除黑名单。
 *
 * 后端是两阶段解除：本次调用只把该行置为「解除中」并落操作日志，
 * 解除通知推达支付宝之后才真正搬历史表、删主表行。
 * 因此调用成功不等于卡已放行，列表里那行会停在「解除中」。
 *
 * releaseReason / releaseBy 会被暂存到主表、阶段二随行搬进历史表，
 * 不传就永久丢失审计信息，所以两者都要送。
 */
export function releaseBlacklist(cardId, params) {
  return request({
    url: `/blacklist-server/page/blacklist/${encodeURIComponent(cardId)}`,
    method: 'delete',
    params
  })
}

/**
 * 触发解除通知补偿，重推所有「解除中且通知未推达」的行。
 *
 * 这是全表补偿、不是按行重推：后端按 STATUS='RELEASING' + 同步状态在
 * PENDING / FAILED 白名单内扫表，与 web-admin 定时任务走的是同一个端点。
 */
export function compensateReleaseNotify(limit) {
  return request({
    url: '/blacklist-server/internal/blacklist/channel-sync/compensate-release',
    method: 'post',
    params: { limit }
  })
}

/** 触发加黑通知补偿，重推所有「生效中且通知未推达」的行。 */
export function compensateAddNotify(limit) {
  return request({
    url: '/blacklist-server/internal/blacklist/channel-sync/compensate-add',
    method: 'post',
    params: { limit }
  })
}
