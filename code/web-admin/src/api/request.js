import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：附带 token
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一解包 { code, message, data }
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 二进制流等直接返回
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response
    }
    if (res.code === 200) {
      return res.data
    }
    if (res.code === 401) {
      handleUnauthorized()
      return Promise.reject(new Error(res.message || '未登录或登录已过期'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      handleUnauthorized()
    } else {
      const msg = error.response?.data?.message || error.message || '网络错误'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

function handleUnauthorized() {
  const userStore = useUserStore()
  userStore.logout({ silent: true })
  ElMessage.error('登录已过期，请重新登录')
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

export default request
