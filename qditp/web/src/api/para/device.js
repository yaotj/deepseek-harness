import request from '@/utils/request'

// 设备台账来自 ACC 同步（只读），页面可维护业主自定义管理模式、可手动触发同步。
export const listDevices = (params) => request({ url: '/para-server/page/device/list', method: 'get', params })
// 手动同步耗时可达数十秒，单请求放宽超时（全局默认 10s 不够）。
export const syncDevices = () => request({ url: '/para-server/page/device/sync', method: 'post', timeout: 120000 })
export const updateDeviceManageMode = (data) => request({ url: '/para-server/page/device/manage-mode', method: 'put', data })
