# -*- coding: utf-8 -*-
"""修复 cmtSoundOn 响应式（模板按钮文本跟随切换）"""
import io

P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old = """/** 评论提示音：Web Audio 合成短提示音（无需资源文件），开关持久化到 localStorage */
let cmtSoundOn = localStorage.getItem('usmind.cmtSound') !== '0'
function toggleCmtSound() {
  cmtSoundOn = !cmtSoundOn
  localStorage.setItem('usmind.cmtSound', cmtSoundOn ? '1' : '0')
}"""
new = """/** 评论提示音：Web Audio 合成短提示音（无需资源文件），开关持久化到 localStorage */
const cmtSoundOn = ref(localStorage.getItem('usmind.cmtSound') !== '0')
function toggleCmtSound() {
  cmtSoundOn.value = !cmtSoundOn.value
  localStorage.setItem('usmind.cmtSound', cmtSoundOn.value ? '1' : '0')
}"""
assert s.count(old) == 1, 'sound anchor'
s = s.replace(old, new)

old2 = """  if (!cmtSoundOn) return"""
new2 = """  if (!cmtSoundOn.value) return"""
assert s.count(old2) == 1, 'play anchor'
s = s.replace(old2, new2)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('Editor.vue sound reactive OK')
