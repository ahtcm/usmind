import axios from 'axios'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('usmind.token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      if (body.code === 401) {
        localStorage.removeItem('usmind.token')
        localStorage.removeItem('usmind.user')
        router.push({ name: 'Login' })
        return Promise.reject(new Error(body.message || '未登录'))
      }
      const er = new Error(body.message || '请求失败')
      er.code = body.code
      er.status = body.status
      return Promise.reject(er)
    }
    return body
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('usmind.token')
      localStorage.removeItem('usmind.user')
      router.push({ name: 'Login' })
    }
    return Promise.reject(err)
  }
)

export default request
