import request from './request'

export function fetchIcons() {
  return request.get('/icons')
}