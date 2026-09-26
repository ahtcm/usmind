import request from './request'

export function getFileList(params) {
  return request.get('/admin/files', { params })
}

export function deleteFile(id) {
  return request.delete(`/admin/files/${id}`)
}

export function batchDeleteFiles(ids) {
  return request.post('/admin/files/batch-delete', ids)
}
