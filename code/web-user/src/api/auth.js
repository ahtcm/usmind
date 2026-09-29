import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  register: (data) => request.post('/auth/register', data),
  info: () => request.get('/auth/info'),
  logout: () => request.post('/auth/logout'),
  changePassword: (data) => request.put('/auth/password', data),
  captcha: () => request.get('/auth/captcha'),
  sendCode: (data) => request.post('/auth/send-code', data),
  wechatUrl: () => request.get('/auth/wechat/url'),
  wechatCallback: (code, state) => request.get('/auth/wechat/callback', { params: { code, state } }),
  updateProfile: (data) => request.put('/user/profile', data)
}
