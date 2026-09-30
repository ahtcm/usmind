# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) 呼吸灯幅度加大
old = '''@keyframes cmtBreath {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(255,77,79,0)); opacity: 1; }
  50% { filter: drop-shadow(0 0 9px rgba(255,77,79,.95)); opacity: .72; }
}
@keyframes cmtBreathPath {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(255,77,79,0)); opacity: 1; }
  50% { filter: drop-shadow(0 0 6px rgba(255,77,79,.75)); opacity: .6; }
}
@keyframes cmtBreathLine {
  0%, 100% { stroke-opacity: .9; }
  50% { stroke-opacity: .25; }
}'''
new = '''@keyframes cmtBreath {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(255,77,79,0)); opacity: 1; }
  50% { filter: drop-shadow(0 0 18px rgba(255,77,79,1)); opacity: .62; }
}
@keyframes cmtBreathPath {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(255,77,79,0)); opacity: 1; }
  50% { filter: drop-shadow(0 0 12px rgba(255,77,79,.9)); opacity: .5; }
}
@keyframes cmtBreathLine {
  0%, 100% { stroke-opacity: 1; }
  50% { stroke-opacity: .15; }
}
/* 流动电子线：克隆原连线路径，虚线点沿路径流动（同组元素，随画布缩放/平移） */
.cmt-flow-line {
  stroke: #ff4d4f !important;
  stroke-width: 3.2 !important;
  stroke-linecap: round !important;
  fill: none !important;
  stroke-dasharray: 2.5 22 !important;
  animation: cmtFlow 0.9s linear infinite !important;
  opacity: .98 !important;
  pointer-events: none !important;
}
@keyframes cmtFlow {
  to { stroke-dashoffset: -24.5; }
}'''
assert s.count(old) == 1, 'breath=' + str(s.count(old))
s = s.replace(old, new)

# 2) 来源标签 + 组织评论输入框样式（追加在 .cmt-badge-el 后）
old = '''.cmt-badge-el { position: absolute; min-width: 18px; height: 18px; line-height: 18px; text-align: center; font-size: 11px; font-weight: 700; color: #fff; background: linear-gradient(135deg, #ff6b6b, #e53935); border-radius: 9px; padding: 0 5px; box-shadow: 0 2px 8px rgba(229,57,53,.6); transform: translate(-50%, -50%); }'''
new = '''.cmt-badge-el { position: absolute; min-width: 18px; height: 18px; line-height: 18px; text-align: center; font-size: 11px; font-weight: 700; color: #fff; background: linear-gradient(135deg, #ff6b6b, #e53935); border-radius: 9px; padding: 0 5px; box-shadow: 0 2px 8px rgba(229,57,53,.6); transform: translate(-50%, -50%); }
.cmt-src { display: inline-block; font-size: 10px; line-height: 16px; height: 16px; padding: 0 6px; border-radius: 4px; margin-right: 6px; font-weight: 600; vertical-align: 1px; }
.cmt-src-share { background: rgba(74,92,247,.12); color: #4A5CF7; }
.cmt-src-org { background: rgba(14,124,134,.12); color: #0e7c86; }
.cmt-new { display: flex; gap: 8px; padding: 10px 14px; border-top: 1px solid rgba(255,255,255,.08); }
.cmt-new input { flex: 1; min-width: 0; background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.12); border-radius: 6px; color: #e6e6e6; font-size: 13px; padding: 7px 10px; outline: none; }
.cmt-new input:focus { border-color: rgba(74,92,247,.6); }
.cmt-new button { background: linear-gradient(135deg, #4A5CF7, #7c6cf0); border: none; color: #fff; font-size: 13px; padding: 7px 16px; border-radius: 6px; cursor: pointer; white-space: nowrap; }'''
assert s.count(old) == 1, 'src=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('CSS OK')
