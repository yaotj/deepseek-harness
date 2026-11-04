import request from '@/utils/request'

/** 各密钥域当前版本汇总（只读，不含密钥材料）。 */
export function listKeyVersions() {
  return request({ url: '/key-server/page/key/versions', method: 'get' })
}

/**
 * 审批某接入方某批次的 AGM 密钥版本（操作型，安全红线）。
 * 只允许 20010 初始化 → 20020 审批通过 / 20030 拒绝；只改审批状态元信息，不生成 / 不返回密钥材料。
 * @param {Object} data { providerId, batchNumber, targetStatus, operator }
 */
export function approveAgmKeyVersion(data) {
  return request({ url: '/key-server/page/key/agm-version/approval', method: 'post', data })
}
