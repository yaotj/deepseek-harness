import request from '@/utils/request'
import { parseStrEmpty } from "@/utils/points";

// 查询操作员列表
export function listOperator(query) {
  return request({
    url: '/para/operator/list',
    method: 'get',
    params: query
  })
}

// 查询操作员详细
export function getOperator(operatorId) {
  return request({
    url: '/para/operator/' + parseStrEmpty(operatorId),
    method: 'get'
  })
}

// 新增操作员
export function addOperator(data) {
  return request({
    url: '/para/operator',
    method: 'post',
    data: data
  })
}

// 修改操作员
export function updateOperator(data) {
  return request({
    url: '/para/operator',
    method: 'put',
    data: data
  })
}

// 删除操作员
export function delOperator(operatorId) {
  return request({
    url: '/para/operator/' + operatorId,
    method: 'delete'
  })
}

// 操作员状态修改
export function changeOperatorStatus(operatorId, status) {
  const data = {
    operatorId,
    status
  }
  return request({
    url: '/para/operator/changeStatus',
    method: 'put',
    data: data
  })
}

