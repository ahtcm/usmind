<template>
  <el-container class="admin-layout">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" :width="collapsed ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <span v-if="!collapsed" class="logo-text">USMIND</span>
        <span v-else class="logo-text-mini">U</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        class="side-menu"
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#40e0d0"
        router
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>{{ m.label }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar" :class="{ 'topbar-mobile': isMobile }">
        <div class="topbar-left">
          <el-icon class="collapse-btn" @click="isMobile ? (drawerVisible = true) : (collapsed = !collapsed)">
            <Menu v-if="isMobile" />
            <Fold v-else-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb v-if="!isMobile" separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
          <span v-else class="mobile-title">{{ route.meta.title || 'USMIND' }}</span>
        </div>
        <div class="topbar-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.user?.avatar" @click.stop="avatarInput.click()" title="点击上传头像">
                {{ avatarText }}
              </el-avatar>
              <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarPick" />
              <span v-if="!isMobile" class="username">{{ userStore.user?.nickname || userStore.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 移动端抽屉导航 -->
    <el-drawer v-model="drawerVisible" direction="ltr" :size="230" :with-header="false" custom-class="mobile-drawer-panel">
      <div class="logo">
        <span class="logo-text">USMIND</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="side-menu"
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#40e0d0"
        router
        @select="drawerVisible = false"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>{{ m.label }}</template>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdDialog.visible" title="修改密码" :width="dialogWidth">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" :label-width="isMobile ? '70px' : '90px'">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="pwdDialog.loading" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'
import request from '@/api/request'
import { Fold, Expand, Menu, ArrowDown, Odometer, User, Share, Files, FolderOpened, Brush, Star, Setting, Tools, Refresh, Medal, Key, Lock, List, OfficeBuilding, Tickets, ChatDotRound } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const drawerVisible = ref(false)
const avatarInput = ref(null)
const isMobile = ref(false)

const menus = [
  { path: '/dashboard', label: '仪表盘', icon: Odometer },
  { path: '/users', label: '用户管理', icon: User },
  { path: '/members', label: '会员管理', icon: Medal },
  { path: '/member-config', label: '会员功能配置', icon: Key },
  { path: '/pay-config', label: '支付与验证码', icon: Lock },
  { path: '/ip-rules', label: 'IP 黑白名单', icon: Lock },
  { path: '/mindmaps', label: '导图管理', icon: Share },
  { path: '/orgs', label: '机构管理', icon: OfficeBuilding },
  { path: '/orders', label: '订单管理', icon: Tickets },
  { path: '/comments', label: '评论管理', icon: ChatDotRound },
  { path: '/resources', label: '资源管理', icon: Files },
  { path: '/oss', label: 'OSS配置', icon: FolderOpened },
  { path: '/themes', label: '主题模板', icon: Brush },
  { path: '/icons', label: '图标库', icon: Star },
  { path: '/settings', label: '系统设置', icon: Setting },
  { path: '/project-config', label: '项目配置', icon: Tools },
  { path: '/data-backup', label: '数据备份', icon: Refresh },
  { path: '/logs', label: '请求日志', icon: List }
]

const activeMenu = computed(() => route.path)
const dialogWidth = computed(() => (isMobile.value ? '92%' : '420px'))
const avatarText = computed(() => {
  const name = userStore.user?.nickname || userStore.user?.username || 'U'
  return name.charAt(0).toUpperCase()
})

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})
onBeforeUnmount(() => window.removeEventListener('resize', checkMobile))

const pwdDialog = reactive({ visible: false, loading: false })
const pwdFormRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '新密码至少6位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function onAvatarPick(e) {
  const f = e.target && e.target.files && e.target.files[0]
  e.target.value = ''
  if (!f) return
  if (!f.type.startsWith('image/')) { ElMessage.warning('请选择图片文件'); return }
  if (f.size > 2 * 1024 * 1024) { ElMessage.warning('头像不能超过 2MB'); return }
  const fd = new FormData()
  fd.append('file', f)
  try {
    const up = await request.post('/file/upload-avatar', fd)
    userStore.user = { ...userStore.user, avatar: up.url || '' }
    localStorage.setItem('usmind_admin_user', JSON.stringify(userStore.user))
    ElMessage.success('头像已更新')
  } catch (e2) {
    ElMessage.error((e2 && e2.message) || '头像上传失败')
  }
}

function handleCommand(cmd) {
  if (cmd === 'profile') {
    ElMessage.info('个人信息功能开发中')
  } else if (cmd === 'password') {
    pwdDialog.visible = true
    pwdForm.oldPassword = pwdForm.newPassword = pwdForm.confirmPassword = ''
  } else if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).then(async () => {
      await userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    }).catch(() => {})
  }
}

async function submitPassword() {
  await pwdFormRef.value.validate()
  pwdDialog.loading = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    pwdDialog.visible = false
    await userStore.logout()
    router.push('/login')
  } finally {
    pwdDialog.loading = false
  }
}
</script>

<style scoped>
.admin-layout {
  height: 100%;
}
.sidebar {
  background: #1f2d3d;
  transition: width 0.2s;
  overflow: hidden;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 18px;
  letter-spacing: 1px;
}
.logo-text-mini {
  color: #40e0d0;
  font-size: 22px;
}
.side-menu {
  border-right: none;
}
.topbar {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  height: 56px;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #5a5e66;
}
.mobile-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.username {
  font-size: 14px;
  color: #303133;
}
.main-content {
  background: #f0f2f5;
  overflow-y: auto;
  padding: 12px;
}
.mobile-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: #1f2d3d;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
@media (max-width: 768px) {
  .main-content {
    padding: 8px;
  }
  /* 移动端顶栏与抽屉同色，整体统一 */
  .topbar-mobile {
    background: #1f2d3d;
    border-bottom: none;
  }
  .topbar-mobile .collapse-btn {
    color: #bfcbd9;
  }
  .topbar-mobile .mobile-title {
    color: #fff;
  }
  .topbar-mobile .user-info {
    color: #fff;
  }
  .topbar-mobile .user-info .el-icon {
    color: #bfcbd9;
  }
}
</style>
