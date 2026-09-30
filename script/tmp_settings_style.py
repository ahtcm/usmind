# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-admin\src\views\Settings.vue'
s = io.open(p, encoding='utf-8').read()
# 检查 style 块是否存在 opt-tip；没有则在 </style> 前补
if 'opt-tip' not in s:
    old = '</style>'
    new = '''.opt-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>'''
    assert s.count(old) == 1
    s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('settings style OK')
