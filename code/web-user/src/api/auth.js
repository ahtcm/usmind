import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  register: (data) => request.post('/auth/register', data),
  info: () => request.get('/auth/info'),
  logout: () => request.post('/auth/logout'),
  changePassword: (data) => request.put('/auth/password', data)
}
