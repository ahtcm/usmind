import request from './request'

export const memberApi = {
  plans: () => request.get('/member/plans'),
  info: () => request.get('/member/info'),
  wechatCreate: (planId) => request.post('/pay/wechat/create', { planId })
}

export const configApi = {
  client: () => request.get('/config/client')
}
