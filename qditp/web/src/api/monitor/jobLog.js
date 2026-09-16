import request from '@/utils/request'

// 查询调度日志列表
export function listJobLog(query) {
  return request({
    url: '/web-server/monitor/jobLog/list',
    method: 'get',
    params: query
  })
}

// 查询调度日志的执行日志（按 job_message 里的 traceId 去日志系统反查全链路）
export function getJobTraceLog(jobLogId) {
  return request({
    url: '/web-server/monitor/jobLog/trace/' + jobLogId,
    method: 'get'
  })
}

// 删除调度日志
export function delJobLog(jobLogId) {
  return request({
    url: '/web-server/monitor/jobLog/' + jobLogId,
    method: 'delete'
  })
}

// 清空调度日志
export function cleanJobLog() {
  return request({
    url: '/web-server/monitor/jobLog/clean',
    method: 'delete'
  })
}
