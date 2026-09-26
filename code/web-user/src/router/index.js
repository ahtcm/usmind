import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/maps' },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  { path: '/maps', name: 'MindMapList', component: () => import('../views/MindMapList.vue'), meta: { requiresAuth: true } },
  { path: '/editor/:id?', name: 'Editor', component: () => import('../views/Editor.vue'), meta: { requiresAuth: true } },
  { path: '/share/:token', name: 'ShareView', component: () => import('../views/ShareView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('usmind.token')
  if (to.meta.requiresAuth && !token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'Login' && token) {
    return { name: 'MindMapList' }
  }
})

export default router
