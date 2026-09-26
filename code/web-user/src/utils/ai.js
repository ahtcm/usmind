// USMIND AI 助手面板 (后端 API 版)
import { aiApi } from '../api/ai'
import { toast } from './modal'

export class AIAssistant {
  constructor(mind, opts = {}) {
    this.mind = mind
    this.opts = opts
    this.mode = 'create'
    this.panel = null
  }

  mount(panel) {
    this.panel = panel
    panel.querySelectorAll('.ai-mode-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        panel.querySelectorAll('.ai-mode-btn').forEach((b) => b.classList.remove('active'))
        btn.classList.add('active')
        this.mode = btn.dataset.mode
      })
    })
    panel.querySelector('#ai-close').addEventListener('click', () => {
      panel.classList.add('hidden')
    })
    panel.querySelector('#ai-run').addEventListener('click', () => this.run())
  }

  async run() {
    const inputEl = this.panel.querySelector('#ai-input')
    const outEl = this.panel.querySelector('#ai-output')
    const userInput = inputEl.value.trim()
    if (!userInput) {
      toast('请输入指令或内容')
      return
    }

    let context = ''
    try {
      context = JSON.stringify(this.mind.getData())
    } catch (e) { /* ignore */ }

    outEl.innerHTML = '<div class="ai-loading">AI 思考中...</div>'
    try {
      const res = await aiApi.generate({
        mode: this.mode,
        prompt: this.mode === 'create' ? userInput : userInput,
        mindMapData: this.mode === 'create' ? undefined : context
      })
      const text = res.result || ''
      outEl.innerHTML = ''
      if (this.mode === 'create') {
        this.applyGeneratedTree(text, userInput)
      } else {
        outEl.innerHTML = '<div class="ai-result">' + this.escapeHtml(text) + '</div>'
      }
    } catch (e) {
      outEl.innerHTML = '<div class="ai-error">调用失败: ' + this.escapeHtml(e.message || String(e)) + '</div>'
    }
  }

  applyGeneratedTree(text, rootTopic) {
    const m = text.match(/\{[\s\S]*\}/)
    if (!m) {
      this.panel.querySelector('#ai-output').innerHTML =
        '<div class="ai-error">未能解析 AI 返回的导图结构</div>'
      return
    }
    try {
      const tree = JSON.parse(m[0])
      const converted = this.toMindMapData(tree)
      if (converted && converted.root) {
        this.mind.setData(converted.root)
        if (this.mind.view) this.mind.view.fit()
      }
    } catch (e) {
      this.panel.querySelector('#ai-output').innerHTML =
        '<div class="ai-error">解析失败: ' + this.escapeHtml(e.message) + '</div>'
    }
  }

  toMindMapData(tree) {
    const walk = (node) => {
      const out = { data: { text: String(node.topic || node.text || '').trim() || '未命名' } }
      if (Array.isArray(node.children) && node.children.length) {
        out.children = node.children.map(walk)
      }
      return out
    }
    if (!tree) return null
    return { root: walk(tree) }
  }

  escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[c])
  }
}
