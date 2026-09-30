# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) 评论面板：来源标签 + 回复改用 submitComment + 底部组织评论输入
old = '''          <div class="cmt-head">
            <b>{{ c.userName }}</b>
            <span v-if="c.isAuthor === 1" class="cmt-author-tag">我</span>
            <span class="cmt-time">{{ fmtCmtTime(c.createdAt) }}</span>
          </div>
          <div class="cmt-body">{{ c.content }}</div>
          <div class="cmt-foot">
            <button class="cmt-reply" @click="setCmtReply(c)">{{ cmtPanel.replyTo && cmtPanel.replyTo.id === c.id ? '取消回复' : '回复' }}</button>
          </div>
          <div v-if="cmtPanel.replyTo && cmtPanel.replyTo.id === c.id" class="cmt-quick">
            <input v-model="cmtPanel.input" maxlength="128" :placeholder="'回复 ' + c.userName + '…'" @keyup.enter="submitAuthorReply" />
            <button @click="submitAuthorReply">发送</button>
          </div>
        </div>
      </div>
      <div class="cmt-tip">
        <template v-if="cmtPanel.cmtErr"><span class="cmt-err">{{ cmtPanel.cmtErr }}</span></template>
        <template v-else>共 {{ cmtPanel.list.length }} 条评论 ｜ 有新评论的节点会闪烁提示</template>
      </div>
    </div>'''
new = '''          <div class="cmt-head">
            <span class="cmt-src" :class="c.source === 'org' ? 'cmt-src-org' : 'cmt-src-share'">{{ c.source === 'org' ? '组织' : '分享' }}</span>
            <b>{{ c.userName }}</b>
            <span v-if="c.isAuthor === 1" class="cmt-author-tag">我</span>
            <span class="cmt-time">{{ fmtCmtTime(c.createdAt) }}</span>
          </div>
          <div class="cmt-body">{{ c.content }}</div>
          <div class="cmt-foot">
            <button class="cmt-reply" @click="setCmtReply(c)">{{ cmtPanel.replyTo && cmtPanel.replyTo.id === c.id ? '取消回复' : '回复' }}</button>
          </div>
          <div v-if="cmtPanel.replyTo && cmtPanel.replyTo.id === c.id" class="cmt-quick">
            <input v-model="cmtPanel.input" maxlength="128" :placeholder="'回复 ' + c.userName + '…'" @keyup.enter="submitComment" />
            <button @click="submitComment">发送</button>
          </div>
        </div>
      </div>
      <div class="cmt-tip">
        <template v-if="cmtPanel.cmtErr"><span class="cmt-err">{{ cmtPanel.cmtErr }}</span></template>
        <template v-else>共 {{ cmtPanel.list.length }} 条评论 ｜ 有新评论的节点会闪烁提示</template>
      </div>
      <div v-if="!cmtPanel.replyTo" class="cmt-new">
        <input v-model="cmtPanel.input" maxlength="128" placeholder="组织内评论（成员可见，标注为「组织」）…" @keyup.enter="submitComment" />
        <button @click="submitComment">发送</button>
      </div>
    </div>'''
assert s.count(old) == 1, 'panel=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('PANEL OK')
