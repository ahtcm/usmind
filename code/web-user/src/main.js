import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')

// ===== 全局错误上报（进入管理后台"请求日志"可查前端异常） =====
;(function () {
  const enabled = true
  const cache = []
  let timer = null
  function report(payload) {
    if (!enabled) return
    cache.push(payload)
    if (cache.length >= 10) { flush() }
    if (!timer) {
      timer = setTimeout(flush, 5000)
    }
  }
  function flush() {
    timer = null
    const batch = cache.splice(0, cache.length)
    if (!batch.length) return
    const body = {
      level: batch.some(b => b.level === 'error') ? 'error' : 'warn',
      message: batch.map(b => b.message).join('\n').slice(0, 1000),
      detail: batch.map(b => b.detail || '').join('\n').slice(0, 3000),
      path: batch[0].path || location.pathname
    }
    try {
      const token = localStorage.getItem('usmind_token') || ''
      fetch('/api/log/report', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
        body: JSON.stringify(body)
      }).catch(() => {})
    } catch (e) { /* 忽略 */ }
  }
  window.addEventListener('error', function (e) {
    report({ level: 'error', path: location.pathname, message: 'JS错误: ' + (e.message || 'unknown'), detail: (e.filename || '') + ':' + (e.lineno || '') })
  })
  window.addEventListener('unhandledrejection', function (e) {
    const reason = e && e.reason
    const msg = reason && reason.message ? reason.message : String(reason || 'unknown')
    report({ level: 'error', path: location.pathname, message: 'Promise错误: ' + msg })
  })
  // 接口调用失败的 URL 级错误（axios 拦截器兜底，避免重复）
  window.__usmind_report_api_error = function (path, msg) {
    report({ level: 'warn', path: path || location.pathname, message: '接口异常: ' + (msg || '') })
  }
  // 页面卸载前尽量清空缓存
  window.addEventListener('pagehide', flush)
})()

