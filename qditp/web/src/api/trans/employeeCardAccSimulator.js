import request from '@/utils/request'

/**
 * 分页查询模拟 ACC 员工卡列表。
 *
 * @param {Object} params 查询参数：pageNum、pageSize、cardNo、employeeName、cardStatus
 * @returns {Promise} 分页结果，data 为 PageInfo 结构（含 list 与 total）
 */
export function listAccSimulatorCards(params) {
  return request({
    url: '/employee-card-acc-simulator/page/acc-simulator/cards',
    method: 'get',
    params
  })
}

/**
 * 新增或编辑模拟 ACC 员工卡。
 *
 * @param {Object} data 员工卡信息，cardNo 为业务主键，重复时按编辑处理
 * @returns {Promise} 操作结果
 */
export function saveAccSimulatorCard(data) {
  return request({
    url: '/employee-card-acc-simulator/page/acc-simulator/cards',
    method: 'post',
    data
  })
}

/**
 * 分页查询模拟调用历史记录。
 *
 * @param {Object} params 查询参数：pageNum、pageSize、operation
 * @returns {Promise} 分页结果
 */
export function listAccSimulatorHistory(params) {
  return request({
    url: '/employee-card-acc-simulator/page/acc-simulator/history',
    method: 'get',
    params
  })
}

/**
 * 发送员工卡状态通知（模拟 ACC → ITP）。
 *
 * @param {Object} data 通知请求，含 targetUrl 与 cardList
 * @returns {Promise} 交互记录
 */
export function sendEmployeeCardNotify(data) {
  return request({
    url: '/employee-card-acc-simulator/page/acc-simulator/notify',
    method: 'post',
    data
  })
}

/**
 * 发送员工资料变更通知（模拟 ACC → ITP）。
 *
 * @param {Object} data 变更通知请求，含 targetUrl 与 employee
 * @returns {Promise} 交互记录
 */
export function sendEmployeeCardUpdateNotify(data) {
  return request({
    url: '/employee-card-acc-simulator/page/acc-simulator/update-notify',
    method: 'post',
    data
  })
}

/**
 * 清空所有模拟 ACC 员工卡。
 *
 * @returns {Promise} 操作结果
 */
export function clearAccSimulatorCards() {
  return request({
    url: '/employee-card-acc-simulator/page/acc-simulator/cards',
    method: 'delete'
  })
}

/**
 * 清空所有模拟调用历史记录。
 *
 * @returns {Promise} 操作结果
 */
export function clearAccSimulatorHistory() {
  return request({
    url: '/employee-card-acc-simulator/page/acc-simulator/history',
    method: 'delete'
  })
}
