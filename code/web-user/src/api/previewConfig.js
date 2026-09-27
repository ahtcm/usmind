import request from './request'

export function fetchPreviewConfig() {
  return request.get('/preview-config')
}
