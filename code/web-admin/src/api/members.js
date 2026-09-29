import request from './request'

// 会员管理
export function getMemberList(params) {
  return request.get('/admin/members', { params })
}
export function setMember(id, data) {
  return request.put(`/admin/members/${id}/member`, data)
}

// IP 黑白名单
export function getIpRules(params) {
  return request.get('/admin/ip-rules', { params })
}
export function createIpRule(data) {
  return request.post('/admin/ip-rules', data)
}
export function updateIpRule(id, data) {
  return request.put(`/admin/ip-rules/${id}`, data)
}
export function deleteIpRule(id) {
  return request.delete(`/admin/ip-rules/${id}`)
}
export function batchDeleteIpRules(ids) {
  return request.delete('/admin/ip-rules/batch', { data: ids })
}

// 支付 / 验证码 / 防护配置
export function getPayConfig() {
  return request.get('/admin/pay-config')
}
export function savePayConfig(data) {
  return request.put('/admin/pay-config', data)
}
