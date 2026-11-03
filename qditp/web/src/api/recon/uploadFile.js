import request from '@/utils/request'

// 查询对账文件上传列表
export function listReconUploadFile(query) {
  return request({
    url: '/web-server/recon/upload-file/list',
    method: 'get',
    params: query
  })
}

// 上传对账文件
export function uploadReconFile(data) {
  return request({
    url: '/web-server/recon/upload-file/upload',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 下载对账文件
export function downloadReconFile(id) {
  return request({
    url: '/web-server/recon/upload-file/download/' + id,
    method: 'get',
    responseType: 'blob'
  })
}

// 删除对账文件记录
export function delReconUploadFile(id) {
  return request({
    url: '/web-server/recon/upload-file/' + id,
    method: 'delete'
  })
}

// 获取文件二进制内容用于预览（复用下载接口）
export function previewReconFile(id) {
  return request({
    url: '/web-server/recon/upload-file/download/' + id,
    method: 'get',
    responseType: 'arraybuffer'
  })
}
