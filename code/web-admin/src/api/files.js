import request from './request'

export function getFileList(params) {
  return request.get('/admin/files', { params })
}

export function deleteFile(id, force) {
  return request.delete(`/admin/files/${id}`, { params: { force: !!force } })
}

export function batchDeleteFiles(ids, force) {
  return request.post('/admin/files/batch-delete', ids, { params: { force: !!force } })
}

export function getOrphanStats() {
  return request.get('/admin/files/orphans')
}

export function cleanupOrphans() {
  return request.post('/admin/files/orphans/cleanup')
}
