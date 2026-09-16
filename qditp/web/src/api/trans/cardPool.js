import request from '@/utils/request'

export function getCardPoolSummary() {
  return request({ url: '/card-pool-server/page/card-pools/summary', method: 'get' })
}

export function listCardPoolBatches(params) {
  return request({ url: '/card-pool-server/page/card-pools/batches', method: 'get', params })
}

export function requestCardPoolBatch(cardType) {
  return request({ url: '/card-pool-server/page/card-pools/batches', method: 'post', data: { cardType, operator: 'WEB' } })
}

export function retryCardPoolBatch(batchNo) {
  return request({ url: `/card-pool-server/page/card-pools/batches/${encodeURIComponent(batchNo)}/retry`, method: 'post' })
}
