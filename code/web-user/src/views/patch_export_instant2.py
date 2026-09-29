# -*- coding: utf-8 -*-
# 点击导出后立即渲染遮罩：先让出主线程（nextTick + 延时），再开始 getSvgData 等耗时同步计算
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = open(p, encoding='utf-8').read()

old_run = """  exporting.value = true
  showExportLoading(0, '')
  try {
    if (type === 'png' || type === 'jpg' || type === 'jpeg') {
      await exportChunkedImages(type)"""
new_run = """  exporting.value = true
  showExportLoading(0, '')
  // 先让出主线程：Vue 响应式 DOM 更新 + 浏览器绘制遮罩后再开始耗时同步计算（getSvgData 序列化大 SVG 会阻塞主线程）
  await nextTick()
  await new Promise(r => setTimeout(r, 60))
  try {
    if (type === 'png' || type === 'jpg' || type === 'jpeg') {
      await exportChunkedImages(type)"""
assert old_run in s, 'RUN NOT FOUND'
s = s.replace(old_run, new_run, 1)

open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('instant render OK')
