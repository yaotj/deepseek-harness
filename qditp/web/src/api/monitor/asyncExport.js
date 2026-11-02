import request from '@/utils/request'

// 查询异步导出任务列表
export function listAsyncExportTask(query) {
  return request({
    url: '/web-server/monitor/async-export/list',
    method: 'get',
    params: query
  })
}

// 提交异步导出任务
export function submitAsyncExportTask(data) {
  return request({
    url: '/web-server/monitor/async-export/submit',
    method: 'post',
    data: data
  })
}

// 下载异步导出文件
export function downloadAsyncExportFile(taskId) {
  return request({
    url: '/web-server/monitor/async-export/download/' + taskId,
    method: 'get',
    responseType: 'blob'
  })
}
