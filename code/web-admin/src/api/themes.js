import request from './request'

// 主题模板管理
export const themeApi = {
  list() { return request.get('/admin/themes') },
  get(id) { return request.get(`/admin/themes/${id}`) },
  create(data) { return request.post('/admin/themes', data) },
  update(id, data) { return request.put(`/admin/themes/${id}`, data) },
  remove(id) { return request.delete(`/admin/themes/${id}`) }
}
