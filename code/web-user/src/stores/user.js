import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('usmind.token') || '')
  const user = ref(JSON.parse(localStorage.getItem('usmind.user') || 'null'))

  function setToken(t) {
    token.value = t
    localStorage.setItem('usmind.token', t)
  }

  function setUser(u) {
    user.value = u
    localStorage.setItem('usmind.user', JSON.stringify(u))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('usmind.token')
    localStorage.removeItem('usmind.user')
  }

  return { token, user, setToken, setUser, logout }
})
