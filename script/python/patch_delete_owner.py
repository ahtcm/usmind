# -*- coding: utf-8 -*-
p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")

old = """/** 删除权限：仅 该节点创建人 或 其双亲节点创建人 可删；根节点（无父）仅导图创建者可删 */
function canDeleteNode(node) {
  try {
    if (!node) return false
    const me = myUid()
    if (!me) return false
    if (!node.parent) return Number(mapOwnerId.value) === me
    const nodeCreator = Number(node.getData('creatorUid')) || 0
    const parentCreator = Number(node.parent.getData('creatorUid')) || 0
    return me === nodeCreator || me === parentCreator
  } catch (e) { return false }
}"""

new = """/** 删除权限：导图创建者可删导图内任意节点；其它人仅可删 自己创建的 或 其双亲节点创建者 的节点 */
function canDeleteNode(node) {
  try {
    if (!node) return false
    const me = myUid()
    if (!me) return false
    // 导图创建者：拥有导图内全部节点，可删除任意节点（含根节点、协作成员创建的节点）
    if (Number(mapOwnerId.value) === me) return true
    if (!node.parent) return Number(mapOwnerId.value) === me
    const nodeCreator = Number(node.getData('creatorUid')) || 0
    const parentCreator = Number(node.parent.getData('creatorUid')) || 0
    return me === nodeCreator || me === parentCreator
  } catch (e) { return false }
}"""

assert old in c, 'MISS canDeleteNode'
c = c.replace(old, new)
with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("OK canDeleteNode 导图创建者豁免")
