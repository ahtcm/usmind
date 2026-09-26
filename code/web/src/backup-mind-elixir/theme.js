// USMIND 主题模板（对标 XMind 观感）
// mind-elixir 主题结构: { name, palette: string[], cssVar }
// 额外通过 #map[data-theme] 由 style.css 精确控制各主题的节点渐变/阴影/画布质感

export const THEMES = [
  {
    name: 'XMind 浅色',
    cssVar: {
      '--node-gap-x': '26px',
      '--node-gap-y': '12px',
      '--main-gap-x': '60px',
      '--main-gap-y': '42px',
      '--main-color': '#334155',
      '--main-bgcolor': '#ffffff',
      '--main-bgcolor-transparent': 'rgba(255,255,255,0.9)',
      '--root-color': '#ffffff',
      '--root-bgcolor': '#4A5CF7',
      '--root-border-color': 'rgba(0,0,0,0)',
      '--root-radius': '14px',
      '--main-radius': '10px',
      '--topic-padding': '10px 18px',
      '--color': '#94a3b8',
      '--bgcolor': '#fafbff',
      '--selected': '#f59e0b',
      '--accent-color': '#f59e0b',
      '--panel-color': '#334155',
      '--panel-bgcolor': '#ffffff',
      '--panel-border-color': '#e2e8f0',
      '--map-padding': '40px 70px'
    },
    palette: ['#5B8FF9', '#2FB36B', '#F6A609', '#9270CA', '#E8684A', '#22A7C9', '#F08BB4', '#8C9B6F']
  },
  {
    name: '深空黑',
    cssVar: {
      '--main-color': '#e8eaed',
      '--main-bgcolor': '#23262d',
      '--color': '#9aa3af',
      '--bgcolor': '#16181d'
    },
    palette: ['#5b8def', '#3dd68c', '#f5b942', '#c678dd', '#ff6b6b', '#4dd8e8']
  },
  {
    name: '极简白',
    cssVar: {
      '--main-color': '#33363d',
      '--main-bgcolor': '#ffffff',
      '--color': '#6b7280',
      '--bgcolor': '#f7f8fa'
    },
    palette: ['#4f6ef7', '#2f9e57', '#f08c2e', '#8b5cf6', '#e5484d', '#12a5b0']
  },
  {
    name: '暮色紫',
    cssVar: {
      '--main-color': '#e8e3f5',
      '--main-bgcolor': '#2a2340',
      '--color': '#9d90bd',
      '--bgcolor': '#17141f'
    },
    palette: ['#a78bfa', '#6ee7b7', '#fbbf24', '#f472b6', '#60a5fa', '#fb7185']
  },
  {
    name: '森林绿',
    cssVar: {
      '--main-color': '#1f3d27',
      '--main-bgcolor': '#ffffff',
      '--color': '#5b7a63',
      '--bgcolor': '#f2f7f3'
    },
    palette: ['#2f9e57', '#d97706', '#2563eb', '#7c3aed', '#db2777', '#0891b2']
  },
  {
    name: '暖阳橙',
    cssVar: {
      '--main-color': '#4a3320',
      '--main-bgcolor': '#ffffff',
      '--color': '#8a6a4a',
      '--bgcolor': '#fdf6ee'
    },
    palette: ['#f08c2e', '#e5484d', '#2f9e57', '#7c3aed', '#2563eb', '#0891b2']
  }
]

// 应用主题: 通知 mind 换色 + 给画布 #map 打 data-theme 标记供 CSS 精细化控制
export function applyTheme(mind, themeName) {
  const theme = THEMES.find((t) => t.name === themeName) || THEMES[0]
  try {
    mind.changeTheme({
      name: theme.name,
      palette: theme.palette.slice(),
      cssVar: Object.assign({}, theme.cssVar)
    })
  } catch (e) {
    console.warn('主题切换失败', e)
  }
  // data-theme 必须设在 #map 元素(mind 会把 .mind-elixir 类加到它上面)
  const mapEl = document.getElementById('map')
  if (mapEl) mapEl.setAttribute('data-theme', theme.name)
}

// 渲染主题选择网格(XMind 风格色块卡片)
export function renderThemeList(onSelect, currentName) {
  const list = document.getElementById('theme-list')
  if (!list) return
  list.innerHTML = ''
  THEMES.forEach((t) => {
    const item = document.createElement('div')
    item.className = 'theme-card' + (t.name === currentName ? ' active' : '')
    item.title = t.name
    const sw = document.createElement('div')
    sw.className = 'theme-card-swatch'
    sw.style.background = 'linear-gradient(135deg, ' + t.palette[0] + ', ' + t.palette[1] + ')'
    const nm = document.createElement('div')
    nm.className = 'theme-card-name'
    nm.textContent = t.name
    item.appendChild(sw)
    item.appendChild(nm)
    item.addEventListener('click', () => onSelect(t.name))
    list.appendChild(item)
  })
}
