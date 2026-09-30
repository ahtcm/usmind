# -*- coding: utf-8 -*-
"""修复评论'我'标记：仅当前登录用户自己发的评论才显示'我'"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()
old = '            <span v-if="c.isAuthor === 1" class="cmt-author-tag">我</span>'
new = '            <span v-if="c.isAuthor === 1 && c.userId && myUid() === Number(c.userId)" class="cmt-author-tag">我</span>'
assert s.count(old) == 1, 'anchor'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('Editor.vue OK')
