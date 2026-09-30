# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s = io.open(p, encoding='utf-8').read()
old = """    private boolean fillCreatorRec(com.fasterxml.jackson.databind.JsonNode n, long owner) {
        boolean changed = false;
        if (n.isObject()) {
            if (!n.has("creatorUid")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) n).put("creatorUid", owner);
                changed = true;
            }
            com.fasterxml.jackson.databind.JsonNode children = n.get("children");
            if (children != null && children.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode c : children) {
                    if (fillCreatorRec(c, owner)) changed = true;
                }
            }
        }
        return changed;
    }"""
new = """    private boolean fillCreatorRec(com.fasterxml.jackson.databind.JsonNode n, long owner) {
        boolean changed = false;
        if (n.isObject()) {
            // 节点数据在 data 层（simple-mind-map 结构：{ data:{text,uid,...}, children:[...] }）
            com.fasterxml.jackson.databind.JsonNode dataNode = n.get("data");
            if (dataNode != null && dataNode.isObject() && !dataNode.has("creatorUid")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) dataNode).put("creatorUid", owner);
                changed = true;
            }
            com.fasterxml.jackson.databind.JsonNode children = n.get("children");
            if (children != null && children.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode c : children) {
                    if (fillCreatorRec(c, owner)) changed = true;
                }
            }
        }
        return changed;
    }"""
assert s.count(old) == 1, 'count=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('FILLCREATOR FIX OK')
