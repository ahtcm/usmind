// 自定义弹窗工具（深色玻璃风格）——替换浏览器原生 alert / confirm / prompt
// 用法：
//   toast('保存成功')
//   toast('删除失败', 'error')
//   const ok = await showConfirm({ title: '删除', message: '确定删除吗？', danger: true })
//   const val = await showPrompt({ title: '设置密码', message: '输入分享密码', defaultValue: '', placeholder: '留空则无密码' })

let toastWrap = null
function ensureToastWrap() {
  if (!toastWrap) {
    toastWrap = document.createElement('div')
    toastWrap.className = 'um-toast-wrap'
    document.body.appendChild(toastWrap)
  }
  return toastWrap
}

export function toast(msg, type = 'info') {
  const w = ensureToastWrap()
  const el = document.createElement('div')
  el.className = 'um-toast' + (type === 'error' ? ' err' : '')
  el.textContent = msg
  w.appendChild(el)
  setTimeout(() => {
    el.style.transition = 'opacity .25s, transform .25s'
    el.style.opacity = '0'
    el.style.transform = 'translateY(-8px)'
    setTimeout(() => el.remove(), 260)
  }, 2400)
}

let modalRoot = null
function ensureModalRoot() {
  if (!modalRoot) {
    modalRoot = document.createElement('div')
    modalRoot.className = 'um-modal-root'
    document.body.appendChild(modalRoot)
  }
  return modalRoot
}

// 打开一个模态层；返回 { destroy, onBackground } 用于绑定取消语义
function openModal(buildBody) {
  const root = ensureModalRoot()
  const mask = document.createElement('div')
  mask.className = 'um-modal-mask'
  const box = document.createElement('div')
  box.className = 'um-modal'
  mask.appendChild(box)
  buildBody(box)
  root.appendChild(mask)
  document.body.style.overflow = 'hidden'
  requestAnimationFrame(() => mask.classList.add('open'))

  let done = false
  const destroy = () => {
    if (done) return
    done = true
    mask.classList.remove('open')
    document.body.style.overflow = ''
    setTimeout(() => mask.remove(), 200)
  }
  const onBackground = (cb) => {
    const onKey = (e) => { if (e.key === 'Escape') cb() }
    const onMask = (e) => { if (e.target === mask) cb() }
    document.addEventListener('keydown', onKey)
    mask.addEventListener('click', onMask)
    return () => { document.removeEventListener('keydown', onKey); mask.removeEventListener('click', onMask) }
  }
  return { destroy, onBackground }
}

export function showConfirm(opts) {
  return new Promise((resolve) => {
    const o = opts || {}
    let settled = false
    const { destroy, onBackground } = openModal((b) => {
      const title = document.createElement('div')
      title.className = 'um-modal-title'
      title.textContent = o.title || '提示'
      const msg = document.createElement('div')
      msg.className = 'um-modal-msg'
      msg.textContent = o.message || ''
      const btns = document.createElement('div')
      btns.className = 'um-modal-btns'
      const cancel = document.createElement('button')
      cancel.className = 'um-btn um-btn-cancel'
      cancel.textContent = '取消'
      const ok = document.createElement('button')
      ok.className = 'um-btn ' + (o.danger ? 'um-btn-danger' : 'um-btn-ok')
      ok.textContent = o.okText || (o.danger ? '删除' : '确定')
      cancel.addEventListener('click', () => finish(false))
      ok.addEventListener('click', () => finish(true))
      btns.appendChild(cancel)
      btns.appendChild(ok)
      b.appendChild(title)
      b.appendChild(msg)
      b.appendChild(btns)
    })
    const offBg = onBackground(() => finish(false))
    function finish(v) {
      if (settled) return
      settled = true
      offBg()
      destroy()
      resolve(v)
    }
  })
}

export function showPrompt(opts) {
  return new Promise((resolve) => {
    const o = opts || {}
    let settled = false
    const { destroy, onBackground } = openModal((b) => {
      const title = document.createElement('div')
      title.className = 'um-modal-title'
      title.textContent = o.title || '输入'
      const msg = document.createElement('div')
      msg.className = 'um-modal-msg'
      msg.textContent = o.message || ''
      const input = document.createElement('input')
      input.className = 'um-modal-input'
      input.type = 'text'
      input.value = o.defaultValue || ''
      input.placeholder = o.placeholder || ''
      const btns = document.createElement('div')
      btns.className = 'um-modal-btns'
      const cancel = document.createElement('button')
      cancel.className = 'um-btn um-btn-cancel'
      cancel.textContent = '取消'
      const ok = document.createElement('button')
      ok.className = 'um-btn um-btn-ok'
      ok.textContent = o.okText || '确定'
      const submit = () => finish(input.value.trim())
      cancel.addEventListener('click', () => finish(null))
      ok.addEventListener('click', submit)
      input.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit() })
      btns.appendChild(cancel)
      btns.appendChild(ok)
      b.appendChild(title)
      b.appendChild(msg)
      b.appendChild(input)
      b.appendChild(btns)
      setTimeout(() => { input.focus(); input.select() }, 60)
    })
    const offBg = onBackground(() => finish(null))
    function finish(v) {
      if (settled) return
      settled = true
      offBg()
      destroy()
      resolve(v)
    }
  })
}
