import request from './request'

export const mindmapApi = {
  list: (params) => request.get('/mindmaps', { params }),
  create: (data) => request.post('/mindmaps', data),
  get: (id) => request.get('/mindmaps/' + id),
  update: (id, data) => request.put('/mindmaps/' + id, data),
  remove: (id) => request.delete('/mindmaps/' + id),
  share: (id, password) => request.post('/mindmaps/' + id + '/share', { password }),
  shareInfo: (id) => request.get('/mindmaps/' + id + '/share-info'),
  stopShare: (id) => request.delete('/mindmaps/' + id + '/share'),
  getShare: (token, password) => request.get('/share/' + token, { params: password ? { password } : {} })
}
