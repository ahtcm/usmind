import request from './request'

export function getMindmapList(params) {
  return request.get('/mindmaps', { params })
}

export function getMindmapDetail(id) {
  return request.get(`/mindmaps/${id}`)
}

export function deleteMindmap(id) {
  return request.delete(`/mindmaps/${id}`)
}

export function batchDeleteMindmaps(ids) {
  return request.post('/admin/mindmaps/batch-delete', ids)
}

export function cancelShareMindmap(id) {
  return request.delete(`/mindmaps/${id}/share`)
}

export function getCollabConfig(id) {
  return request.get(`/mindmaps/${id}/collab-config`)
}

export function updateCollabConfig(id, cfg) {
  return request.put(`/mindmaps/${id}/collab-config`, cfg)
}
