import request from './request'

export function getUserList(params) {
  return request.get('/admin/users', { params })
}

export function createUser(data) {
  return request.post('/admin/users', data)
}

export function updateUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

export function deleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}

export function batchDeleteUsers(ids) {
  return request.post('/admin/users/batch-delete', ids)
}

export function resetUserPassword(id, data) {
  return request.put(`/admin/users/${id}/password`, data)
}
