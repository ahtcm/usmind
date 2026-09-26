import request from './request'

export function getOssConfigs() {
  return request.get('/admin/oss/configs')
}

export function createOssConfig(data) {
  return request.post('/admin/oss/configs', data)
}

export function updateOssConfig(id, data) {
  return request.put(`/admin/oss/configs/${id}`, data)
}

export function deleteOssConfig(id) {
  return request.delete(`/admin/oss/configs/${id}`)
}

export function activateOssConfig(id) {
  return request.put(`/admin/oss/configs/${id}/activate`)
}

export function testOssConfig(id) {
  return request.get(`/admin/oss/test/${id}`)
}
