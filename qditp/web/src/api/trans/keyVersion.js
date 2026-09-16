import request from '@/utils/request'

/** 各密钥域当前版本汇总（只读，不含密钥材料）。 */
export function listKeyVersions() {
  return request({ url: '/key-server/page/key/versions', method: 'get' })
}
