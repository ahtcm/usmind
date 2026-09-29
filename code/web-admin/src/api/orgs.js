import request from './request'

export function getOrgList(params) {
  return request.get('/admin/orgs', { params })
}

export function getOrgMembers(id) {
  return request.get(`/admin/orgs/${id}/members`)
}

export function disbandOrg(id) {
  return request.delete(`/admin/orgs/${id}`)
}
