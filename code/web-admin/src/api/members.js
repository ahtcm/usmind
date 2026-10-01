import request from './request'

// 会员管理
export function getMemberList(params) {
  return request.get('/admin/members', { params })
}
export function setMember(id, data) {
  return request.put(`/admin/members/${id}/member`, data)
}

// 会员套餐价格管理
export function getMemberPlans() {
  return request.get('/admin/member-plans')
}
export function createMemberPlan(data) {
  return request.post('/admin/member-plans', data)
}
export function updateMemberPlan(id, data) {
  return request.put(`/admin/member-plans/${id}`, data)
}
export function deleteMemberPlan(id) {
  return request.delete(`/admin/member-plans/${id}`)
}

// 会员兑换码管理
export function getRedeemCodes(params) {
  return request.get('/admin/redeem-codes', { params })
}
export function createRedeemCodes(data) {
  return request.post('/admin/redeem-codes', data)
}
export function deleteRedeemCode(id) {
  return request.delete(`/admin/redeem-codes/${id}`)
}
export function exportRedeemCodes(status) {
  return request.get('/admin/redeem-codes/export', { params: { status: status || '' }, responseType: 'blob' })
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
