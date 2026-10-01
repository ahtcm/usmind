import request from './request'

export const memberApi = {
  plans: () => request.get('/member/plans'),
  info: () => request.get('/member/info'),
  redeem: (code) => request.post('/member/redeem', { code }),
  wechatCreate: (planId) => request.post('/pay/wechat/create', { planId }),
  wechatCreateH5: (planId) => request.post('/pay/wechat/create-h5', { planId })
}

export const configApi = {
  client: () => request.get('/config/client')
}
