import request from './request'

export function getLogList(params) {
  return request.get('/admin/logs', { params })
}

export function getInterfaceList(keyword) {
  return request.get('/admin/logs/interface-list', { params: { keyword: keyword || '' } })
}

export function getLogDetail(id) {
  return request.get(`/admin/logs/${id}`)
}

export function clearAllLogs() {
  return request.delete('/admin/logs')
}
