import request from './request'

export function getOrderList(params) {
  return request.get('/admin/orders', { params })
}

export function deleteOrder(id) {
  return request.delete(`/admin/orders/${id}`)
}
