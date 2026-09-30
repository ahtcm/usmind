# -*- coding: utf-8 -*-
"""Editor.vue: diffApplyLatest 加 no-anim 包裹防闪屏"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old1 = '''    const localRoot = mind.value.renderer && mind.value.renderer.root
    if (!localRoot) return'''
new1 = '''    const localRoot = mind.value.renderer && mind.value.renderer.root
    if (!localRoot) return
    // 局部应用期间关闭动画，避免批量 remove/insert 造成视觉跳动
    mapEl.value.classList.add('no-anim')'''
assert s.count(old1) == 1, 'anchor1'
s = s.replace(old1, new1)

old2 = '''    suppressDirtyUntil = Date.now() + 3000
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    try { structSnap = snapshotStruct() } catch (e) {}
  } catch (e) { console.warn('diffApplyLatest err', e) }
}'''
new2 = '''    suppressDirtyUntil = Date.now() + 3000
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    try { structSnap = snapshotStruct() } catch (e) {}
  } catch (e) { console.warn('diffApplyLatest err', e) }
  try { mapEl.value.classList.remove('no-anim') } catch (e) {}
}'''
assert s.count(old2) == 1, 'anchor2'
s = s.replace(old2, new2)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('diffApplyLatest no-anim OK')
