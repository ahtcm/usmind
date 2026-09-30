# -*- coding: utf-8 -*-
"""折叠命令后显式重建特效 + syncCommentFlash 延迟补偿（渲染布局稳定后徽标定位正确）"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

# 1) afterExecCommand：折叠/展开命令后延迟重建评论特效（node_tree_render_end 可能不触发或过早）
old = '''      // 结构增量实时广播：对端零延迟局部应用（不拉全量、不闪屏）
      broadcastStructDelta()
    }
  })'''
new = '''      // 结构增量实时广播：对端零延迟局部应用（不拉全量、不闪屏）
      broadcastStructDelta()
    }
    // 折叠/展开后：渲染重绘，延迟重建评论特效（收起节点特效落到最近可见祖先=未收起的部分）
    if (name === 'SET_NODE_EXPAND' || name === 'UNEXPAND_ALL' || name === 'EXPAND_ALL') {
      setTimeout(() => { try { syncCommentFlash() } catch (e) {} }, 220)
    }
  })'''
assert s.count(old) == 1, 'afterExec'
s = s.replace(old, new)

# 2) syncCommentFlash：多次延迟补偿（渲染布局稳定后徽标/路径定位正确）
old2 = '''function syncCommentFlash() {
  nextTick(() => { try { applyCommentFlash() } catch (e) {} })
}'''
new2 = '''function syncCommentFlash() {
  nextTick(() => { try { applyCommentFlash() } catch (e) {} })
  // 渲染布局稳定前 getBoundingClientRect 可能未就绪：延迟补偿重建 + 徽标重定位
  setTimeout(() => { try { applyCommentFlash(); repositionCommentBadges() } catch (e) {} }, 200)
  setTimeout(() => { try { applyCommentFlash(); repositionCommentBadges() } catch (e) {} }, 500)
}'''
assert s.count(old2) == 1, 'sync'
s = s.replace(old2, new2)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('折叠触发+延迟补偿 OK')
