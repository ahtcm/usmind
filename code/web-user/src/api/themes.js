import request from './request'

// 主题模板（后台配置，公开读取）
export function fetchThemes() {
  return request.get('/themes')
}
