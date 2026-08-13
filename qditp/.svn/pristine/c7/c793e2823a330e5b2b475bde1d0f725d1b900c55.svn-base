import request from '@/utils/request'

// 风险组、风险规则为可维护参数；风险控制记录为只读审计数据。
export const listRiskGroups = (params) => request({ url: '/para-server/page/risk/groups', method: 'get', params })
export const listRiskGroupOptions = () => request({ url: '/para-server/page/risk/groups/options', method: 'get' })
export const addRiskGroup = (data) => request({ url: '/para-server/page/risk/groups', method: 'post', data })
export const updateRiskGroup = (groupId, data) => request({ url: `/para-server/page/risk/groups/${groupId}`, method: 'put', data })
export const delRiskGroup = (groupId) => request({ url: `/para-server/page/risk/groups/${groupId}`, method: 'delete' })

export const listRiskRules = (params) => request({ url: '/para-server/page/risk/rules', method: 'get', params })
export const addRiskRule = (data) => request({ url: '/para-server/page/risk/rules', method: 'post', data })
export const updateRiskRule = (ruleId, data) => request({ url: `/para-server/page/risk/rules/${encodeURIComponent(ruleId)}`, method: 'put', data })
export const delRiskRule = (ruleId) => request({ url: `/para-server/page/risk/rules/${encodeURIComponent(ruleId)}`, method: 'delete' })

export const listRiskControlLogs = (params) => request({ url: '/para-server/page/risk/control-logs', method: 'get', params })
