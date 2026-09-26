import request from './request'

export function getIconList(params) {
  return request.get('/admin/icons', { params })
}

export function createIcon(data) {
  return request.post('/admin/icons', data)
}

export function batchImport(data) {
  return request.post('/admin/icons/batch', data)
}

export function deleteIcon(id) {
  return request.delete(`/admin/icons/${id}`)
}

export function batchDeleteIcons(ids) {
  return request.post('/admin/icons/batch-delete', ids)
}
