# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s = io.open(p, encoding='utf-8').read()
old = """            com.fasterxml.jackson.databind.JsonNode root = om.readTree(data);
            if (root == null) return;
            boolean changed = fillCreatorRec(root, owner);
            if (changed) m.setData(om.writeValueAsString(root));"""
new = """            com.fasterxml.jackson.databind.JsonNode root = om.readTree(data);
            if (root == null) return;
            // 数据包装：{ root:{data,children}, _tabs, _activeTab }，从 root 字段进入
            com.fasterxml.jackson.databind.JsonNode treeRoot = root.path("root");
            if (treeRoot.isMissingNode() || treeRoot.isNull()) treeRoot = root;
            boolean changed = fillCreatorRec(treeRoot, owner);
            if (changed) m.setData(om.writeValueAsString(root));"""
assert s.count(old) == 1, 'count=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('FILL ROOT OK')
