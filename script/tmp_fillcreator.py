# -*- coding: utf-8 -*-
import io

def patch(path, pairs):
    with io.open(path, 'r', encoding='utf-8') as f:
        s = f.read()
    for old, new, tag in pairs:
        assert s.count(old) == 1, tag + ' count=' + str(s.count(old))
        s = s.replace(old, new)
    with io.open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(s)
    print('OK', path.split('\\')[-1])

patch(r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java', [
    ('''    @Override
    public MindMap get(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        return m;
    }''',
     '''    @Override
    public MindMap get(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        fillCreatorUid(m);
        return m;
    }

    /** 历史节点所有权兜底：无 creatorUid 的节点归导图创建者所有（仅内存返回，保存时自然持久化） */
    private void fillCreatorUid(MindMap m) {
        try {
            String data = m.getData();
            if (data == null || data.isBlank()) return;
            long owner = m.getUserId() == null ? 0 : m.getUserId();
            if (owner <= 0) return;
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(data);
            if (root == null) return;
            boolean changed = fillCreatorRec(root, owner);
            if (changed) m.setData(om.writeValueAsString(root));
        } catch (Exception ignored) {
        }
    }

    private boolean fillCreatorRec(com.fasterxml.jackson.databind.JsonNode n, long owner) {
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
    }''',
     'get-fillcreator'),
])
print('DONE')
