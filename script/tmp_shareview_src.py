# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\ShareView.vue'
s = io.open(p, encoding='utf-8').read()

old = '''          <div v-for="c in cmtList" :key="c.id" class="cmt-item" :class="{ 'cmt-author': c.isAuthor === 1 }">
            <div class="cmt-head">
              <b>{{ c.userName }}</b>
              <span v-if="c.isAuthor === 1" class="cmt-author-tag">作者</span>
              <span class="cmt-time">{{ fmtTime(c.createdAt) }}</span>
            </div>'''
new = '''          <div v-for="c in cmtList" :key="c.id" class="cmt-item" :class="{ 'cmt-author': c.isAuthor === 1 }">
            <div class="cmt-head">
              <span class="cmt-src" :class="c.source === 'org' ? 'cmt-src-org' : 'cmt-src-share'">{{ c.source === 'org' ? '组织' : '分享' }}</span>
              <b>{{ c.userName }}</b>
              <span v-if="c.isAuthor === 1" class="cmt-author-tag">作者</span>
              <span class="cmt-time">{{ fmtTime(c.createdAt) }}</span>
            </div>'''
assert s.count(old) == 1, 'share=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SHAREVIEW OK')
