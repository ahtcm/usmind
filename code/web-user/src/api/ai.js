import request from './request'

export const aiApi = {
  generate: (data) => request.post('/ai/generate', data)
}
