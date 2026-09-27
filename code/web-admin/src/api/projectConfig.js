import request from './request'

// 项目配置：上传/预览类型 + Java/Nginx 配置文件
export function getProjectConfig() {
  return request.get('/admin/project-config')
}

export function saveProjectConfig(data) {
  return request.put('/admin/project-config', data)
}
