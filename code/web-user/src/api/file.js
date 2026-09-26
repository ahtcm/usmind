import request from './request'

export const fileApi = {
  upload: (file, mindMapId) => {
    const fd = new FormData()
    fd.append('file', file)
    if (mindMapId) fd.append('mindMapId', mindMapId)
    return request.post('/file/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  getFileUrl: (id) => '/api/file/' + id,
  previewUrl: (fileId) => '/api/preview/' + fileId
}
