// USMIND AI 助手面板
// 双数据源可插拔: 本地 Ollama / 第三方 OpenAI 兼容 API
// 配置存 localStorage(仅本地浏览器), 不写入静态文件

const AI_CONFIG_KEY = 'usmind.ai.config'

export class AIAssistant {
  constructor(mind, opts = {}) {
    this.mind = mind
    this.opts = opts
    this.mode = 'create'
    this.panel = null
    this.config = this.loadConfig()
  }

  loadConfig() {
    try {
      return JSON.parse(localStorage.getItem(AI_CONFIG_KEY)) || {}
    } catch (e) {
      return {}
    }
  }

  saveConfig() {
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(this.config))
  }

  mount(panel) {
    this.panel = panel
    // 模式切换
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
    // 运行
    panel.querySelector('#ai-run').addEventListener('click', () => this.run())
    // 配置入口(在输出区底部提供"AI 配置")
    this.renderConfigLink()
  }

  renderConfigLink() {
    const out = this.panel.querySelector('#ai-output')
    const link = document.createElement('div')
    link.className = 'ai-config-link'
    link.textContent = '⚙ AI 数据源配置'
    link.addEventListener('click', () => this.openConfig())
    out.appendChild(link)
  }

  openConfig() {
    const cfg = this.config
    const base = prompt('AI 接口地址\n本地 Ollama 示例: http://127.0.0.1:11434/v1\n第三方示例: https://api.openai.com/v1', cfg.baseUrl || '')
    if (base === null) return
    const apiKey = prompt('API Key (Ollama 留空)', cfg.apiKey || '')
    if (apiKey === null) return
    const model = prompt('模型名称 (Ollama 示例: qwen2.5)', cfg.model || '')
    if (model === null) return
    this.config = { baseUrl: base.trim(), apiKey: apiKey.trim(), model: model.trim() }
    this.saveConfig()
    alert('AI 数据源已保存')
  }

  buildSystemPrompt() {
    if (this.mode === 'create') {
      return '你是思维导图结构生成专家。根据用户主题，输出一个思维导图的 JSON 数据，格式为：{"topic":"根主题","children":[{"topic":"分支1","children":[...]}]}。只输出 JSON，不要输出任何解释或 markdown 代码块标记。'
    }
    if (this.mode === 'summary') {
      return '你是内容归纳专家。用户会提供思维导图内容，请给出精炼的核心要点总结，用简洁的分点形式输出。'
    }
    return '你是思维导图顾问。针对用户的问题给出清晰、可操作的解答，必要时给出思维导图结构建议。'
  }

  async run() {
    const inputEl = this.panel.querySelector('#ai-input')
    const outEl = this.panel.querySelector('#ai-output')
    const userInput = inputEl.value.trim()
    if (!userInput) {
      alert('请输入指令或内容')
      return
    }
    if (!this.config.baseUrl) {
      alert('请先配置 AI 数据源 (点击输出区下方"AI 配置")')
      return
    }

    // 收集当前图/选中节点上下文
    let context = ''
    try {
      const data = this.mind.getData()
      context = JSON.stringify(data)
    } catch (e) { /* ignore */ }

    const prompt =
      this.mode === 'create'
        ? userInput
        : `思维导图当前数据: ${context}\n\n用户指令: ${userInput}`

    outEl.innerHTML = '<div class="ai-loading">AI 思考中...</div>'
    try {
      const text = await this.callAI(prompt)
      outEl.innerHTML = ''
      if (this.mode === 'create') {
        this.applyGeneratedTree(text, userInput)
      } else {
        outEl.innerHTML = '<div class="ai-result">' + this.escapeHtml(text) + '</div>'
      }
    } catch (e) {
      outEl.innerHTML = '<div class="ai-error">调用失败: ' + this.escapeHtml(e.message) + '</div>'
    }
  }

  async callAI(prompt) {
    const { baseUrl, apiKey, model } = this.config
    const url = baseUrl.replace(/\/+$/, '') + '/chat/completions'
    const headers = { 'Content-Type': 'application/json' }
    if (apiKey) headers['Authorization'] = 'Bearer ' + apiKey
    const resp = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        model,
        messages: [
          { role: 'system', content: this.buildSystemPrompt() },
          { role: 'user', content: prompt }
        ],
        stream: false
      })
    })
    if (!resp.ok) {
      throw new Error('HTTP ' + resp.status + ' ' + (await resp.text()).slice(0, 200))
    }
    const json = await resp.json()
    return json.choices && json.choices[0] ? json.choices[0].message.content : ''
  }

  applyGeneratedTree(text, rootTopic) {
    // 从 AI 回复中提取 JSON 并应用到画布
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
        alert('AI 已生成思维导图')
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
