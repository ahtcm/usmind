<template>
  <div class="login-page">
    <div class="login-box">
      <h2>USMIND 思维导图</h2>
      <div class="field">
        <label>用户名</label>
        <input v-model="username" type="text" placeholder="请输入用户名" @keyup.enter="doLogin" />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="password" type="password" placeholder="请输入密码" @keyup.enter="doLogin" />
      </div>
      <button class="btn-login" @click="doLogin" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</button>
      <div v-if="err" class="err">{{ err }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const username = ref('')
const password = ref('')
const err = ref('')
const loading = ref(false)

async function doLogin() {
  if (!username.value || !password.value) {
    err.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  err.value = ''
  try {
    const data = await authApi.login({ username: username.value, password: password.value })
    userStore.setToken(data.token)
    userStore.setUser(data.user)
    router.push(route.query.redirect || '/maps')
  } catch (e) {
    err.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>
