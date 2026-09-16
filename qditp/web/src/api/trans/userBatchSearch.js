import request from '@/utils/request'

/** 批量导入逻辑卡号查询手机号（单批上限 500 条）。 */
export function batchSearchUsers(cardIds) {
  return request({ url: '/account-server/page/user/itp/batch-search', method: 'post', data: cardIds })
}
