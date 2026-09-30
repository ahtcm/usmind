# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 呼吸灯与流动线 CSS 改为 :deep()（目标元素是 simple-mind-map SVG 原生节点，无 data-v 属性）
old = '''.cmt-flash { animation: cmtBreath 1.6s ease-in-out infinite; }
.cmt-breath-path { animation: cmtBreathPath 1.6s ease-in-out infinite; }
.cmt-breath-line { animation: cmtBreathLine 1.6s ease-in-out infinite; }'''
new = ''':deep(.cmt-flash) { animation: cmtBreath 1.6s ease-in-out infinite; }
:deep(.cmt-breath-path) { animation: cmtBreathPath 1.6s ease-in-out infinite; }
:deep(.cmt-breath-line) { animation: cmtBreathLine 1.6s ease-in-out infinite; }'''
assert s.count(old) == 1, 'flash=' + str(s.count(old))
s = s.replace(old, new)

old = '''/* 流动电子线：克隆原连线路径，虚线点沿路径流动（同组元素，随画布缩放/平移） */
.cmt-flow-line {
  stroke: #ff4d4f !important;
  stroke-width: 3.2 !important;
  stroke-linecap: round !important;
  fill: none !important;
  stroke-dasharray: 2.5 22 !important;
  animation: cmtFlow 0.9s linear infinite !important;
  opacity: .98 !important;
  pointer-events: none !important;
}'''
new = '''/* 流动电子线：克隆原连线路径，虚线点沿路径流动（同组元素，随画布缩放/平移） */
:deep(.cmt-flow-line) {
  stroke: #ff4d4f !important;
  stroke-width: 3.2 !important;
  stroke-linecap: round !important;
  fill: none !important;
  stroke-dasharray: 2.5 22 !important;
  animation: cmtFlow 0.9s linear infinite !important;
  opacity: .98 !important;
  pointer-events: none !important;
}'''
assert s.count(old) == 1, 'flowcss=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('DEEP CSS OK')
