<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="login-title">
        <h2>USMIND 管理后台</h2>
        <p>请登录以继续</p>
      </div>
      <el-form ref="loginFormRef" :model="loginForm" :rules="rules" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="密码" :prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" size="large" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)
const loginForm = reactive({ username: 'admin', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await loginFormRef.value.validate()
  loading.value = true
  try {
    await userStore.login(loginForm)
    if (!userStore.isAdmin) {
      await userStore.logout({ silent: true })
      ElMessage.error('该账号不是管理账户，无权登录管理后台')
      return
    }
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #0e7c86 100%);
}
.login-card {
  width: 380px;
  max-width: 92%;
  border-radius: 10px;
}
.login-title {
  text-align: center;
  margin-bottom: 24px;
}
.login-title h2 {
  margin: 0 0 8px;
  color: #1f2d3d;
}
.login-title p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.login-btn {
  width: 100%;
}
</style>
