# -*- coding: utf-8 -*-
p = r'C:\Users\25750\Desktop\usmind\code\web\src\main.js'
s = open(p, encoding='utf-8').read()

# 媒体节点统一收尾: 圆角矩形卡片样式 + 锁定
old = """    scheduleBranchRainbow()
    saveDoc()
  } catch (e) {
    console.warn('[USMIND] 插入媒体失败:', e)
    alert('插入媒体失败: ' + (e && e.message ? e.message : e))
  }
}"""
new = """    // 媒体节点卡片样式: 圆角矩形贴合图片/播放器(替代默认椭圆框)
    try {
      mind.execCommand('SET_NODE_STYLES', node, { shape: 'roundedRectangle', borderRadius: 14, borderWidth: 1 })
      manualStyled.add(node.getData('uid'))
    } catch (e) { /* 样式设置失败不阻塞插入 */ }
    scheduleBranchRainbow()
    saveDoc()
  } catch (e) {
    console.warn('[USMIND] 插入媒体失败:', e)
    alert('插入媒体失败: ' + (e && e.message ? e.message : e))
  }
}"""
assert old in s, 'card style'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('OK')
