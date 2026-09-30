# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = """    let remoteRoot = (d.root && d.root.data && d.root.data.uid) ? d.root : ((d.root && d.root.data) ? { data: d.root.data, children: d.root.children || [] } : (d || {}))
    if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
      const active = d._tabs[d._activeTab || 0]
      if (active && active.data && active.data.root) remoteRoot = active.data.root
    }"""
new = """    // 优先主数据 root（保存主字段）；_tabs 仅作兜底，避免 API 直改后 _tabs 与主数据不一致时 diff 用了旧树
    let remoteRoot = (d.root && d.root.data && d.root.data.uid) ? d.root : ((d.root && d.root.data) ? { data: d.root.data, children: d.root.children || [] } : (d || {}))
    if (!remoteRoot || !(remoteRoot.uid || (remoteRoot.data && remoteRoot.data.uid))) {
      if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
        const active = d._tabs[d._activeTab || 0]
        if (active && active.data && active.data.root) remoteRoot = active.data.root
      }
    }"""
assert s.count(old) == 1, 'c1=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('REMOTE ROOT FIX OK')
