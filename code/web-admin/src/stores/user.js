import { defineStore } from 'pinia'
import { login as loginApi, getInfo, logout as logoutApi } from '@/api/auth'

const TOKEN_KEY = 'usmind_admin_token'
const USER_KEY = 'usmind_admin_user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    async login(loginForm) {
      const data = await loginApi(loginForm)
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    async fetchInfo() {
      const info = await getInfo()
      this.user = info
      localStorage.setItem(USER_KEY, JSON.stringify(info))
      return info
    },
    async logout(options = {}) {
      const { silent } = options
      try {
        if (this.token) await logoutApi()
      } catch (e) {
        // ignore
      }
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      if (!silent) {
        // 由调用方跳转
      }
    }
  }
})
