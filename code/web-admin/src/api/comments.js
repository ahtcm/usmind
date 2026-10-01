import request from './request'

export function getCommentList(params) {
  return request.get('/admin/comments', { params })
}

export function deleteComment(id) {
  return request.delete(`/admin/comments/${id}`)
}

export function batchDeleteComments(ids) {
  return request.post('/admin/comments/batch-delete', { ids })
}
