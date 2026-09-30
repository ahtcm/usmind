import request from './request'

// 仪表盘统计（契约未明确，按约定路径）
export function getDashboardStats() {
  return request.get('/admin/stats')
}

// 经营统计（注册/VIP/收入/趋势/分布）
export function getBusinessStats(start, end) {
  return request.get('/admin/stats/dashboard/business', { params: { start, end } })
}

// 系统设置（JWT / 上传 / AI 配置）
export function getSettings() {
  return request.get('/admin/settings')
}

export function saveSettings(data) {
  return request.put('/admin/settings', data)
}
