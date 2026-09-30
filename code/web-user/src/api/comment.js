import request from './request'

/** 分享导图节点评论 API（分享页 + 作者编辑器） */
export const commentApi = {
  // ===== 分享页（无需登录，密码在参数/body 中）=====
  /** 拉取分享评论（全部，按 id 升序）：?password= */
  listShare: (token, password) => request.get('/share/' + token + '/comments', { params: password ? { password } : {} }),
  /** 发表评论/回复：{nodeId, parentId?, nick, content, password?} */
  addShare: (token, data) => request.post('/share/' + token + '/comments', data),

  // ===== 作者编辑器（需登录 + 导图归属）=====
  /** 增量拉取评论+未读状态：?afterId= 上次最大评论 id；afterId=0/缺省返回全部 */
  listByMindMap: (id, afterId) => request.get('/mindmaps/' + id + '/comments', { params: afterId ? { afterId } : {} }),
  /** 作者回复评论：{commentId, content} */
  reply: (id, commentId, content) => request.post('/mindmaps/' + id + '/comments/reply', { commentId, content }),
  /** 组织内评论（协作导图成员对任意节点发评论）：{nodeId, parentId?, content} */
  create: (id, data) => request.post('/mindmaps/' + id + '/comments', data),
  /** 标记某节点已读（点击后停止闪烁）：{nodeId} */
  markRead: (id, nodeId) => request.post('/mindmaps/' + id + '/comments/read', { nodeId })
}
