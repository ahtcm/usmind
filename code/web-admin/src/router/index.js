import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'users', name: 'Users', component: () => import('@/views/Users.vue'), meta: { title: '用户管理', requireAdmin: true } },
      { path: 'mindmaps', name: 'MindMaps', component: () => import('@/views/MindMaps.vue'), meta: { title: '导图管理', requireAdmin: true } },
      { path: 'resources', name: 'Resources', component: () => import('@/views/Resources.vue'), meta: { title: '资源管理', requireAdmin: true } },
      { path: 'oss', name: 'Oss', component: () => import('@/views/OssConfig.vue'), meta: { title: 'OSS配置', requireAdmin: true } },
      { path: 'themes', name: 'Themes', component: () => import('@/views/Themes.vue'), meta: { title: '主题模板', requireAdmin: true } },
      { path: 'icons', name: 'Icons', component: () => import('@/views/Icons.vue'), meta: { title: '图标库', requireAdmin: true } },
      { path: 'settings', name: 'Settings', component: () => import('@/views/Settings.vue'), meta: { title: '系统设置', requireAdmin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.path === '/login') {
    if (userStore.isLoggedIn) return '/dashboard'
    return true
  }
  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requireAdmin && !userStore.isAdmin) {
    return { path: '/dashboard' }
  }
  document.title = `${to.meta.title || ''} · USMIND 管理后台`
  return true
})

export default router
