import request from './request'

export function login(data) {
  // 管理后台独立登录接口：不校验前台图形验证码
  return request.post('/auth/admin-login', data)
}

export function getInfo() {
  return request.get('/auth/info')
}

export function logout() {
  return request.post('/auth/logout')
}

export function changePassword(data) {
  return request.put('/auth/password', data)
}
