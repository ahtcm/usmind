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

export function cancelShareMindmap(id) {
  return request.delete(`/mindmaps/${id}/share`)
}
