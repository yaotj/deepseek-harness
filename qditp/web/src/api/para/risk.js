import request from '@/utils/request'

// 风险组
export function listRiskGroups(query) {
  return request({ url: '/para-server/page/risk/groups', method: 'get', params: query })
}
export function listRiskGroupOptions() {
  return request({ url: '/para-server/page/risk/groups/options', method: 'get' })
}
export function addRiskGroup(data) {
  return request({ url: '/para-server/page/risk/groups', method: 'post', data })
}
export function updateRiskGroup(groupId, data) {
  return request({ url: `/para-server/page/risk/groups/${groupId}`, method: 'put', data })
}
export function delRiskGroup(groupId) {
  return request({ url: `/para-server/page/risk/groups/${groupId}`, method: 'delete' })
}

// 风险规则
export function listRiskRules(query) {
  return request({ url: '/para-server/page/risk/rules', method: 'get', params: query })
}
export function addRiskRule(data) {
  return request({ url: '/para-server/page/risk/rules', method: 'post', data })
}
export function updateRiskRule(ruleId, data) {
  return request({ url: `/para-server/page/risk/rules/${ruleId}`, method: 'put', data })
}
export function delRiskRule(ruleId) {
  return request({ url: `/para-server/page/risk/rules/${ruleId}`, method: 'delete' })
}

// 风控记录
export function listRiskControlLogs(query) {
  return request({ url: '/para-server/page/risk/control-logs', method: 'get', params: query })
}

// 灰名单（风控规则台账）
export function listGraylist(query) {
  return request({ url: '/para-server/page/risk/graylist', method: 'get', params: query })
}
